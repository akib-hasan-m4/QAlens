package com.qalens.service;

import com.qalens.ai.ImpactMatcher;
import com.qalens.ai.ImpactResult;
import com.qalens.common.ResourceNotFoundException;
import com.qalens.domain.CasePriority;
import com.qalens.domain.Requirement;
import com.qalens.repository.ImpactCandidateRow;
import com.qalens.repository.LinkedRequirementRow;
import com.qalens.repository.RequirementLinkRepository;
import com.qalens.repository.RequirementRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suggests the existing cases a new or changed requirement probably affects, so they can be re-run or linked.
 * Ranked by meaning (embedding similarity) weighted by the case's priority; every row says why it is there.
 */
@Service
public class ImpactService {

    private final ImpactMatcher matcher;
    private final RequirementRepository requirements;
    private final RequirementLinkRepository links;
    private final ProjectAccess access;
    private final double runFirst;

    public ImpactService(ImpactMatcher matcher, RequirementRepository requirements, RequirementLinkRepository links,
                         ProjectAccess access, @Value("${qalens.impact.run-first:0.5}") double runFirst) {
        this.matcher = matcher;
        this.requirements = requirements;
        this.links = links;
        this.access = access;
        this.runFirst = runFirst;
    }

    /** Presentation hint for showing the impact card; {@link #suggest} enforces the same rule. */
    @Transactional(readOnly = true)
    public boolean mayUse(Long projectId) {
        return access.hasRole(projectId, "ADMIN", "QA_LEAD", "TESTER", "DEVELOPER");
    }

    /** Not transactional: embedding calls go to the model and must not hold a database connection. */
    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER', 'DEVELOPER')")
    public ImpactSuggestions suggest(Long projectId, Long requirementId) {
        Requirement r = requirements.findByIdAndProjectId(requirementId, projectId)
                .orElseThrow(ResourceNotFoundException::new);
        ImpactResult result = matcher.match(projectId, requirementId, r.getTitle(), r.getBody());
        if (!result.ok()) {
            return new ImpactSuggestions(false, result.message(), List.of(), 0);
        }
        List<ImpactCandidateRow> candidates = result.candidates();
        Map<Long, List<String>> covers = candidates.isEmpty() ? Map.of()
                : links.requirementsOfCases(candidates.stream().map(ImpactCandidateRow::getCaseId).toList(), projectId)
                        .stream().collect(Collectors.groupingBy(LinkedRequirementRow::getCaseId,
                                Collectors.mapping(ImpactService::label, Collectors.toList())));
        List<ImpactRow> rows = candidates.stream()
                .map(c -> toRow(c, covers.getOrDefault(c.getCaseId(), List.of())))
                .sorted(Comparator.comparingDouble(ImpactRow::score).reversed()
                        .thenComparing(Comparator.comparingInt(ImpactRow::similarity).reversed()))
                .toList();
        return new ImpactSuggestions(true, null, rows, result.embedded());
    }

    private ImpactRow toRow(ImpactCandidateRow c, List<String> covers) {
        CasePriority priority = CasePriority.valueOf(c.getPriority());
        double score = rank(c.getSimilarity(), priority);
        return new ImpactRow(c.getCaseId(), c.getTitle(), c.getVersionNo(), c.getSuiteName(), priority,
                (int) Math.round(c.getSimilarity() * 100), score, score >= runFirst, covers);
    }

    /** Similarity dominates; priority only separates cases that are about equally close. */
    public static double rank(double similarity, CasePriority priority) {
        double weight = switch (priority) {
            case HIGH -> 1.0;
            case MEDIUM -> 0.9;
            case LOW -> 0.8;
        };
        return Math.round(similarity * weight * 100) / 100.0;
    }

    private static String label(LinkedRequirementRow r) {
        return r.getExtKey() == null || r.getExtKey().isBlank() ? r.getTitle() : r.getExtKey() + " " + r.getTitle();
    }
}
