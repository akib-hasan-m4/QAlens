package com.qalens.service.testcase;

import com.qalens.domain.CasePriority;

/** Row in a suite's case list: the case id plus the fields of its current version. */
public record CaseSummary(Long id, String title, CasePriority priority, int versionNo) {
}
