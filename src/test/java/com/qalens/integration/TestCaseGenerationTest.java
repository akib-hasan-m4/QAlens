package com.qalens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.qalens.domain.CaseOrigin;
import com.qalens.domain.Organization;
import com.qalens.domain.Project;
import com.qalens.domain.Requirement;
import com.qalens.domain.RequirementPriority;
import com.qalens.domain.RequirementStatus;
import com.qalens.domain.TestCase;
import com.qalens.domain.TestCaseVersion;
import com.qalens.domain.TestSuite;
import com.qalens.repository.AiCallLogRepository;
import com.qalens.repository.AppUserRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Generate and accept AI drafts end to end, with the deterministic stub model. */
@SpringBootTest(properties = "qalens.ai.provider=stub")
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class TestCaseGenerationTest {

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
    @Autowired AiCallLogRepository aiCalls;
    @Autowired PasswordEncoder encoder;

    private TestFixtures fx;
    private Organization acme;
    private Project project;
    private Project other;
    private TestSuite suite;
    private Requirement requirement;

    @BeforeEach
    void seed() {
        fx = new TestFixtures(orgs, users, projects, roles, memberships, encoder);
        acme = fx.org("acme");
        project = fx.project(acme, "GENP");
        other = fx.project(acme, "GENO");
        Long author = userWithRole("ADMIN").getId();
        suite = suites.save(new TestSuite(project.getId(), null, "Generated", 0));
        requirement = requirements.save(new Requirement(project.getId(), author, "GEN-1", "Pay by card",
                "Cards are validated", RequirementStatus.APPROVED, RequirementPriority.HIGH));
    }

    private QalensUserDetails userWithRole(String role) {
        var user = fx.user(acme, "gen-" + role.toLowerCase() + "@acme.test");
        fx.grant(user, project, role);
        return fx.principal(user);
    }

    private String requirementPath() {
        return "/o/acme/projects/" + project.getId() + "/requirements/" + requirement.getId();
    }

    private MockHttpServletRequestBuilder generate(QalensUserDetails p) {
        return post(requirementPath() + "/drafts").header("HX-Request", "true").with(user(p)).with(csrf());
    }

    /** Two stub drafts ticked (the first one edited), the third left unticked. */
    private MockHttpServletRequestBuilder accept(QalensUserDetails p) {
        return accept(p, suite.getId(), "Invalid input is rejected");
    }

    private MockHttpServletRequestBuilder accept(QalensUserDetails p, Long suiteId, String secondTitle) {
        return post(requirementPath() + "/drafts/accept").with(user(p)).with(csrf())
                .param("suiteId", String.valueOf(suiteId))
                .param("drafts[0].selected", "true").param("drafts[0].title", "Main flow succeeds (edited)")
                .param("drafts[0].priority", "HIGH").param("drafts[0].steps[0].action", "Do it")
                .param("drafts[0].steps[0].expected", "It works").param("drafts[0].steps[1].action", "")
                .param("drafts[1].selected", "true").param("drafts[1].title", secondTitle)
                .param("drafts[1].priority", "MEDIUM").param("drafts[1].steps[0].action", "Submit bad data")
                .param("drafts[2].title", "Not wanted").param("drafts[2].priority", "LOW")
                .param("drafts[2].steps[0].action", "x");
    }

    private List<TestCase> casesInSuite() {
        return cases.findBySuiteIdOrderByIdAsc(suite.getId());
    }

    @Test
    void testerGetsTheDraftPanelAndTheCallIsLogged() throws Exception {
        long before = aiCalls.count();

        mvc.perform(generate(userWithRole("TESTER"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Main flow succeeds")))
                .andExpect(content().string(containsString("Boundary value is accepted")))
                .andExpect(content().string(not(containsString("<html"))));

        assertThat(aiCalls.count()).isEqualTo(before + 1);
        assertThat(aiCalls.findAll()).anySatisfy(log -> {
            assertThat(log.getModel()).isEqualTo("stub");
            assertThat(log.getStatus()).isEqualTo("OK");
            assertThat(log.getOutputRef()).isEqualTo("drafts=3,dropped=0");
        });
        assertThat(casesInSuite()).isEmpty();
    }

    @Test
    void acceptSavesTickedDraftsAsAiGeneratedVersionOneAndLinksThem() throws Exception {
        mvc.perform(accept(userWithRole("QA_LEAD"))).andExpect(status().isFound());

        List<TestCase> saved = casesInSuite();
        assertThat(saved).hasSize(2);
        List<TestCaseVersion> current = versions.currentVersionsOf(saved.stream().map(TestCase::getId).toList());
        assertThat(current).extracting(TestCaseVersion::getTitle)
                .containsExactlyInAnyOrder("Main flow succeeds (edited)", "Invalid input is rejected");
        assertThat(current).allSatisfy(v -> {
            assertThat(v.getVersionNo()).isEqualTo(1);
            assertThat(v.getOrigin()).isEqualTo(CaseOrigin.AI_GENERATED);
        });
        assertThat(saved).allSatisfy(c ->
                assertThat(links.existsByRequirementIdAndTestCaseId(requirement.getId(), c.getId())).isTrue());
    }

    @Test
    void anInvalidTickedDraftSavesNothing() throws Exception {
        mvc.perform(accept(userWithRole("TESTER"), suite.getId(), ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Title is required")));
        mvc.perform(post(requirementPath() + "/drafts/accept").with(user(userWithRole("TESTER"))).with(csrf())
                        .param("suiteId", String.valueOf(suite.getId())).param("drafts[0].title", "Unticked"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Tick at least one draft")));
        assertThat(casesInSuite()).isEmpty();
    }

    @Test
    void viewerAndDeveloperCannotGenerateOrAccept() throws Exception {
        long before = aiCalls.count();
        for (String role : List.of("VIEWER", "DEVELOPER")) {
            var p = userWithRole(role);
            mvc.perform(generate(p)).andExpect(status().isForbidden());
            mvc.perform(accept(p)).andExpect(status().isForbidden());
        }
        assertThat(aiCalls.count()).isEqualTo(before);
        assertThat(casesInSuite()).isEmpty();
    }

    @Test
    void aRequirementOrSuiteOfAnotherProjectIsNotFound() throws Exception {
        var admin = userWithRole("ADMIN");
        fx.grant(users.findById(admin.getId()).orElseThrow(), other, "ADMIN");
        mvc.perform(post("/o/acme/projects/" + other.getId() + "/requirements/" + requirement.getId() + "/drafts")
                .with(user(admin)).with(csrf())).andExpect(status().isNotFound());

        TestSuite foreign = suites.save(new TestSuite(other.getId(), null, "Foreign", 0));
        mvc.perform(accept(admin, foreign.getId(), "Invalid input is rejected"))
                .andExpect(status().isNotFound());
        assertThat(cases.findBySuiteIdOrderByIdAsc(foreign.getId())).isEmpty();
    }

    @Test
    void generateAndAcceptRequireCsrf() throws Exception {
        var admin = userWithRole("ADMIN");
        mvc.perform(post(requirementPath() + "/drafts").with(user(admin))).andExpect(status().isForbidden());
        mvc.perform(post(requirementPath() + "/drafts/accept").with(user(admin))
                .param("suiteId", String.valueOf(suite.getId()))).andExpect(status().isForbidden());
    }
}
