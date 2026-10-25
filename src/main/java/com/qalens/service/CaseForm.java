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
import java.util.Objects;

/**
 * Input for creating a case or saving a new version. A bean rather than a record because the step rows are bound
 * from indexed fields ({@code steps[0].action}) and Spring grows the list through the setter-style binder.
 * Rows left completely blank are ignored, so the editor can always offer a spare empty row.
 */
public class CaseForm {

    static final int MAX_STEPS = 50;
    static final int MAX_STEP_LENGTH = 1000;

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title must be at most 500 characters")
    @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "Title must be a single line of text")
    private String title;

    @Size(max = 5000, message = "Precondition must be at most 5000 characters")
    private String precondition;

    @NotNull(message = "Priority is required")
    private CasePriority priority = CasePriority.MEDIUM;

    private List<StepInput> steps = new ArrayList<>();

    public CaseForm() {
    }

    public CaseForm(String title, String precondition, CasePriority priority, List<StepInput> steps) {
        setTitle(title);
        setPrecondition(precondition);
        this.priority = priority;
        this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
    }

    public static CaseForm blank() {
        return new CaseForm();
    }

    /** Builds a form from saved steps, for prefilling the editor or for AI drafts. */
    public static CaseForm of(String title, String precondition, CasePriority priority, List<TestStep> steps) {
        return new CaseForm(title, precondition, priority,
                steps.stream().map(s -> new StepInput(s.action(), s.expected())).toList());
    }

    /** The meaningful steps: trimmed, with fully blank rows removed. */
    public List<TestStep> parsedSteps() {
        return steps.stream()
                .filter(Objects::nonNull)
                .map(s -> new TestStep(clean(s.getAction()), clean(s.getExpected())))
                .filter(s -> !s.action().isEmpty() || !s.expected().isEmpty())
                .toList();
    }

    /** At least one step, at most {@value #MAX_STEPS}, an action in every step, each part within the length limit. */
    @AssertTrue(message = "Enter 1 to 50 steps, each with an action; each part up to 1000 characters")
    public boolean isStepsValid() {
        List<TestStep> parsed = parsedSteps();
        if (parsed.isEmpty() || parsed.size() > MAX_STEPS) {
            return false;
        }
        return parsed.stream().allMatch(s -> !s.action().isEmpty()
                && s.action().length() <= MAX_STEP_LENGTH && s.expected().length() <= MAX_STEP_LENGTH);
    }

    /** What the editor shows: the rows as entered plus one empty row to type into. */
    public List<StepInput> editorRows() {
        List<StepInput> rows = new ArrayList<>();
        for (TestStep s : parsedSteps()) {
            rows.add(new StepInput(s.action(), s.expected()));
        }
        rows.add(new StepInput("", ""));
        return rows;
    }

    private static String clean(String s) {
        return s == null ? "" : s.strip();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? null : title.strip();
    }

    public String getPrecondition() {
        return precondition;
    }

    public void setPrecondition(String precondition) {
        this.precondition = precondition == null || precondition.isBlank() ? null : precondition.strip();
    }

    public CasePriority getPriority() {
        return priority;
    }

    public void setPriority(CasePriority priority) {
        this.priority = priority;
    }

    public List<StepInput> getSteps() {
        return steps;
    }

    public void setSteps(List<StepInput> steps) {
        this.steps = steps;
    }
}
