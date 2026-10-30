package com.qalens.service.user;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Input for creating a user. The organisation is always the caller's and is never bound from the request.
 * The initial password is set by the admin and handed over out of band.
 */
public record NewUserForm(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        @Pattern(regexp = "^[\\p{L}][\\p{L}\\p{M} .'-]*$",
                message = "Use letters, spaces and . ' - only, starting with a letter")
        String displayName,

        @NotBlank(message = "Password is required")
        @Size(min = 10, message = "Password must be at least 10 characters")
        String password,

        Boolean orgAdmin) {

    /** BCrypt only reads the first 72 bytes of a password. */
    static final int MAX_PASSWORD_BYTES = 72;

    public NewUserForm {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        displayName = displayName == null ? null : displayName.strip();
        // an unticked checkbox sends nothing
        orgAdmin = Boolean.TRUE.equals(orgAdmin);
    }

    @AssertTrue(message = "Password must be at most 72 bytes")
    public boolean isPasswordWithinLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }
}
