package com.qalens.service;

import java.util.ArrayList;
import java.util.List;

/**
 * The review panel's form. Drafts are not stored anywhere between generating and accepting: they travel in the
 * page as ordinary fields, so whatever the user edited is bound and validated exactly like a manual case.
 */
public class DraftsForm {

    public static final int MAX_DRAFTS = 10;

    private Long suiteId;
    private List<DraftInput> drafts = new ArrayList<>();

    public List<DraftInput> selectedDrafts() {
        return drafts.stream().filter(d -> d != null && d.isSelected()).toList();
    }

    public Long getSuiteId() {
        return suiteId;
    }

    public void setSuiteId(Long suiteId) {
        this.suiteId = suiteId;
    }

    public List<DraftInput> getDrafts() {
        return drafts;
    }

    public void setDrafts(List<DraftInput> drafts) {
        this.drafts = drafts;
    }
}
