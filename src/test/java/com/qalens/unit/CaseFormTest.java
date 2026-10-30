package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.qalens.domain.CasePriority;
import com.qalens.domain.TestStep;
import com.qalens.service.testcase.CaseForm;
import com.qalens.service.testcase.StepInput;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseFormTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private CaseForm form(String title, StepInput... steps) {
        return new CaseForm(title, null, CasePriority.MEDIUM, List.of(steps));
    }

    private static StepInput step(String action, String expected) {
        return new StepInput(action, expected);
    }

    private static List<TestStep> parsed(CaseForm f) {
        return f.parsedSteps();
    }

    @Test
    void stepsAreTrimmedAndBlankRowsDropped() {
        CaseForm f = form("Login", step("  Open page ", " Page loads "), step("", "  "), step(null, null),
                step("Click logout", null));
        assertThat(parsed(f)).containsExactly(new TestStep("Open page", "Page loads"), new TestStep("Click logout", ""));
    }

    @Test
    void nullStepListIsEmpty() {
        assertThat(new CaseForm("t", null, CasePriority.LOW, null).parsedSteps()).isEmpty();
    }

    @Test
    void validCaseHasNoViolations() {
        assertThat(validator.validate(form("Login works", step("Open page", "Page loads")))).isEmpty();
    }

    @Test
    void titleIsRequiredAndSingleLine() {
        assertThat(validator.validate(form(" ", step("a", "b")))).isNotEmpty();
        assertThat(validator.validate(form("two\nlines", step("a", "b")))).isNotEmpty();
    }

    @Test
    void atLeastOneStepIsRequired() {
        assertThat(validator.validate(form("t"))).isNotEmpty();
        assertThat(validator.validate(form("t", step(" ", " ")))).isNotEmpty();
    }

    @Test
    void stepCountAndLengthAreBounded() {
        assertThat(validator.validate(form("t", repeat(50)))).isEmpty();
        assertThat(validator.validate(form("t", repeat(51)))).isNotEmpty();
        assertThat(validator.validate(form("t", step("a".repeat(1001), "")))).isNotEmpty();
        assertThat(validator.validate(form("t", step("a", "b".repeat(1001))))).isNotEmpty();
    }

    @Test
    void aStepWithoutAnActionIsRejected() {
        assertThat(validator.validate(form("t", step("", "only expected")))).isNotEmpty();
    }

    @Test
    void editorOffersOneSpareRow() {
        CaseForm f = form("t", step("a", "b"));
        assertThat(f.editorRows()).extracting(StepInput::getAction).containsExactly("a", "");
        assertThat(CaseForm.blank().editorRows()).hasSize(1);
    }

    private static StepInput[] repeat(int n) {
        List<StepInput> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(step("step " + i, ""));
        }
        return out.toArray(new StepInput[0]);
    }
}
