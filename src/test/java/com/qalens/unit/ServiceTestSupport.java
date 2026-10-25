package com.qalens.unit;

import com.qalens.service.QalensUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Signs a fake user into the security context, as the service layer reads the caller from there. */
final class ServiceTestSupport {

    private ServiceTestSupport() {
    }

    static void signIn(long userId, long orgId) {
        var user = new QalensUserDetails(userId, "u" + userId + "@acme.test", "User " + userId, orgId, "acme", "Acme",
                "x", true);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    static void signOut() {
        SecurityContextHolder.clearContext();
    }
}
