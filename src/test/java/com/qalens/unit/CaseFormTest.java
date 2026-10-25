package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.qalens.domain.CasePriority;
import com.qalens.domain.TestStep;
import com.qalens.service.CaseForm;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseFormTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private CaseForm form(String title, String steps) {
        return new CaseForm(title, null, CasePriority.MEDIUM, steps);
    }

    @Test
    void parsesActionAndExpectedPerLine() {
        CaseForm f = form("Login", "Open page | Page loads\n\n  Enter creds | Dashboard shows  \nClick logout");
        assertThat(f.steps()).containsExactly(
                new TestStep("Open page", "Page loads"),
                new TestStep("Enter creds", "Dashboard shows"),
                new TestStep("Click logout", ""));
    }

    @Test
    void validCaseHasNoViolations() {
        assertThat(validator.validate(form("Login works", "Open page | Page loads"))).isEmpty();
    }

    @Test
    void titleIsRequiredAndSingleLine() {
        assertThat(validator.validate(form(" ", "a | b"))).isNotEmpty();
        assertThat(validator.validate(form("two\nlines", "a | b"))).isNotEmpty();
    }

    @Test
    void atLeastOneStepIsRequired() {
        assertThat(validator.validate(form("t", null))).isNotEmpty();
        assertThat(validator.validate(form("t", "  \n \n"))).isNotEmpty();
    }

    @Test
    void stepCountAndLengthAreBounded() {
        assertThat(validator.validate(form("t", "step\n".repeat(50)))).isEmpty();
        assertThat(validator.validate(form("t", "step\n".repeat(51)))).isNotEmpty();
        assertThat(validator.validate(form("t", "a".repeat(1001)))).isNotEmpty();
        assertThat(validator.validate(form("t", "a | " + "b".repeat(1001)))).isNotEmpty();
    }

    @Test
    void aStepWithoutAnActionIsRejected() {
        assertThat(validator.validate(form("t", " | only expected"))).isNotEmpty();
    }

    @Test
    void stepsRoundTripThroughText() {
        List<TestStep> steps = List.of(new TestStep("A", "B"), new TestStep("C", ""));
        assertThat(CaseForm.of("t", null, CasePriority.LOW, steps).steps()).isEqualTo(steps);
    }
}
