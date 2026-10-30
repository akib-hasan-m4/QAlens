package com.qalens.service.testcase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Input for creating or renaming a suite. {@code parentId} is only read on create and re-checked in the service. */
public record SuiteForm(
        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "Name must be a single line of text")
        String name,

        Long parentId) {

    public SuiteForm {
        name = name == null ? null : name.strip();
    }

    public static SuiteForm blank() {
        return new SuiteForm(null, null);
    }
}
