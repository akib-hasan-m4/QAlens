package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.qalens.domain.TestSuite;
import com.qalens.service.SuiteForm;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class SuiteFormTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void nameIsRequiredAndSingleLine() {
        assertThat(validator.validate(new SuiteForm("Smoke tests", null))).isEmpty();
        assertThat(validator.validate(new SuiteForm("  ", null))).isNotEmpty();
        assertThat(validator.validate(new SuiteForm("a\nb", null))).isNotEmpty();
        assertThat(validator.validate(new SuiteForm("x".repeat(201), null))).isNotEmpty();
    }
}
