package com.qalens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import com.qalens.domain.RequirementPriority;
import com.qalens.domain.RequirementStatus;
import com.qalens.domain.TestCase;
import com.qalens.domain.TestCaseVersion;
import com.qalens.domain.TestStep;
import com.qalens.domain.TestSuite;
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

/** Requirement-to-case links: who may link, project scoping of both ends, idempotence, visibility on both pages. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class RequirementLinkTest {

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
    @Autowired PasswordEncoder encoder;

    private TestFixtures fx;
    private Organization acme;
    private Project project;
    private Project other;
    private TestCase testCase;
    private Requirement requirement;

    @BeforeEach
    void seed() {
        fx = new TestFixtures(orgs, users, projects, roles, memberships, encoder);
        acme = fx.org("acme");
        project = fx.project(acme, "LNKP");
        other = fx.project(acme, "LNKO");
        Long author = userWithRole("ADMIN").getId();
        TestSuite suite = suites.save(new TestSuite(project.getId(), null, "Linking", 0));
        testCase = cases.save(new TestCase(suite.getId(), author));
        TestCaseVersion v = versions.save(new TestCaseVersion(testCase.getId(), 1, "Linked case", null,
                List.of(new TestStep("a", "b")), CasePriority.MEDIUM, CaseOrigin.MANUAL, author));
        testCase.pointTo(v);
        testCase = cases.save(testCase);
        requirement = requirements.save(new Requirement(project.getId(), author, "LNK-1", "Linkable requirement",
                null, RequirementStatus.APPROVED, RequirementPriority.HIGH));
    }

    private QalensUserDetails userWithRole(String role) {
        var user = fx.user(acme, "lnk-" + role.toLowerCase() + "@acme.test");
        fx.grant(user, project, role);
        return fx.principal(user);
    }

    private String casePath() {
        return "/o/acme/projects/" + project.getId() + "/cases/" + testCase.getId();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder link(
            String path, Long requirementId, QalensUserDetails p) {
        return post(path + "/requirements").param("requirementId", String.valueOf(requirementId))
                .with(user(p)).with(csrf());
    }

    @Test
    void testerLinksAndBothPagesShowTheLink() throws Exception {
        mvc.perform(link(casePath(), requirement.getId(), userWithRole("TESTER"))).andExpect(status().isFound());
        assertThat(links.existsByRequirementIdAndTestCaseId(requirement.getId(), testCase.getId())).isTrue();

        var viewer = user(userWithRole("VIEWER"));
        mvc.perform(get(casePath()).with(viewer)).andExpect(status().isOk())
                .andExpect(content().string(containsString("LNK-1 · Linkable requirement")));
        mvc.perform(get("/o/acme/projects/" + project.getId() + "/requirements/" + requirement.getId()).with(viewer))
                .andExpect(content().string(containsString("Linked case")));
    }

    @Test
    void linkingTwiceKeepsOneRow() throws Exception {
        var tester = userWithRole("TESTER");
        mvc.perform(link(casePath(), requirement.getId(), tester)).andExpect(status().isFound());
        mvc.perform(link(casePath(), requirement.getId(), tester)).andExpect(status().isFound());
        assertThat(links.requirementsOfCase(testCase.getId(), project.getId())).hasSize(1);
    }

    @Test
    void viewerAndDeveloperCannotLinkOrUnlink() throws Exception {
        links.save(new com.qalens.domain.RequirementLink(requirement.getId(), testCase.getId()));
        for (String role : List.of("VIEWER", "DEVELOPER")) {
            var p = userWithRole(role);
            mvc.perform(link(casePath(), requirement.getId(), p)).andExpect(status().isForbidden());
            mvc.perform(post(casePath() + "/requirements/" + requirement.getId() + "/remove")
                    .with(user(p)).with(csrf())).andExpect(status().isForbidden());
        }
        assertThat(links.existsByRequirementIdAndTestCaseId(requirement.getId(), testCase.getId())).isTrue();
    }

    @Test
    void unlinkRemovesTheRow() throws Exception {
        links.save(new com.qalens.domain.RequirementLink(requirement.getId(), testCase.getId()));
        mvc.perform(post(casePath() + "/requirements/" + requirement.getId() + "/remove")
                .with(user(userWithRole("QA_LEAD"))).with(csrf())).andExpect(status().isFound());
        assertThat(links.existsByRequirementIdAndTestCaseId(requirement.getId(), testCase.getId())).isFalse();
    }

    @Test
    void aRequirementFromAnotherProjectCannotBeLinked() throws Exception {
        Requirement foreign = requirements.save(new Requirement(other.getId(), userWithRole("ADMIN").getId(), null,
                "Foreign", null, RequirementStatus.DRAFT, RequirementPriority.LOW));
        mvc.perform(link(casePath(), foreign.getId(), userWithRole("ADMIN"))).andExpect(status().isNotFound());
        assertThat(links.existsByRequirementIdAndTestCaseId(foreign.getId(), testCase.getId())).isFalse();
    }

    @Test
    void aCaseIsNotReachableThroughAnotherProject() throws Exception {
        var admin = fx.user(acme, "lnk-other-admin@acme.test");
        fx.grant(admin, other, "ADMIN");
        mvc.perform(link("/o/acme/projects/" + other.getId() + "/cases/" + testCase.getId(), requirement.getId(),
                fx.principal(admin))).andExpect(status().isNotFound());
    }

    @Test
    void linkRequiresCsrf() throws Exception {
        mvc.perform(post(casePath() + "/requirements").param("requirementId", String.valueOf(requirement.getId()))
                .with(user(userWithRole("ADMIN")))).andExpect(status().isForbidden());
    }
}
