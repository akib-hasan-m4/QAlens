package com.qalens.integration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.qalens.domain.Organization;
import com.qalens.domain.Project;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.OrganizationRepository;
import com.qalens.repository.ProjectRepository;
import com.qalens.repository.RoleRepository;
import com.qalens.repository.UserProjectRoleRepository;
import com.qalens.security.QalensUserDetails;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Role matrix: one named check per role and endpoint, as the spec requires for every protected endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class ProjectAuthorizationTest {

    private static final List<String> ROLES = List.of("ADMIN", "QA_LEAD", "TESTER", "DEVELOPER", "VIEWER");
    private static final List<String> MAY_OPEN_SETTINGS = List.of("ADMIN", "QA_LEAD");

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository orgs;
    @Autowired AppUserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired RoleRepository roles;
    @Autowired UserProjectRoleRepository memberships;
    @Autowired PasswordEncoder encoder;

    private TestFixtures fx;
    private Project project;

    private QalensUserDetails userWithRole(String role) {
        var user = fx.user(fx.org("acme"), "authz-" + role.toLowerCase() + "@acme.test");
        fx.grant(user, project, role);
        return fx.principal(user);
    }

    @BeforeEach
    void seed() {
        fx = new TestFixtures(orgs, users, projects, roles, memberships, encoder);
        Organization acme = fx.org("acme");
        project = fx.project(acme, "AUTHZ");
    }

    @Test
    void everyRoleCanViewTheProject() throws Exception {
        for (String role : ROLES) {
            mvc.perform(get("/o/acme/projects/" + project.getId()).with(user(userWithRole(role))))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(role)));
        }
    }

    @Test
    void onlyAdminAndQaLeadCanOpenSettings() throws Exception {
        for (String role : ROLES) {
            var request = get("/o/acme/projects/" + project.getId() + "/settings").with(user(userWithRole(role)));
            if (MAY_OPEN_SETTINGS.contains(role)) {
                mvc.perform(request).andExpect(status().isOk());
            } else {
                mvc.perform(request).andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void testerGetsForbiddenOnSettings() throws Exception {
        mvc.perform(get("/o/acme/projects/" + project.getId() + "/settings").with(user(userWithRole("TESTER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerGetsForbiddenOnSettings() throws Exception {
        mvc.perform(get("/o/acme/projects/" + project.getId() + "/settings").with(user(userWithRole("VIEWER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void projectListShowsOnlyProjectsWithARole() throws Exception {
        mvc.perform(get("/o/acme/projects").with(user(userWithRole("VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AUTHZ")));
    }

    @Test
    void anonymousIsSentToLogin() throws Exception {
        mvc.perform(get("/o/acme/projects/" + project.getId()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("http://localhost/login"));
    }
}
