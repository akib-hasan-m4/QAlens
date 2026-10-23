package com.qalens.config;

import com.qalens.repository.OrganizationRepository;
import com.qalens.service.QalensUserDetails;
import com.qalens.tenant.TenantContext;
import com.qalens.tenant.TenantFilter;
import com.qalens.tenant.TenantResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    static final String SESSION_COOKIE = "QALENS_SESSION";

    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; object-src 'none'; base-uri 'self'; form-action 'self'; "
            + "frame-ancestors 'none'";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, TenantResolver tenantResolver,
                                    OrganizationRepository organizations, TenantContext tenantContext) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/error", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .successHandler(orgLandingHandler())
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies(SESSION_COOKIE))
                .addFilterBefore(new TenantFilter(tenantResolver, organizations, tenantContext), AuthorizationFilter.class)
                .sessionManagement(sm -> sm.sessionFixation(fix -> fix.changeSessionId()))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .frameOptions(fo -> fo.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .permissionsPolicyHeader(pp -> pp.policy("camera=(), microphone=(), geolocation=()")));
        return http.build();
    }

    private AuthenticationSuccessHandler orgLandingHandler() {
        return (request, response, authentication) -> {
            var principal = (QalensUserDetails) authentication.getPrincipal();
            var delegate = new SimpleUrlAuthenticationSuccessHandler("/o/" + principal.getOrgSlug() + "/");
            delegate.setAlwaysUseDefaultTargetUrl(true);
            delegate.onAuthenticationSuccess(request, response, authentication);
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
