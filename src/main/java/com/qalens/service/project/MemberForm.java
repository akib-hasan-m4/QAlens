package com.qalens.service.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Adding a member: the user is picked from the organisation, the role from the five project roles. */
public record MemberForm(
        @NotNull(message = "Choose a user")
        Long userId,

        @NotBlank(message = "Choose a role")
        String role) {
}
