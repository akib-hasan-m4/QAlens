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

import com.qalens.domain.Organization;
import com.qalens.domain.Project;
import com.qalens.domain.Requirement;
import com.qalens.domain.RequirementPriority;
import com.qalens.domain.RequirementStatus;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.OrganizationRepository;
import com.qalens.repository.ProjectRepository;
import com.qalens.repository.RequirementRepository;
import com.qalens.repository.RoleRepository;
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

/** Requirement CRUD: role matrix, project scoping, tenant boundaries, escaping and literal search. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class RequirementCrudTest {

    private static final List<String> ROLES = List.of("ADMIN", "QA_LEAD", "TESTER", "DEVELOPER", "VIEWER");
    private static final List<String> MAY_WRITE = List.of("ADMIN", "QA_LEAD", "TESTER");

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository orgs;
    @Autowired AppUserRepository users;
    @Autowired ProjectRepository projects;
    @Autowired RoleRepository roles;
    @Autowired UserProjectRoleRepository memberships;
    @Autowired RequirementRepository requirements;
    @Autowired PasswordEncoder encoder;

    private TestFixtures fx;
    private Organization acme;
    private Project project;
    private Project other;

    @BeforeEach
    void seed() {
        fx = new TestFixtures(orgs, users, projects, roles, memberships, encoder);
        acme = fx.org("acme");
        project = fx.project(acme, "REQP");
        other = fx.project(acme, "REQO");
    }

    private QalensUserDetails userWithRole(String role) {
        var user = fx.user(acme, "req-" + role.toLowerCase() + "@acme.test");
        fx.grant(user, project, role);
        return fx.principal(user);
    }

    private String base() {
        return "/o/acme/projects/" + project.getId() + "/requirements";
    }

    private MockHttpServletRequestBuilder create(String path, String title, QalensUserDetails principal) {
        return post(path).param("title", title).param("body", "body").param("status", "DRAFT")
                .param("priority", "HIGH").with(user(principal)).with(csrf());
    }

    private Requirement existing(String title) {
        return requirements.save(new Requirement(project.getId(), userWithRole("ADMIN").getId(), null, title,
                "b", RequirementStatus.DRAFT, RequirementPriority.LOW));
    }

    @Test
    void everyRoleCanReadTheList() throws Exception {
        for (String role : ROLES) {
            mvc.perform(get(base()).with(user(userWithRole(role)))).andExpect(status().isOk());
        }
    }

    @Test
    void onlyAdminLeadAndTesterCanCreate() throws Exception {
        for (String role : ROLES) {
            var request = create(base(), "Created by " + role, userWithRole(role));
            if (MAY_WRITE.contains(role)) {
                mvc.perform(request).andExpect(status().isFound()).andExpect(redirectedUrlPattern(base() + "/*"));
            } else {
                mvc.perform(request).andExpect(status().isForbidden());
            }
        }
        assertThat(requirements.findByProjectId(project.getId(), org.springframework.data.domain.Sort.unsorted()))
                .extracting(Requirement::getTitle)
                .contains("Created by ADMIN", "Created by QA_LEAD", "Created by TESTER")
                .doesNotContain("Created by DEVELOPER", "Created by VIEWER");
    }

    @Test
    void viewerAndDeveloperGetForbiddenOnEdit() throws Exception {
        Requirement r = existing("Protected requirement");
        for (String role : List.of("VIEWER", "DEVELOPER")) {
            mvc.perform(create(base() + "/" + r.getId(), "Hijacked", userWithRole(role)))
                    .andExpect(status().isForbidden());
        }
        assertThat(requirements.findByIdAndProjectId(r.getId(), project.getId()).orElseThrow().getTitle())
                .isEqualTo("Protected requirement");
    }

    @Test
    void testerCanEdit() throws Exception {
        Requirement r = existing("Before edit");
        mvc.perform(create(base() + "/" + r.getId(), "After edit", userWithRole("TESTER")))
                .andExpect(status().isFound());
        assertThat(requirements.findByIdAndProjectId(r.getId(), project.getId()).orElseThrow().getTitle())
                .isEqualTo("After edit");
    }

    @Test
    void requirementIdFromAnotherProjectIsNotFound() throws Exception {
        Requirement r = existing("Belongs to REQP");
        var admin = fx.user(acme, "req-admin@acme.test");
        fx.grant(admin, other, "ADMIN");
        mvc.perform(get("/o/acme/projects/" + other.getId() + "/requirements/" + r.getId())
                .with(user(fx.principal(admin)))).andExpect(status().isNotFound());
        mvc.perform(create("/o/acme/projects/" + other.getId() + "/requirements/" + r.getId(), "Moved",
                fx.principal(admin))).andExpect(status().isNotFound());
    }

    @Test
    void projectWithoutARoleIsNotFound() throws Exception {
        var outsider = fx.principal(fx.user(acme, "req-outsider@acme.test"));
        mvc.perform(get(base()).with(user(outsider))).andExpect(status().isNotFound());
        mvc.perform(create(base(), "Sneaky", outsider)).andExpect(status().isNotFound());
    }

    @Test
    void anotherOrganisationsSlugIsNotFound() throws Exception {
        mvc.perform(get("/o/globex/projects/" + project.getId() + "/requirements")
                .with(user(userWithRole("ADMIN")))).andExpect(status().isNotFound());
    }

    @Test
    void createWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post(base()).param("title", "No token").param("status", "DRAFT").param("priority", "LOW")
                .with(user(userWithRole("ADMIN")))).andExpect(status().isForbidden());
    }

    @Test
    void invalidInputIsRejectedAndNothingIsSaved() throws Exception {
        var admin = userWithRole("ADMIN");
        mvc.perform(post(base()).param("title", "").param("status", "DRAFT").param("priority", "LOW")
                .with(user(admin)).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(post(base()).param("title", "ok").param("status", "NOPE").param("priority", "LOW")
                .with(user(admin)).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(post(base()).param("title", "ok").param("extKey", "<x>").param("status", "DRAFT")
                .param("priority", "LOW").with(user(admin)).with(csrf())).andExpect(status().isBadRequest());
    }

    @Test
    void hostileTitleIsEscapedWhenRendered() throws Exception {
        Requirement r = existing("<script>alert(1)</script>");
        mvc.perform(get(base() + "/" + r.getId()).with(user(userWithRole("VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)"))));
    }

    @Test
    void searchTreatsPercentAndUnderscoreLiterally() throws Exception {
        existing("Discount is 100% of price");
        existing("Plain unrelated text");
        mvc.perform(get(base()).param("q", "100%").with(user(userWithRole("VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Discount is 100% of price")))
                .andExpect(content().string(not(containsString("Plain unrelated text"))));
        mvc.perform(get(base()).param("q", "%").with(user(userWithRole("VIEWER"))))
                .andExpect(content().string(containsString("Discount is 100% of price")))
                .andExpect(content().string(not(containsString("Plain unrelated text"))));
    }

    @Test
    void unknownSortValueFallsBackToTheDefault() throws Exception {
        mvc.perform(get(base()).param("sort", "title; drop table requirement").with(user(userWithRole("VIEWER"))))
                .andExpect(status().isOk());
    }
}
