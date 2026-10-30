package com.qalens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.qalens.security.QalensUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** The organisation Users page: only organisation admins, own organisation only, no self-lockout. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class UserAdministrationTest {

    private static final String USERS = "/o/acme/admin/users";

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository orgs;
    @Autowired AppUserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired RoleRepository roles;
    @Autowired UserProjectRoleRepository memberships;
    @Autowired PasswordEncoder encoder;

    private TestFixtures fx;
    private Organization acme;
    private AppUser admin;
    private QalensUserDetails adminPrincipal;

    @BeforeEach
    void seed() {
        fx = new TestFixtures(orgs, users, projects, roles, memberships, encoder);
        acme = fx.org("acme");
        admin = fx.orgAdmin(fx.user(acme, "ua-admin@acme.test"));
        adminPrincipal = fx.principal(admin);
    }

    private AppUser acmeUser(String tag) {
        return fx.user(acme, "ua-" + tag + "@acme.test");
    }

    private AppUser reload(AppUser user) {
        return users.findByEmailIgnoreCase(user.getEmail()).orElseThrow();
    }

    @Test
    void orgAdminSeesTheUsersOfTheirOwnOrganisationOnly() throws Exception {
        acmeUser("listed");
        fx.user(fx.org("globex"), "ua-outsider@globex.test");
        mvc.perform(get(USERS).with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ua-listed@acme.test")))
                .andExpect(content().string(not(containsString("ua-outsider@globex.test"))));
    }

    @Test
    void projectAdminWithoutTheOrganisationRightIsForbidden() throws Exception {
        AppUser projectAdmin = acmeUser("project-admin");
        fx.grant(projectAdmin, fx.project(acme, "UADM"), "ADMIN");
        var principal = fx.principal(projectAdmin);
        mvc.perform(get(USERS).with(user(principal))).andExpect(status().isForbidden());
        mvc.perform(post(USERS).with(user(principal)).with(csrf())
                        .param("email", "ua-sneaky@acme.test").param("displayName", "Sneaky")
                        .param("password", "long-enough-pw"))
                .andExpect(status().isForbidden());
        assertThat(users.existsByEmailIgnoreCase("ua-sneaky@acme.test")).isFalse();
        mvc.perform(post(USERS + "/" + admin.getId() + "/active").param("active", "false")
                        .with(user(principal)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void usersLinkIsShownToOrgAdminsOnly() throws Exception {
        mvc.perform(get("/o/acme/projects").with(user(adminPrincipal)))
                .andExpect(content().string(containsString("/o/acme/admin/users")));
        mvc.perform(get("/o/acme/projects").with(user(fx.principal(acmeUser("plain")))))
                .andExpect(content().string(not(containsString("/o/acme/admin/users"))));
    }

    @Test
    void createdUserBelongsToTheCallersOrganisationAndCanSignIn() throws Exception {
        Long globexId = fx.org("globex").getId();
        mvc.perform(post(USERS).with(user(adminPrincipal)).with(csrf())
                        .param("email", "  UA-New@Acme.test ").param("displayName", "Nina New")
                        .param("password", "correct horse").param("orgAdmin", "false")
                        .param("orgId", globexId.toString()).param("active", "false"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(USERS))
                .andExpect(flash().attribute("notice", containsString("Nina New")));

        AppUser created = users.findByEmailIgnoreCase("ua-new@acme.test").orElseThrow();
        assertThat(created.getEmail()).isEqualTo("ua-new@acme.test");
        assertThat(created.getOrg().getId()).isEqualTo(acme.getId());
        assertThat(created.isActive()).isTrue();
        assertThat(created.isOrgAdmin()).isFalse();
        assertThat(created.getPasswordHash()).isNotEqualTo("correct horse");

        mvc.perform(post("/login").with(csrf()).param("email", "ua-new@acme.test").param("password", "correct horse"))
                .andExpect(redirectedUrl("/o/acme/"));
    }

    @Test
    void duplicateEmailIsRejectedWhateverItsCase() throws Exception {
        acmeUser("taken");
        mvc.perform(post(USERS).with(user(adminPrincipal)).with(csrf())
                        .param("email", "UA-TAKEN@acme.test").param("displayName", "Copy")
                        .param("password", "long-enough-pw"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("already registered")));
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        mvc.perform(post(USERS).with(user(adminPrincipal)).with(csrf())
                        .param("email", "not-an-email").param("displayName", "<b>x</b>").param("password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("valid email")))
                .andExpect(content().string(containsString("at least 10 characters")))
                .andExpect(content().string(not(containsString("<b>x</b>"))));
        mvc.perform(post(USERS).with(user(adminPrincipal)).with(csrf())
                        .param("email", "ua-long@acme.test").param("displayName", "Long")
                        .param("password", "é".repeat(40)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("at most 72 bytes")));
    }

    @Test
    void deactivatingAUserEndsTheirSessionAndBlocksSignIn() throws Exception {
        AppUser target = acmeUser("leaver");
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("email", target.getEmail()).param("password", "secret"))
                .andExpect(redirectedUrl("/o/acme/"))
                .andReturn().getRequest().getSession(false);

        mvc.perform(post(USERS + "/" + target.getId() + "/active").param("active", "false")
                        .with(user(adminPrincipal)).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(flash().attribute("notice", containsString("deactivated")));
        assertThat(reload(target).isActive()).isFalse();

        mvc.perform(get("/o/acme/projects").session(session))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("/login?expired")));
        mvc.perform(post("/login").with(csrf()).param("email", target.getEmail()).param("password", "secret"))
                .andExpect(redirectedUrl("/login?error"));

        mvc.perform(post(USERS + "/" + target.getId() + "/active").param("active", "true")
                        .with(user(adminPrincipal)).with(csrf()))
                .andExpect(status().isFound());
        assertThat(reload(target).isActive()).isTrue();
    }

    @Test
    void grantingTheOrganisationRightTakesEffectImmediately() throws Exception {
        AppUser target = acmeUser("promoted");
        var principal = fx.principal(target);
        mvc.perform(get(USERS).with(user(principal))).andExpect(status().isForbidden());

        mvc.perform(post(USERS + "/" + target.getId() + "/org-admin").param("orgAdmin", "true")
                        .with(user(adminPrincipal)).with(csrf()))
                .andExpect(status().isFound());
        assertThat(reload(target).isOrgAdmin()).isTrue();
        mvc.perform(get(USERS).with(user(principal))).andExpect(status().isOk());

        mvc.perform(post(USERS + "/" + target.getId() + "/org-admin").param("orgAdmin", "false")
                        .with(user(adminPrincipal)).with(csrf()))
                .andExpect(status().isFound());
        mvc.perform(get(USERS).with(user(principal))).andExpect(status().isForbidden());
    }

    @Test
    void adminCannotLockThemselvesOut() throws Exception {
        mvc.perform(post(USERS + "/" + admin.getId() + "/active").param("active", "false")
                        .with(user(adminPrincipal)).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(flash().attribute("error", containsString("your own account")));
        mvc.perform(post(USERS + "/" + admin.getId() + "/org-admin").param("orgAdmin", "false")
                        .with(user(adminPrincipal)).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(flash().attribute("error", containsString("your own administrator rights")));
        AppUser after = reload(admin);
        assertThat(after.isActive()).isTrue();
        assertThat(after.isOrgAdmin()).isTrue();
    }

    @Test
    void userOfAnotherOrganisationIsNotFound() throws Exception {
        AppUser outsider = fx.user(fx.org("globex"), "ua-target@globex.test");
        mvc.perform(post(USERS + "/" + outsider.getId() + "/active").param("active", "false")
                        .with(user(adminPrincipal)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(reload(outsider).isActive()).isTrue();
        mvc.perform(get("/o/globex/admin/users").with(user(adminPrincipal))).andExpect(status().isNotFound());
    }

    @Test
    void stateChangesNeedACsrfToken() throws Exception {
        AppUser target = acmeUser("csrf");
        mvc.perform(post(USERS + "/" + target.getId() + "/active").param("active", "false").with(user(adminPrincipal)))
                .andExpect(status().isForbidden());
        assertThat(reload(target).isActive()).isTrue();
    }

    @Test
    void orgAdminMayCreateAProjectWithoutHoldingAnyProjectRole() throws Exception {
        AppUser fresh = fx.orgAdmin(acmeUser("no-roles"));
        mvc.perform(post("/o/acme/projects").param("name", "Admin Built").with(user(fx.principal(fresh))).with(csrf()))
                .andExpect(status().isFound());
    }
}
