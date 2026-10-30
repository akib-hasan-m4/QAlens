package com.qalens.service.requirement;

import com.qalens.domain.RequirementPriority;
import com.qalens.domain.RequirementStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Input for creating and editing a requirement; the project and author come from the URL and the session. */
public record RequirementForm(
        @Pattern(regexp = "^$|^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$",
                message = "Use letters, digits, . _ - only, up to 50 characters")
        String extKey,

        @NotBlank(message = "Title is required")
        @Size(max = 500, message = "Title must be at most 500 characters")
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "Title must be a single line of text")
        String title,

        @Size(max = 10000, message = "Description must be at most 10000 characters")
        String body,

        @NotNull(message = "Status is required") RequirementStatus status,
        @NotNull(message = "Priority is required") RequirementPriority priority) {

    public RequirementForm {
        extKey = extKey == null || extKey.isBlank() ? null : extKey.strip();
        title = title == null ? null : title.strip();
        body = body == null || body.isBlank() ? null : body.strip();
    }

    public static RequirementForm blank() {
        return new RequirementForm(null, null, null, RequirementStatus.DRAFT, RequirementPriority.MEDIUM);
    }
}
