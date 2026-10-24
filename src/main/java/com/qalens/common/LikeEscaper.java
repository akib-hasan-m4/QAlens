package com.qalens.common;

/**
 * Escapes {@code %}, {@code _} and the escape character itself so a user's search term matches literally.
 * Bind the result as a parameter and declare {@code escape '\'} in the query.
 */
public final class LikeEscaper {

    private LikeEscaper() {
    }

    public static String escape(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** {@code %term%} with the term escaped, ready to bind to a {@code like ... escape '\'} parameter. */
    public static String contains(String term) {
        return "%" + escape(term) + "%";
    }
}
