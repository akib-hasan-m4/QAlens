package com.qalens.service;

import java.text.Normalizer;
import java.util.Locale;

/** Derives short project keys such as {@code PG} from "Payment Gateway"; the service resolves collisions. */
public final class ProjectKeys {

    public static final int MAX_LENGTH = 10;

    private ProjectKeys() {
    }

    /** Initials of a multi-word name, or the first letters of a single word. Always {@code [A-Z][A-Z0-9]{1,9}}. */
    public static String base(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
        String key;
        if (ascii.isEmpty()) {
            key = "PRJ";
        } else if (ascii.contains(" ")) {
            StringBuilder initials = new StringBuilder();
            for (String word : ascii.split(" ")) {
                initials.append(word.charAt(0));
            }
            key = initials.substring(0, Math.min(initials.length(), 6));
        } else {
            key = ascii.substring(0, Math.min(ascii.length(), 5));
        }
        if (Character.isDigit(key.charAt(0))) {
            key = "P" + key;
        }
        while (key.length() < 2) {
            key += "X";
        }
        return key.substring(0, Math.min(key.length(), MAX_LENGTH));
    }

    /** {@code attempt} 1 is the base itself; later attempts append a number, trimming the base to fit. */
    public static String candidate(String base, int attempt) {
        if (attempt <= 1) {
            return base;
        }
        String suffix = String.valueOf(attempt);
        return base.substring(0, Math.min(base.length(), MAX_LENGTH - suffix.length())) + suffix;
    }
}
