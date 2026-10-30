package com.qalens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.qalens.domain.CaseOrigin;
import com.qalens.domain.CasePriority;
import com.qalens.domain.Organization;
import com.qalens.domain.Project;
import com.qalens.domain.TestCase;
import com.qalens.domain.TestCaseVersion;
import com.qalens.domain.TestSuite;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.OrganizationRepository;
import com.qalens.repository.ProjectRepository;
import com.qalens.repository.RoleRepository;
import com.qalens.repository.TestCaseRepository;
import com.qalens.repository.TestCaseVersionRepository;
import com.qalens.repository.TestSuiteRepository;
import com.qalens.repository.UserProjectRoleRepository;
import com.qalens.security.QalensUserDetails;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Suites and append-only case versions: role matrix, project scoping, version discipline, escaping. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class TestCaseVersioningTest {

    private static final List<String> ROLES = List.of("ADMIN", "QA_LEAD", "TESTER", "DEVELOPER", "VIEWER");
    private static final List<String> MAY_WRITE = List.of("ADMIN", "QA_LEAD", "TESTER");

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository orgs;
    @Autowired AppUserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired RoleRepository roles;
    @Autowired UserProjectRoleRepository memberships;
    @Autowired TestSuiteRepository suites;
    @Autowired TestCaseRepository cases;
    @Autowired TestCaseVersionRepository versions;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private TestFixtures fx;
    private Organization acme;
    private Project project;
    private Project other;
    private TestSuite suite;

    @BeforeEach
    void seed() {
        fx = new TestFixtures(orgs, users, projects, roles, memberships, encoder);
        acme = fx.org("acme");
        project = fx.project(acme, "VERP");
        other = fx.project(acme, "VERO");
        suite = suites.save(new TestSuite(project.getId(), null, "Checkout", 0));
    }

    private QalensUserDetails userWithRole(String role) {
        var user = fx.user(acme, "ver-" + role.toLowerCase() + "@acme.test");
        fx.grant(user, project, role);
        return fx.principal(user);
    }

    private String base() {
        return "/o/acme/projects/" + project.getId();
    }

    private MockHttpServletRequestBuilder caseForm(String path, String title, String steps, QalensUserDetails p) {
        var request = post(path).param("title", title).param("precondition", "pre").param("priority", "HIGH");
        int i = 0;
        for (String line : steps.split("\\n")) {
            String[] parts = line.split("\\|", 2);
            request.param("steps[" + i + "].action", parts[0]);
            request.param("steps[" + i + "].expected", parts.length > 1 ? parts[1] : "");
            i++;
        }
        return request.with(user(p)).with(csrf());
    }

    private TestCase newCase(String title) {
        TestCase tc = cases.save(new TestCase(suite.getId(), userWithRole("ADMIN").getId()));
        TestCaseVersion v = versions.save(new TestCaseVersion(tc.getId(), 1, title, null,
                List.of(new com.qalens.domain.TestStep("a", "b")), CasePriority.LOW, CaseOrigin.MANUAL,
                userWithRole("ADMIN").getId()));
        tc.pointTo(v);
        return cases.save(tc);
    }

    @Test
    void everyRoleCanReadSuitesAndCases() throws Exception {
        TestCase tc = newCase("Readable");
        for (String role : ROLES) {
            var p = userWithRole(role);
            mvc.perform(get(base() + "/suites").with(user(p))).andExpect(status().isOk());
            mvc.perform(get(base() + "/suites/" + suite.getId()).with(user(p))).andExpect(status().isOk());
            mvc.perform(get(base() + "/cases/" + tc.getId()).with(user(p))).andExpect(status().isOk());
        }
    }

    @Test
    void onlyAdminLeadAndTesterCanCreateSuitesAndCases() throws Exception {
        for (String role : ROLES) {
            var p = userWithRole(role);
            var newSuite = post(base() + "/suites").param("name", "Suite by " + role).with(user(p)).with(csrf());
            var newCase = caseForm(base() + "/suites/" + suite.getId() + "/cases", "Case by " + role, "x | y", p);
            if (MAY_WRITE.contains(role)) {
                mvc.perform(newSuite).andExpect(status().isFound());
                mvc.perform(newCase).andExpect(status().isFound())
                        .andExpect(redirectedUrlPattern(base() + "/cases/*"));
            } else {
                mvc.perform(newSuite).andExpect(status().isForbidden());
                mvc.perform(newCase).andExpect(status().isForbidden());
            }
        }
        assertThat(suites.findByProjectIdOrderByPositionAscIdAsc(project.getId()))
                .extracting(TestSuite::getName)
                .contains("Suite by ADMIN", "Suite by TESTER").doesNotContain("Suite by VIEWER");
        assertThat(cases.findBySuiteIdOrderByIdAsc(suite.getId())).hasSize(3);
    }

    @Test
    void creatingACaseWritesVersionOne() throws Exception {
        var p = userWithRole("TESTER");
        mvc.perform(caseForm(base() + "/suites/" + suite.getId() + "/cases", "Pay with card",
                "Open cart | Cart shown\nPay | Receipt", p)).andExpect(status().isFound());
        TestCase tc = cases.findBySuiteIdOrderByIdAsc(suite.getId()).get(0);
        List<TestCaseVersion> all = versions.findByCaseIdOrderByVersionNoDesc(tc.getId());
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getVersionNo()).isEqualTo(1);
        assertThat(all.get(0).getOrigin()).isEqualTo(CaseOrigin.MANUAL);
        assertThat(all.get(0).getSteps()).hasSize(2);
        assertThat(tc.getCurrentVersionId()).isEqualTo(all.get(0).getId());
    }

    @Test
    void editingAppendsAVersionAndLeavesTheOldRowUntouched() throws Exception {
        TestCase tc = newCase("Original title");
        var p = userWithRole("QA_LEAD");
        mvc.perform(caseForm(base() + "/cases/" + tc.getId(), "Edited title", "new step | new result", p))
                .andExpect(status().isFound());

        List<TestCaseVersion> all = versions.findByCaseIdOrderByVersionNoDesc(tc.getId());
        assertThat(all).extracting(TestCaseVersion::getVersionNo).containsExactly(2, 1);
        assertThat(all.get(1).getTitle()).isEqualTo("Original title");
        assertThat(all.get(1).getSteps()).extracting(com.qalens.domain.TestStep::action).containsExactly("a");
        assertThat(all.get(0).getTitle()).isEqualTo("Edited title");
        assertThat(cases.findInProject(tc.getId(), project.getId()).orElseThrow().getCurrentVersionId())
                .isEqualTo(all.get(0).getId());
    }

    @Test
    void anOldVersionCanBeReadAndIsMarkedAsOld() throws Exception {
        TestCase tc = newCase("Version one title");
        mvc.perform(caseForm(base() + "/cases/" + tc.getId(), "Version two title", "s | r",
                userWithRole("TESTER"))).andExpect(status().isFound());
        var viewer = user(userWithRole("VIEWER"));
        mvc.perform(get(base() + "/cases/" + tc.getId() + "?version=1").with(viewer))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Version one title")))
                .andExpect(content().string(containsString("Older version")));
        mvc.perform(get(base() + "/cases/" + tc.getId()).with(viewer))
                .andExpect(content().string(containsString("Version two title")))
                .andExpect(content().string(not(containsString("Older version"))));
        mvc.perform(get(base() + "/cases/" + tc.getId() + "?version=9").with(viewer))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewerAndDeveloperCannotSaveANewVersion() throws Exception {
        TestCase tc = newCase("Locked");
        for (String role : List.of("VIEWER", "DEVELOPER")) {
            mvc.perform(caseForm(base() + "/cases/" + tc.getId(), "Hijacked", "a | b", userWithRole(role)))
                    .andExpect(status().isForbidden());
        }
        assertThat(versions.findByCaseIdOrderByVersionNoDesc(tc.getId())).hasSize(1);
    }

    @Test
    void caseAndSuiteIdsFromAnotherProjectAreNotFound() throws Exception {
        TestCase tc = newCase("Belongs to VERP");
        var admin = fx.user(acme, "ver-admin@acme.test");
        fx.grant(admin, other, "ADMIN");
        var p = fx.principal(admin);
        String otherBase = "/o/acme/projects/" + other.getId();
        mvc.perform(get(otherBase + "/cases/" + tc.getId()).with(user(p))).andExpect(status().isNotFound());
        mvc.perform(caseForm(otherBase + "/cases/" + tc.getId(), "Moved", "a | b", p))
                .andExpect(status().isNotFound());
        mvc.perform(get(otherBase + "/suites/" + suite.getId()).with(user(p))).andExpect(status().isNotFound());
        mvc.perform(caseForm(otherBase + "/suites/" + suite.getId() + "/cases", "Planted", "a | b", p))
                .andExpect(status().isNotFound());
        assertThat(versions.findByCaseIdOrderByVersionNoDesc(tc.getId())).hasSize(1);
    }

    @Test
    void aParentSuiteFromAnotherProjectIsRejected() throws Exception {
        TestSuite foreign = suites.save(new TestSuite(other.getId(), null, "Foreign", 0));
        mvc.perform(post(base() + "/suites").param("name", "Child").param("parentId", String.valueOf(foreign.getId()))
                .with(user(userWithRole("ADMIN"))).with(csrf())).andExpect(status().isNotFound());
        assertThat(suites.findByProjectIdOrderByPositionAscIdAsc(project.getId()))
                .extracting(TestSuite::getName).doesNotContain("Child");
    }

    @Test
    void childSuitesNestUnderTheirParent() throws Exception {
        mvc.perform(post(base() + "/suites").param("name", "Payments").param("parentId", String.valueOf(suite.getId()))
                .with(user(userWithRole("ADMIN"))).with(csrf())).andExpect(status().isFound());
        mvc.perform(get(base() + "/suites").with(user(userWithRole("VIEWER"))))
                .andExpect(content().string(containsString("↳ Payments")));
    }

    @Test
    void invalidInputIsRejectedAndNothingIsSaved() throws Exception {
        var p = userWithRole("ADMIN");
        mvc.perform(caseForm(base() + "/suites/" + suite.getId() + "/cases", "", "a | b", p))
                .andExpect(status().isBadRequest());
        mvc.perform(caseForm(base() + "/suites/" + suite.getId() + "/cases", "No steps", " | ", p))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base() + "/suites").param("name", " ").with(user(p)).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(cases.findBySuiteIdOrderByIdAsc(suite.getId())).isEmpty();
    }

    @Test
    void hostileTextIsEscapedAndCsrfIsRequired() throws Exception {
        var p = userWithRole("ADMIN");
        mvc.perform(caseForm(base() + "/suites/" + suite.getId() + "/cases", "<script>alert(1)</script>",
                "<img src=x onerror=y> | <b>done</b>", p)).andExpect(status().isFound());
        TestCase tc = cases.findBySuiteIdOrderByIdAsc(suite.getId()).get(0);
        mvc.perform(get(base() + "/cases/" + tc.getId()).with(user(p)))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert"))))
                .andExpect(content().string(not(containsString("<img src=x"))));
        mvc.perform(post(base() + "/cases/" + tc.getId()).param("title", "t").param("priority", "LOW")
                .param("steps[0].action", "a").with(user(p))).andExpect(status().isForbidden());
    }

    @Test
    void theDatabaseRefusesNoSilentRewriteOfVersionOne() {
        // Version rows are @Immutable: a second row with the same (case, number) violates the unique constraint.
        TestCase tc = newCase("Unique numbering");
        long before = jdbc.queryForObject("select count(*) from test_case_version where case_id = ?", Long.class,
                tc.getId());
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> versions.saveAndFlush(new TestCaseVersion(tc.getId(), 1, "dup", null,
                        List.of(new com.qalens.domain.TestStep("a", "b")), CasePriority.LOW, CaseOrigin.MANUAL,
                        tc.getOwnerId())));
        assertThat(jdbc.queryForObject("select count(*) from test_case_version where case_id = ?", Long.class,
                tc.getId())).isEqualTo(before);
    }
}
