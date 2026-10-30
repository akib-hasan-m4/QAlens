package com.qalens.service.ai;

import com.qalens.service.testcase.CaseForm;

/** One AI draft as shown in the review panel: the case fields plus whether the user keeps it. */
public class DraftInput extends CaseForm {

    private boolean selected;

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
