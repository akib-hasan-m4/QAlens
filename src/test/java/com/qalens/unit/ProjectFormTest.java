package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.qalens.service.project.ProjectForm;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ProjectFormTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(String name, String description) {
        return validator.validate(new ProjectForm(name, description)).isEmpty();
    }

    @Test
    void ordinaryNamesPass() {
        assertThat(valid("Payment Gateway", "Handles cards")).isTrue();
        assertThat(valid("Café (v2) - R&D, phase 1/2", null)).isTrue();
    }

    @Test
    void blankOrMissingNameFails() {
        assertThat(valid(null, null)).isFalse();
        assertThat(valid("   ", null)).isFalse();
    }

    @Test
    void markupAndControlCharactersAreOutsideTheAllowlist() {
        assertThat(valid("<script>alert(1)</script>", null)).isFalse();
        assertThat(valid("name\nwith newline", null)).isFalse();
        assertThat(valid("-leading dash", null)).isFalse();
        assertThat(valid("semi;colon", null)).isFalse();
    }

    @Test
    void lengthLimitsApply() {
        assertThat(valid("a".repeat(200), null)).isTrue();
        assertThat(valid("a".repeat(201), null)).isFalse();
        assertThat(valid("ok", "d".repeat(2001))).isFalse();
    }

    @Test
    void inputIsTrimmedAndBlankDescriptionBecomesNull() {
        ProjectForm form = new ProjectForm("  Name  ", "   ");
        assertThat(form.name()).isEqualTo("Name");
        assertThat(form.description()).isNull();
    }
}
