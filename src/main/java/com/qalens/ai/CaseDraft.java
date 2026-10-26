package com.qalens.ai;

import com.qalens.domain.CasePriority;
import com.qalens.domain.TestStep;
import java.util.List;

/** A validated, not yet saved test case proposed by the model. Limits match the manual case form. */
public record CaseDraft(String title, String precondition, CasePriority priority, List<TestStep> steps) {

    public static final int MAX_TITLE = 500;
    public static final int MAX_PRECONDITION = 5000;
    public static final int MAX_STEPS = 50;
    public static final int MAX_STEP_TEXT = 1000;
}
