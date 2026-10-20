package com.qalens.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestContainer.class)
class AuthFlowTest {

    @Autowired MockMvc mvc;
    @Autowired OrganizationRepository orgs;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void seed() {
        Organization org = orgs.findBySlug("acme").orElseGet(() -> orgs.save(new Organization("acme", "Acme Corp")));
        if (!users.existsByEmailIgnoreCase("ada@acme.test")) {
            users.save(new AppUser(org, "ada@acme.test", "Ada Admin", encoder.encode("secret"), true));
            users.save(new AppUser(org, "off@acme.test", "Off User", encoder.encode("secret"), false));
        }
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/login").with(csrf()).param("email", email).param("password", password)).andReturn();
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/o/acme/")).andExpect(status().isFound()).andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void loginPageRenders() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Sign in")));
    }

    @Test
    void validLoginLandsOnOrgHomeAndLogoutEndsSession() throws Exception {
        MvcResult result = login("ada@acme.test", "secret");
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/o/acme/");
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

        mvc.perform(get("/o/acme/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ada Admin")));

        mvc.perform(post("/logout").with(csrf()).session(session))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login?logout"));

        mvc.perform(get("/o/acme/").session(session)).andExpect(status().isFound());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        org.assertj.core.api.Assertions.assertThat(login("ada@acme.test", "nope").getResponse().getRedirectedUrl())
                .isEqualTo("/login?error");
    }

    @Test
    void inactiveUserIsRejected() throws Exception {
        org.assertj.core.api.Assertions.assertThat(login("off@acme.test", "secret").getResponse().getRedirectedUrl())
                .isEqualTo("/login?error");
    }

    @Test
    void otherOrgSlugReturns404() throws Exception {
        MockHttpSession session = (MockHttpSession) login("ada@acme.test", "secret").getRequest().getSession(false);
        mvc.perform(get("/o/other-org/").session(session)).andExpect(status().isNotFound());
    }
}
