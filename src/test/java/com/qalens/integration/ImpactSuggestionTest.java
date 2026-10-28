package com.qalens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.qalens.domain.CaseOrigin;
import com.qalens.domain.CasePriority;
import com.qalens.domain.Organization;
import com.qalens.domain.Project;
import com.qalens.domain.Requirement;
import com.qalens.domain.RequirementLink;
import com.qalens.domain.RequirementPriority;
import com.qalens.domain.RequirementStatus;
import com.qalens.domain.TestCase;
import com.qalens.domain.TestCaseVersion;
import com.qalens.domain.TestStep;
import com.qalens.domain.TestSuite;
import com.qalens.repository.AiCallLogRepository;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.CaseVersionEmbeddingRepository;
import com.qalens.repository.OrganizationRepository;
import com.qalens.repository.ProjectRepository;
import com.qalens.repository.RequirementLinkRepository;
import com.qalens.repository.RequirementRepository;
import com.qalens.repository.RoleRepository;
import com.qalens.repository.TestCaseRepository;
import com.qalens.repository.TestCaseVersionRepository;
import com.qalens.repository.TestSuiteRepository;
import com.qalens.repository.UserProjectRoleRepository;
import com.qalens.service.QalensUserDetails;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Impacted-case suggestions end to end, with the stub's bag-of-words embeddings. Each test gets a fresh project. */
@SpringBootTest(properties = "qalens.ai.provider=stub")
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class ImpactSuggestionTest {

    private static final AtomicInteger PROJECTS = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository orgs;
    @Autowired AppUserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired RoleRepository roles;
    @Autowired UserProjectRoleRepository memberships;
    @Autowired RequirementRepository requirements;
    @Autowired TestSuiteRepository suites;
    @Autowired TestCaseRepository cases;
    @Autowired TestCaseVersionRepository versions;
    @Autowired RequirementLinkRepository links;
    @Autowired CaseVersionEmbeddingRepository embeddings;
    @Autowired AiCallLogRepository aiCalls;
    @Autowired PasswordEncoder encoder;

    private TestFixtures fx;
    private Organization acme;
    private Project project;
    private Long author;
    private Requirement refund;
    private TestCase cardOk;
    private TestCase cardDeclined;
    private TestCase wishlist;

    @BeforeEach
    void seed() {
        fx = new TestFixtures(orgs, users, projects, roles, memberships, encoder);
        acme = fx.org("acme");
        project = fx.project(acme, "IMP" + PROJECTS.incrementAndGet());
        author = userWithRole("ADMIN").getId();
        TestSuite payment = suites.save(new TestSuite(project.getId(), null, "Payment", 0));
        TestSuite catalog = suites.save(new TestSuite(project.getId(), null, "Catalog", 1));
        cardOk = testCase(payment, "Valid card payment is accepted", CasePriority.HIGH,
                new TestStep("Enter a card number and pay", "The card is charged for the payment"));
        cardDeclined = testCase(payment, "Declined card keeps the cart", CasePriority.HIGH,
                new TestStep("Pay with a declined card", "A message says the card payment was declined"));
        wishlist = testCase(catalog, "Wishlist can be shared by link", CasePriority.LOW,
                new TestStep("Open the wishlist and copy its link", "A friend sees the same wishlist"));
        Requirement payByCard = requirement("SHOP-2", "Pay by card", "The shopper pays with a credit or debit card.");
        links.save(new RequirementLink(payByCard.getId(), cardOk.getId()));
        refund = requirement("SHOP-7", "Refund a card payment",
                "A shopper gets a refund for a card payment back to the same card.");
        links.save(new RequirementLink(refund.getId(), cardDeclined.getId()));
    }

    private QalensUserDetails userWithRole(String role) {
        var user = fx.user(acme, "imp-" + role.toLowerCase() + "@acme.test");
        fx.grant(user, project, role);
        return fx.principal(user);
    }

    private Requirement requirement(String key, String title, String body) {
        return requirements.save(new Requirement(project.getId(), author, key, title, body,
                RequirementStatus.APPROVED, RequirementPriority.HIGH));
    }

    private TestCase testCase(TestSuite suite, String title, CasePriority priority, TestStep... steps) {
        TestCase tc = cases.save(new TestCase(suite.getId(), author));
        return addVersion(tc, title, priority, steps);
    }

    private TestCase addVersion(TestCase tc, String title, CasePriority priority, TestStep... steps) {
        int next = versions.latestVersionNo(tc.getId()) + 1;
        tc.pointTo(versions.save(new TestCaseVersion(tc.getId(), next, title, null, List.of(steps), priority,
                CaseOrigin.MANUAL, author)));
        return cases.save(tc);
    }

    private String requirementPath() {
        return "/o/acme/projects/" + project.getId() + "/requirements/" + refund.getId();
    }

    private MockHttpServletRequestBuilder impact(QalensUserDetails p) {
        return post(requirementPath() + "/impact").header("HX-Request", "true").with(user(p)).with(csrf());
    }

    private String latestOutputRef() {
        return aiCalls.findTop20ByProjectIdOrderByCreatedAtDescIdDesc(project.getId()).get(0).getOutputRef();
    }

    @Test
    void relatedCasesAreSuggestedWithReasonsAndUnrelatedOnesAreNot() throws Exception {
        mvc.perform(impact(userWithRole("TESTER"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(content().string(containsString("Valid card payment is accepted")))
                .andExpect(content().string(containsString("already covers SHOP-2 Pay by card")))
                .andExpect(content().string(containsString("HIGH priority")))
                .andExpect(content().string(not(containsString("Wishlist can be shared"))))
                // already linked to this requirement, so not a suggestion
                .andExpect(content().string(not(containsString("Declined card keeps the cart"))));

        assertThat(embeddings.currentVersionsWithoutEmbedding(project.getId(), "stub")).isEmpty();
        var log = aiCalls.findTop20ByProjectIdOrderByCreatedAtDescIdDesc(project.getId()).get(0);
        assertThat(log.getFeature()).isEqualTo("IMPACT_SUGGESTION");
        assertThat(log.getModel()).isEqualTo("stub");
        assertThat(log.getStatus()).isEqualTo("OK");
        assertThat(log.getOutputRef()).isEqualTo("candidates=1,embedded=3");
    }

    @Test
    void onlyNewVersionsAreEmbeddedOnTheNextSearch() throws Exception {
        var tester = userWithRole("TESTER");
        mvc.perform(impact(tester)).andExpect(status().isOk());
        mvc.perform(impact(tester)).andExpect(status().isOk());
        assertThat(latestOutputRef()).isEqualTo("candidates=1,embedded=0");

        addVersion(wishlist, "Wishlist items can be paid by card in one payment", CasePriority.MEDIUM,
                new TestStep("Pay for the wishlist with a card", "The card payment covers every item"));
        mvc.perform(impact(tester)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Wishlist items can be paid by card")));
        assertThat(latestOutputRef()).isEqualTo("candidates=2,embedded=1");
    }

    @Test
    void casesOfAnotherProjectAreNeverSuggested() throws Exception {
        Project other = fx.project(acme, "IMX" + PROJECTS.incrementAndGet());
        TestSuite foreign = suites.save(new TestSuite(other.getId(), null, "Foreign", 0));
        TestCase twin = cases.save(new TestCase(foreign.getId(), author));
        twin.pointTo(versions.save(new TestCaseVersion(twin.getId(), 1, "Refund a card payment back to the same card",
                null, List.of(new TestStep("Refund the card payment", "The shopper gets the refund")),
                CasePriority.HIGH, CaseOrigin.MANUAL, author)));
        cases.save(twin);

        mvc.perform(impact(userWithRole("QA_LEAD"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Refund a card payment back to the same card"))));
    }

    @Test
    void viewerIsForbiddenAndDeveloperMayLookButNotLink() throws Exception {
        long before = aiCalls.count();
        mvc.perform(impact(userWithRole("VIEWER"))).andExpect(status().isForbidden());
        assertThat(aiCalls.count()).isEqualTo(before);
        mvc.perform(get(requirementPath()).with(user(userWithRole("VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Impacted test cases"))));

        var dev = userWithRole("DEVELOPER");
        mvc.perform(impact(dev)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Valid card payment is accepted")))
                .andExpect(content().string(not(containsString(">Link</button>"))));
        mvc.perform(post(requirementPath() + "/links").param("caseId", String.valueOf(cardOk.getId()))
                .with(user(dev)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void aSuggestedCaseCanBeLinkedButNotOneOfAnotherProject() throws Exception {
        var tester = userWithRole("TESTER");
        mvc.perform(post(requirementPath() + "/links").param("caseId", String.valueOf(cardOk.getId()))
                .with(user(tester)).with(csrf())).andExpect(status().isFound());
        assertThat(links.existsByRequirementIdAndTestCaseId(refund.getId(), cardOk.getId())).isTrue();

        Project other = fx.project(acme, "IMY" + PROJECTS.incrementAndGet());
        TestCase foreign = cases.save(new TestCase(suites.save(new TestSuite(other.getId(), null, "F", 0)).getId(),
                author));
        mvc.perform(post(requirementPath() + "/links").param("caseId", String.valueOf(foreign.getId()))
                .with(user(tester)).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void aRequirementOfAnotherProjectIsNotFoundAndCsrfIsRequired() throws Exception {
        Project other = fx.project(acme, "IMZ" + PROJECTS.incrementAndGet());
        var admin = userWithRole("ADMIN");
        fx.grant(users.findById(admin.getId()).orElseThrow(), other, "ADMIN");
        mvc.perform(post("/o/acme/projects/" + other.getId() + "/requirements/" + refund.getId() + "/impact")
                .with(user(admin)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post(requirementPath() + "/impact").with(user(admin))).andExpect(status().isForbidden());
    }

    @Test
    void aPlainPostRendersTheWholePageAndTheLeadSeesTheCallLogged() throws Exception {
        var lead = userWithRole("QA_LEAD");
        mvc.perform(post(requirementPath() + "/impact").with(user(lead)).with(csrf())).andExpect(status().isOk())
                .andExpect(content().string(containsString("<html")))
                .andExpect(content().string(containsString("Valid card payment is accepted")));

        mvc.perform(get("/o/acme/projects/" + project.getId()).with(user(lead)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("IMPACT_SUGGESTION")))
                .andExpect(content().string(containsString("1 case<")));
    }
}
