package com.qalens.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Input for creating and editing a project. Only these fields can be bound; the key is never user-supplied. */
public record ProjectForm(
        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        @Pattern(regexp = "^[\\p{L}\\p{N}][\\p{L}\\p{N} ._&'()/:,-]*$",
                message = "Use letters, digits, spaces and . _ & ' ( ) / : , - only, starting with a letter or digit")
        String name,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description) {

    public ProjectForm {
        name = name == null ? null : name.strip();
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
