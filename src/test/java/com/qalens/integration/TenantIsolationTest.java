package com.qalens.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.qalens.domain.AppUser;
import com.qalens.domain.Organization;
import com.qalens.domain.Project;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.OrganizationRepository;
import com.qalens.repository.ProjectRepository;
import com.qalens.repository.RoleRepository;
import com.qalens.repository.UserProjectRoleRepository;
import com.qalens.service.QalensUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** A valid session and a legitimate role in one organisation must never reach another organisation's data. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class TenantIsolationTest {

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository orgs;
    @Autowired AppUserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired RoleRepository roles;
    @Autowired UserProjectRoleRepository memberships;
    @Autowired PasswordEncoder encoder;

    private TestFixtures fx;
    private QalensUserDetails acmeTester;
    private Project acmeDemo;
    private Project acmeSecret;
    private Project globexProject;

    @BeforeEach
    void seed() {
        fx = new TestFixtures(orgs, users, projects, roles, memberships, encoder);
        Organization acme = fx.org("acme");
        Organization globex = fx.org("globex");
        AppUser tester = fx.user(acme, "iso-tester@acme.test");
        acmeDemo = fx.project(acme, "DEMO");
        acmeSecret = fx.project(acme, "SEC");
        globexProject = fx.project(globex, "GLB");
        fx.grant(tester, acmeDemo, "TESTER");
        fx.grant(fx.user(globex, "iso-admin@globex.test"), globexProject, "ADMIN");
        acmeTester = fx.principal(tester);
    }

    @Test
    void reservedSlugIsNotFoundEvenForAnonymousCallers() throws Exception {
        for (String slug : new String[] {"admin", "api", "login", "static", "actuator", "o"}) {
            mvc.perform(get("/o/" + slug + "/")).andExpect(status().isNotFound());
        }
    }

    @Test
    void unknownSlugIsNotFoundBeforeLoginIsDemanded() throws Exception {
        mvc.perform(get("/o/no-such-org/")).andExpect(status().isNotFound());
    }

    @Test
    void knownSlugStillRequiresLogin() throws Exception {
        mvc.perform(get("/o/acme/")).andExpect(status().isFound()).andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void slugWithoutTrailingSlashRedirectsToHome() throws Exception {
        mvc.perform(get("/o/acme").with(user(acmeTester)))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/o/acme/"));
        mvc.perform(get("/o/globex").with(user(acmeTester))).andExpect(status().isNotFound());
        mvc.perform(get("/o/no-such-org")).andExpect(status().isNotFound());
        mvc.perform(get("/o/admin")).andExpect(status().isNotFound());
        mvc.perform(get("/o/acme")).andExpect(status().isFound()).andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void anotherOrganisationsSlugIsNotFound() throws Exception {
        mvc.perform(get("/o/globex/").with(user(acmeTester))).andExpect(status().isNotFound());
        mvc.perform(get("/o/globex/projects").with(user(acmeTester))).andExpect(status().isNotFound());
    }

    @Test
    void foreignProjectIdUnderOwnSlugIsNotFound() throws Exception {
        mvc.perform(get("/o/acme/projects/" + globexProject.getId()).with(user(acmeTester)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/o/acme/projects/" + globexProject.getId() + "/settings").with(user(acmeTester)))
                .andExpect(status().isNotFound());
    }

    @Test
    void foreignProjectIdUnderForeignSlugIsNotFound() throws Exception {
        mvc.perform(get("/o/globex/projects/" + globexProject.getId()).with(user(acmeTester)))
                .andExpect(status().isNotFound());
    }

    @Test
    void projectInOwnOrganisationWithoutARoleIsNotFound() throws Exception {
        mvc.perform(get("/o/acme/projects/" + acmeSecret.getId()).with(user(acmeTester)))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedProjectIdIsNotFound() throws Exception {
        mvc.perform(get("/o/acme/projects/not-a-number").with(user(acmeTester))).andExpect(status().isNotFound());
    }

    @Test
    void ownProjectIsVisible() throws Exception {
        mvc.perform(get("/o/acme/projects/" + acmeDemo.getId()).with(user(acmeTester))).andExpect(status().isOk());
    }
}
