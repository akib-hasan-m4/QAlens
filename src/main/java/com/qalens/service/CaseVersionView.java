package com.qalens.service;

import com.qalens.domain.CaseOrigin;
import com.qalens.domain.CasePriority;
import com.qalens.domain.TestStep;
import java.time.OffsetDateTime;
import java.util.List;

public record CaseVersionView(int versionNo, String title, String precondition, List<TestStep> steps,
                              CasePriority priority, CaseOrigin origin, String author, OffsetDateTime createdAt) {
}
