package com.qalens.service.testcase;

import java.util.List;

/** {@code shown} is the version being displayed (the current one unless a past one was requested). */
public record CaseView(Long id, Long suiteId, String suiteName, CaseVersionView shown, int currentVersionNo,
                       List<CaseVersionView> history) {

    public boolean showingCurrent() {
        return shown.versionNo() == currentVersionNo;
    }
}
