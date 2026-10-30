package com.qalens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.qalens.domain.Organization;
import com.qalens.domain.Project;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.OrganizationRepository;
import com.qalens.repository.ProjectRepository;
import com.qalens.repository.RoleRepository;
import com.qalens.repository.UserProjectRoleRepository;
import com.qalens.security.QalensUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Create, edit and archive: who may, key generation, and tenant boundaries. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class ProjectCrudTest {

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository orgs;
    @Autowired AppUserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired RoleRepository roles;
    @Autowired UserProjectRoleRepository memberships;
    @Autowired PasswordEncoder encoder;

    private TestFixtures fx;
    private Organization acme;
    private Project base;

    @BeforeEach
    void seed() {
        fx = new TestFixtures(orgs, users, projects, roles, memberships, encoder);
        acme = fx.org("acme");
        base = fx.project(acme, "CRUD");
    }

    private QalensUserDetails acmeUser(String role) {
        var user = fx.user(acme, "crud-" + role.toLowerCase() + "@acme.test");
        fx.grant(user, base, role);
        return fx.principal(user);
    }

    private MockHttpServletRequestBuilder create(String org, String name, QalensUserDetails principal) {
        return post("/o/" + org + "/projects").param("name", name).param("description", "d")
                .with(user(principal)).with(csrf());
    }

    private Project newest(String name) {
        return projects.findAll().stream()
                .filter(p -> p.getName().equals(name) && p.getOrg().getId().equals(acme.getId()))
                .reduce((first, second) -> second).orElseThrow();
    }

    @Test
    void adminCreatesAProjectAndBecomesItsAdmin() throws Exception {
        var admin = acmeUser("ADMIN");
        mvc.perform(create("acme", "Payment Gateway", admin))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("/o/acme/projects/*"));
        Project created = newest("Payment Gateway");
        assertThat(created.getProjKey()).startsWith("PG");
        assertThat(memberships.findRoleName(admin.getId(), created.getId())).contains("ADMIN");
    }

    @Test
    void qaLeadMayCreateToo() throws Exception {
        mvc.perform(create("acme", "Lead Made Project", acmeUser("QA_LEAD"))).andExpect(status().isFound());
    }

    @Test
    void sameNameTwiceGetsDistinctKeys() throws Exception {
        var admin = acmeUser("ADMIN");
        mvc.perform(create("acme", "Duplicate Key Probe", admin)).andExpect(status().isFound());
        mvc.perform(create("acme", "Duplicate Key Probe", admin)).andExpect(status().isFound());
        var keys = projects.findAll().stream()
                .filter(p -> p.getName().equals("Duplicate Key Probe")).map(Project::getProjKey).toList();
        assertThat(keys).hasSize(2).doesNotHaveDuplicates().contains("DKP", "DKP2");
    }

    @Test
    void viewerCannotCreateAProject() throws Exception {
        mvc.perform(create("acme", "Viewer Attempt", acmeUser("VIEWER"))).andExpect(status().isForbidden());
        assertThat(projects.findAll()).noneMatch(p -> p.getName().equals("Viewer Attempt"));
    }

    @Test
    void testerAndDeveloperCannotCreateEither() throws Exception {
        mvc.perform(create("acme", "Tester Attempt", acmeUser("TESTER"))).andExpect(status().isForbidden());
        mvc.perform(create("acme", "Developer Attempt", acmeUser("DEVELOPER"))).andExpect(status().isForbidden());
    }

    @Test
    void creatingUnderAnotherOrganisationsSlugIsNotFound() throws Exception {
        mvc.perform(create("globex", "Cross Org Attempt", acmeUser("ADMIN"))).andExpect(status().isNotFound());
        assertThat(projects.findAll()).noneMatch(p -> p.getName().equals("Cross Org Attempt"));
    }

    @Test
    void createWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(post("/o/acme/projects").param("name", "No Token").with(user(acmeUser("ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidNameIsRejectedAndNothingIsSaved() throws Exception {
        mvc.perform(create("acme", "<script>alert(1)</script>", acmeUser("ADMIN")))
                .andExpect(status().isBadRequest());
        assertThat(projects.findAll()).noneMatch(p -> p.getName().contains("<script>"));
    }

    @Test
    void leadCanEditAndTheKeyDoesNotChange() throws Exception {
        mvc.perform(post("/o/acme/projects/" + base.getId() + "/settings").param("name", "Renamed Project")
                        .param("description", "new").with(user(acmeUser("QA_LEAD"))).with(csrf()))
                .andExpect(status().isFound());
        Project reloaded = projects.findByIdAndOrgId(base.getId(), acme.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Renamed Project");
        assertThat(reloaded.getProjKey()).isEqualTo("CRUD");
    }

    @Test
    void testerCannotEditOrArchive() throws Exception {
        var tester = acmeUser("TESTER");
        mvc.perform(post("/o/acme/projects/" + base.getId() + "/settings").param("name", "Hijack")
                .with(user(tester)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/o/acme/projects/" + base.getId() + "/archive").with(user(tester)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminArchivesAndRestores() throws Exception {
        var admin = acmeUser("ADMIN");
        mvc.perform(post("/o/acme/projects/" + base.getId() + "/archive").with(user(admin)).with(csrf()))
                .andExpect(status().isFound());
        assertThat(projects.findByIdAndOrgId(base.getId(), acme.getId()).orElseThrow().isArchived()).isTrue();
        mvc.perform(post("/o/acme/projects/" + base.getId() + "/restore").with(user(admin)).with(csrf()))
                .andExpect(status().isFound());
        assertThat(projects.findByIdAndOrgId(base.getId(), acme.getId()).orElseThrow().isArchived()).isFalse();
    }

    @Test
    void editingAProjectOfAnotherOrganisationIsNotFound() throws Exception {
        Project foreign = fx.project(fx.org("globex"), "FOREIGN");
        mvc.perform(post("/o/acme/projects/" + foreign.getId() + "/settings").param("name", "Stolen")
                .with(user(acmeUser("ADMIN"))).with(csrf())).andExpect(status().isNotFound());
        assertThat(projects.findByIdAndOrgId(foreign.getId(), foreign.getOrg().getId()).orElseThrow().getName())
                .isEqualTo("FOREIGN project");
    }
}
