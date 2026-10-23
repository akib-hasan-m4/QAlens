package com.qalens.tenant;

import java.util.Set;
import java.util.regex.Pattern;

/** Which strings may be an organisation slug. Reserved words would collide with application routes. */
public final class SlugPolicy {

    public static final Set<String> RESERVED = Set.of(
            "admin", "api", "login", "logout", "static", "assets", "health", "actuator", "signup", "o");

    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$");

    private SlugPolicy() {
    }

    public static boolean isAllowed(String slug) {
        return slug != null && FORMAT.matcher(slug).matches() && !RESERVED.contains(slug);
    }
}
