package com.qalens.service.requirement;

import com.qalens.domain.RequirementPriority;
import com.qalens.domain.RequirementStatus;
import java.time.OffsetDateTime;

public record RequirementView(Long id, Long projectId, String extKey, String title, String body,
                              RequirementStatus status, RequirementPriority priority, OffsetDateTime createdAt) {
}
