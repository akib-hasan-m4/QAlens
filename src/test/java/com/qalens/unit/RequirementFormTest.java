package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.qalens.domain.RequirementPriority;
import com.qalens.domain.RequirementStatus;
import com.qalens.service.RequirementForm;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class RequirementFormTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(String key, String title, String body) {
        return validator.validate(new RequirementForm(key, title, body,
                RequirementStatus.DRAFT, RequirementPriority.MEDIUM)).isEmpty();
    }

    @Test
    void ordinaryRequirementPasses() {
        assertThat(valid("PAY-R12", "A user can reset their password by email", "Details\nover two lines")).isTrue();
        assertThat(valid(null, "Titles may use quotes, <angle> brackets & symbols", null)).isTrue();
    }

    @Test
    void titleIsRequiredAndSingleLine() {
        assertThat(valid(null, null, null)).isFalse();
        assertThat(valid(null, "   ", null)).isFalse();
        assertThat(valid(null, "two\nlines", null)).isFalse();
    }

    @Test
    void keyIsRestrictedToASafeAlphabet() {
        assertThat(valid("REQ_1.2-a", "t", null)).isTrue();
        assertThat(valid("-bad", "t", null)).isFalse();
        assertThat(valid("has space", "t", null)).isFalse();
        assertThat(valid("<x>", "t", null)).isFalse();
        assertThat(valid("k".repeat(51), "t", null)).isFalse();
    }

    @Test
    void lengthLimitsApply() {
        assertThat(valid(null, "t".repeat(500), null)).isTrue();
        assertThat(valid(null, "t".repeat(501), null)).isFalse();
        assertThat(valid(null, "t", "b".repeat(10000))).isTrue();
        assertThat(valid(null, "t", "b".repeat(10001))).isFalse();
    }

    @Test
    void statusAndPriorityAreRequired() {
        assertThat(validator.validate(new RequirementForm(null, "t", null, null, RequirementPriority.LOW))).isNotEmpty();
        assertThat(validator.validate(new RequirementForm(null, "t", null, RequirementStatus.DRAFT, null))).isNotEmpty();
    }

    @Test
    void blankOptionalFieldsBecomeNull() {
        RequirementForm form = new RequirementForm("  ", " t ", "  ", RequirementStatus.DRAFT, RequirementPriority.HIGH);
        assertThat(form.extKey()).isNull();
        assertThat(form.title()).isEqualTo("t");
        assertThat(form.body()).isNull();
    }
}
