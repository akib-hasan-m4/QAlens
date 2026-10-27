package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.qalens.service.DraftInput;
import com.qalens.service.DraftsForm;
import com.qalens.service.StepInput;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Drafts the user edited are validated with the same rules as a manually written case. */
class DraftsFormTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static DraftInput draft(String title, boolean selected, StepInput... steps) {
        DraftInput d = new DraftInput();
        d.setTitle(title);
        d.setSelected(selected);
        d.setSteps(new ArrayList<>(List.of(steps)));
        return d;
    }

    @Test
    void onlyTickedDraftsAreSelected() {
        DraftsForm form = new DraftsForm();
        form.setDrafts(Arrays.asList(draft("A", true, new StepInput("a", "")), null,
                draft("B", false, new StepInput("b", ""))));

        assertThat(form.selectedDrafts()).extracting(DraftInput::getTitle).containsExactly("A");
    }

    @Test
    void anEditedDraftWithoutTitleOrStepsIsInvalid() {
        assertThat(validator.validate(draft("", true, new StepInput("a", "")))).extracting(v -> v.getPropertyPath()
                .toString()).containsExactly("title");
        assertThat(validator.validate(draft("Title", true, new StepInput(" ", " ")))).extracting(v -> v
                .getPropertyPath().toString()).containsExactly("stepsValid");
        assertThat(validator.validate(draft("Title", true, new StepInput("x".repeat(1001), "")))).isNotEmpty();
    }

    @Test
    void aValidDraftPasses() {
        assertThat(validator.validate(draft("Title", true, new StepInput("Do", "Done"), new StepInput("", "")))).isEmpty();
    }
}
