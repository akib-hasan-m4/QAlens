package com.qalens.tenant;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/** Extracts the organisation slug from a request. A subdomain implementation can replace the path one. */
public interface TenantResolver {

    Optional<String> resolveSlug(HttpServletRequest request);
}
