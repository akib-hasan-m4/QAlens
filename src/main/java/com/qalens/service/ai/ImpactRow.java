package com.qalens.service.ai;

import com.qalens.domain.CasePriority;
import java.util.List;

/**
 * One existing case that a requirement probably affects. {@code similarity} is the cosine similarity in percent,
 * {@code score} the similarity weighted by the case priority, and {@code covers} the requirements the case already
 * verifies, shown as the reason it matters.
 */
public record ImpactRow(Long caseId, String title, int versionNo, String suiteName, CasePriority priority,
                        int similarity, double score, boolean runFirst, List<String> covers) {
}
