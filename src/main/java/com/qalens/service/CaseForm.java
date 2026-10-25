package com.qalens.service;

import com.qalens.domain.CasePriority;
import com.qalens.domain.TestStep;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * Input for creating a case or saving a new version. Steps arrive as text, one per line, {@code action | expected};
 * the structured step editor comes later and will bind the same {@link TestStep} list.
 */
public record CaseForm(
        @NotBlank(message = "Title is required")
        @Size(max = 500, message = "Title must be at most 500 characters")
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "Title must be a single line of text")
        String title,

        @Size(max = 5000, message = "Precondition must be at most 5000 characters")
        String precondition,

        @NotNull(message = "Priority is required") CasePriority priority,

        @Size(max = 20000, message = "Steps are too long")
        String stepsText) {

    static final int MAX_STEPS = 50;
    static final int MAX_STEP_LENGTH = 1000;

    public CaseForm {
        title = title == null ? null : title.strip();
        precondition = precondition == null || precondition.isBlank() ? null : precondition.strip();
    }

    public static CaseForm blank() {
        return new CaseForm(null, null, CasePriority.MEDIUM, null);
    }

    public static CaseForm of(String title, String precondition, CasePriority priority, List<TestStep> steps) {
        return new CaseForm(title, precondition, priority, toText(steps));
    }

    /** At least one step, at most {@value #MAX_STEPS}, each part at most {@value #MAX_STEP_LENGTH} characters. */
    @AssertTrue(message = "Enter 1 to 50 steps, one per line as: action | expected result (each part up to 1000 characters)")
    public boolean isStepsValid() {
        List<TestStep> parsed = steps();
        if (parsed.isEmpty() || parsed.size() > MAX_STEPS) {
            return false;
        }
        return parsed.stream().allMatch(s -> !s.action().isEmpty()
                && s.action().length() <= MAX_STEP_LENGTH && s.expected().length() <= MAX_STEP_LENGTH);
    }

    public List<TestStep> steps() {
        List<TestStep> out = new ArrayList<>();
        if (stepsText == null) {
            return out;
        }
        for (String line : stepsText.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            int bar = line.indexOf('|');
            String action = (bar < 0 ? line : line.substring(0, bar)).strip();
            String expected = bar < 0 ? "" : line.substring(bar + 1).strip();
            out.add(new TestStep(action, expected));
        }
        return out;
    }

    private static String toText(List<TestStep> steps) {
        StringBuilder sb = new StringBuilder();
        for (TestStep s : steps) {
            sb.append(s.action());
            if (!s.expected().isEmpty()) {
                sb.append(" | ").append(s.expected());
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
