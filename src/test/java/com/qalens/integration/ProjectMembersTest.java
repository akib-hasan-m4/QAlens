package com.qalens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.qalens.domain.AppUser;
import com.qalens.domain.Organization;
import com.qalens.domain.Project;
import com.qalens.domain.UserProjectRoleId;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.OrganizationRepository;
import com.qalens.repository.ProjectRepository;
import com.qalens.repository.RoleRepository;
import com.qalens.repository.UserProjectRoleRepository;
import com.qalens.security.QalensUserDetails;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Project membership: who may manage it, what a QA_LEAD may not do, and no self-service changes. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class ProjectMembersTest {

    private static final List<String> ROLES = List.of("ADMIN", "QA_LEAD", "TESTER", "DEVELOPER", "VIEWER");
    private static final List<String> MAY_MANAGE = List.of("ADMIN", "QA_LEAD");

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository orgs;
    @Autowired AppUserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired RoleRepository roles;
    @Autowired UserProjectRoleRepository memberships;
    @Autowired PasswordEncoder encoder;

    private TestFixtures fx;
    private Organization acme;
    private Project project;
    private String base;

    @BeforeEach
    void seed() {
        fx = new TestFixtures(orgs, users, projects, roles, memberships, encoder);
        acme = fx.org("acme");
        project = fx.project(acme, "MEMB");
        base = "/o/acme/projects/" + project.getId() + "/members";
    }

    /** A fresh user per tag, so tests that change memberships do not see each other's changes. */
    private AppUser member(String tag, String role) {
        AppUser user = fx.user(acme, "mb-" + tag + "@acme.test");
        if (role != null) {
            fx.grant(user, project, role);
        }
        return user;
    }

    private QalensUserDetails as(String tag, String role) {
        return fx.principal(member(tag, role));
    }

    private Optional<String> roleOf(AppUser user) {
        return memberships.findRoleName(user.getId(), project.getId());
    }

    @Test
    void onlyAdminAndQaLeadOpenTheMembersPage() throws Exception {
        for (String role : ROLES) {
            var request = get(base).with(user(as("page-" + role.toLowerCase(), role)));
            if (MAY_MANAGE.contains(role)) {
                mvc.perform(request).andExpect(status().isOk())
                        .andExpect(content().string(containsString("mb-page-" + role.toLowerCase() + "@acme.test")));
            } else {
                mvc.perform(request).andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void nonMemberAndOtherOrganisationGetNotFound() throws Exception {
        mvc.perform(get(base).with(user(as("outside", null)))).andExpect(status().isNotFound());
        AppUser globexAdmin = fx.user(fx.org("globex"), "mb-globex@globex.test");
        fx.grant(globexAdmin, fx.project(fx.org("globex"), "GMEM"), "ADMIN");
        mvc.perform(get(base).with(user(fx.principal(globexAdmin)))).andExpect(status().isNotFound());
    }

    @Test
    void adminAddsAMemberWhoThenSeesTheProject() throws Exception {
        AppUser newcomer = member("newcomer", null);
        mvc.perform(post(base).param("userId", newcomer.getId().toString()).param("role", "TESTER")
                        .with(user(as("add-admin", "ADMIN"))).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(base))
                .andExpect(flash().attribute("notice", containsString("added as TESTER")));
        assertThat(roleOf(newcomer)).contains("TESTER");
        mvc.perform(get("/o/acme/projects/" + project.getId()).with(user(fx.principal(newcomer))))
                .andExpect(status().isOk());
    }

    @Test
    void qaLeadManagesNonAdminMembers() throws Exception {
        var lead = as("lead-ok", "QA_LEAD");
        AppUser target = member("lead-target", null);
        mvc.perform(post(base).param("userId", target.getId().toString()).param("role", "VIEWER")
                        .with(user(lead)).with(csrf()))
                .andExpect(status().isFound());
        mvc.perform(post(base + "/" + target.getId()).param("role", "DEVELOPER").with(user(lead)).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(flash().attribute("notice", containsString("is now DEVELOPER")));
        assertThat(roleOf(target)).contains("DEVELOPER");
        mvc.perform(post(base + "/" + target.getId() + "/remove").with(user(lead)).with(csrf()))
                .andExpect(status().isFound());
        assertThat(roleOf(target)).isEmpty();
    }

    @Test
    void qaLeadCannotGrantAdminOrTouchAnAdmin() throws Exception {
        var lead = as("lead-limits", "QA_LEAD");
        AppUser target = member("lead-limits-target", null);
        mvc.perform(post(base).param("userId", target.getId().toString()).param("role", "ADMIN")
                        .with(user(lead)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(roleOf(target)).isEmpty();

        AppUser tester = member("lead-limits-tester", "TESTER");
        mvc.perform(post(base + "/" + tester.getId()).param("role", "ADMIN").with(user(lead)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(roleOf(tester)).contains("TESTER");

        AppUser admin = member("lead-limits-admin", "ADMIN");
        mvc.perform(post(base + "/" + admin.getId()).param("role", "VIEWER").with(user(lead)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post(base + "/" + admin.getId() + "/remove").with(user(lead)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(roleOf(admin)).contains("ADMIN");
    }

    @Test
    void qaLeadDoesNotSeeAdminInTheRoleChoices() throws Exception {
        member("choices-tester", "TESTER");
        mvc.perform(get(base).with(user(as("choices-lead", "QA_LEAD"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<option value=\"ADMIN\""))));
        mvc.perform(get(base).with(user(as("choices-admin", "ADMIN"))))
                .andExpect(content().string(containsString("<option value=\"ADMIN\"")));
    }

    @Test
    void nobodyChangesTheirOwnMembership() throws Exception {
        AppUser self = member("self-admin", "ADMIN");
        var principal = fx.principal(self);
        mvc.perform(post(base + "/" + self.getId()).param("role", "VIEWER").with(user(principal)).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(flash().attribute("error", containsString("your own role")));
        mvc.perform(post(base + "/" + self.getId() + "/remove").with(user(principal)).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(flash().attribute("error", containsString("remove yourself")));
        assertThat(roleOf(self)).contains("ADMIN");
    }

    @Test
    void demotionTakesEffectOnTheNextRequest() throws Exception {
        AppUser lead = member("demoted", "QA_LEAD");
        var principal = fx.principal(lead);
        mvc.perform(get("/o/acme/projects/" + project.getId() + "/settings").with(user(principal)))
                .andExpect(status().isOk());
        mvc.perform(post(base + "/" + lead.getId()).param("role", "VIEWER")
                        .with(user(as("demoter", "ADMIN"))).with(csrf()))
                .andExpect(status().isFound());
        mvc.perform(get("/o/acme/projects/" + project.getId() + "/settings").with(user(principal)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotAddUsersOfAnotherOrganisationOrDeactivatedOrTwice() throws Exception {
        var admin = as("checks-admin", "ADMIN");
        AppUser outsider = fx.user(fx.org("globex"), "mb-outsider@globex.test");
        mvc.perform(post(base).param("userId", outsider.getId().toString()).param("role", "VIEWER")
                        .with(user(admin)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(roleOf(outsider)).isEmpty();

        AppUser inactive = member("inactive", null);
        inactive.setActive(false);
        users.save(inactive);
        mvc.perform(post(base).param("userId", inactive.getId().toString()).param("role", "VIEWER")
                        .with(user(admin)).with(csrf()))
                .andExpect(flash().attribute("error", containsString("deactivated")));
        assertThat(roleOf(inactive)).isEmpty();

        AppUser existing = member("existing", "VIEWER");
        mvc.perform(post(base).param("userId", existing.getId().toString()).param("role", "TESTER")
                        .with(user(admin)).with(csrf()))
                .andExpect(flash().attribute("error", containsString("already a member")));
        assertThat(roleOf(existing)).contains("VIEWER");
    }

    @Test
    void unknownRoleIsRejected() throws Exception {
        AppUser target = member("unknown-role", null);
        mvc.perform(post(base).param("userId", target.getId().toString()).param("role", "SUPERUSER")
                        .with(user(as("unknown-admin", "ADMIN"))).with(csrf()))
                .andExpect(flash().attribute("error", containsString("project roles")));
        assertThat(memberships.existsById(new UserProjectRoleId(target.getId(), project.getId()))).isFalse();
    }

    @Test
    void testerCannotManageMembers() throws Exception {
        var tester = as("tester-tries", "TESTER");
        AppUser target = member("tester-target", null);
        mvc.perform(post(base).param("userId", target.getId().toString()).param("role", "VIEWER")
                        .with(user(tester)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(roleOf(target)).isEmpty();
    }

    @Test
    void stateChangesNeedACsrfToken() throws Exception {
        AppUser target = member("csrf-target", "VIEWER");
        mvc.perform(post(base + "/" + target.getId() + "/remove").with(user(as("csrf-admin", "ADMIN"))))
                .andExpect(status().isForbidden());
        assertThat(roleOf(target)).contains("VIEWER");
    }
}
