package com.qalens.service.project;

/** {@code editable} is false for the caller's own row and, for a QA_LEAD, for ADMIN members. */
public record MemberView(Long userId, String email, String displayName, boolean active, String role,
                         boolean self, boolean editable) {
}
