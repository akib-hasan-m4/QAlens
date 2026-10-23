package com.qalens.tenant;

import com.qalens.domain.Organization;
import com.qalens.repository.OrganizationRepository;
import com.qalens.service.QalensUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs after authentication and before authorization. Anything that is not a real organisation, or is
 * another organisation than the caller's, is a 404, so no response confirms that an organisation exists.
 * Deliberately not a {@code @Component}: it is added to the security chain only, never registered twice.
 */
public class TenantFilter extends OncePerRequestFilter {

    private final TenantResolver resolver;
    private final OrganizationRepository organizations;
    private final TenantContext context;

    public TenantFilter(TenantResolver resolver, OrganizationRepository organizations, TenantContext context) {
        this.resolver = resolver;
        this.organizations = organizations;
        this.context = context;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> slug = resolver.resolveSlug(request);
        if (slug.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        Optional<Organization> org = slug.filter(SlugPolicy::isAllowed).flatMap(organizations::findBySlug);
        if (org.isEmpty() || belongsToAnotherOrganisation(org.get())) {
            response.sendError(HttpStatus.NOT_FOUND.value());
            return;
        }
        context.set(org.get());
        chain.doFilter(request, response);
    }

    private boolean belongsToAnotherOrganisation(Organization org) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof QalensUserDetails user
                && !user.getOrgId().equals(org.getId());
    }
}
