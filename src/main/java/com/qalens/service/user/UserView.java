package com.qalens.service.user;

/** {@code self} marks the caller's own row, whose controls are hidden: nobody locks themselves out. */
public record UserView(Long id, String email, String displayName, boolean active, boolean orgAdmin,
                       long projectCount, boolean self) {
}
