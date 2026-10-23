package com.qalens.tenant;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Resolves {@code /o/{slug}/...}. Requests outside that prefix have no tenant. */
@Component
public class PathTenantResolver implements TenantResolver {

    private static final String PREFIX = "/o/";

    @Override
    public Optional<String> resolveSlug(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        if (!path.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rest = path.substring(PREFIX.length());
        int slash = rest.indexOf('/');
        return Optional.of(slash < 0 ? rest : rest.substring(0, slash));
    }
}
