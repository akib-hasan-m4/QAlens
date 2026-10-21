package com.qalens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.qalens.domain.AppUser;
import com.qalens.domain.Organization;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class SecurityHardeningTest {

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository orgs;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void seed() {
        Organization org = orgs.findBySlug("acme").orElseGet(() -> orgs.save(new Organization("acme", "Acme Corp")));
        if (!users.existsByEmailIgnoreCase("hard@acme.test")) {
            users.save(new AppUser(org, "hard@acme.test", "Hard User", encoder.encode("secret"), true));
        }
        if (!users.existsByEmailIgnoreCase("xss@acme.test")) {
            users.save(new AppUser(org, "xss@acme.test", "<script>alert(1)</script>", encoder.encode("secret"), true));
        }
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/login").with(csrf()).param("email", email).param("password", password)).andReturn();
    }

    private MockHttpSession sessionFor(String email) throws Exception {
        return (MockHttpSession) login(email, "secret").getRequest().getSession(false);
    }

    private void assertSecurityHeaders(ResultActions result) throws Exception {
        result.andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Content-Security-Policy", not(containsString("unsafe-inline"))))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "same-origin"))
                .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(post("/login").param("email", "hard@acme.test").param("password", "secret"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(post("/logout").session(sessionFor("hard@acme.test"))).andExpect(status().isForbidden());
    }

    @Test
    void publicPageCarriesSecurityHeaders() throws Exception {
        assertSecurityHeaders(mvc.perform(get("/login")).andExpect(status().isOk()));
    }

    @Test
    void authenticatedPageCarriesSecurityHeadersAndIsNotCached() throws Exception {
        assertSecurityHeaders(mvc.perform(get("/o/acme/").session(sessionFor("hard@acme.test")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store"))));
    }

    @Test
    void sessionIdChangesOnLogin() throws Exception {
        MockHttpSession before = (MockHttpSession) mvc.perform(get("/login")).andReturn().getRequest().getSession(true);
        String oldId = before.getId();
        MvcResult result = mvc.perform(post("/login").session(before).with(csrf())
                .param("email", "hard@acme.test").param("password", "secret")).andReturn();
        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(oldId);
    }

    @Test
    void unknownEmailAndWrongPasswordAreIndistinguishable() throws Exception {
        MvcResult unknown = login("nobody@acme.test", "secret");
        MvcResult wrong = login("hard@acme.test", "wrong");
        assertThat(unknown.getResponse().getStatus()).isEqualTo(wrong.getResponse().getStatus());
        assertThat(unknown.getResponse().getRedirectedUrl()).isEqualTo(wrong.getResponse().getRedirectedUrl())
                .isEqualTo("/login?error");
    }

    @Test
    void errorPageLeaksNoInternals() throws Exception {
        mvc.perform(get("/o/other-org/").session(sessionFor("hard@acme.test")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("org.springframework"))))
                .andExpect(content().string(not(containsString("at com.qalens"))));
    }

    @Test
    void hostileDisplayNameIsEscaped() throws Exception {
        mvc.perform(get("/o/acme/").session(sessionFor("xss@acme.test")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }
}
