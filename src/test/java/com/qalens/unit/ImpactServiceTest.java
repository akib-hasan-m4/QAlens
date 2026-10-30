package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.qalens.ai.ImpactMatcher;
import com.qalens.ai.ImpactResult;
import com.qalens.common.ResourceNotFoundException;
import com.qalens.domain.CasePriority;
import com.qalens.domain.Requirement;
import com.qalens.domain.RequirementPriority;
import com.qalens.domain.RequirementStatus;
import com.qalens.repository.ImpactCandidateRow;
import com.qalens.repository.LinkedRequirementRow;
import com.qalens.repository.RequirementLinkRepository;
import com.qalens.repository.RequirementRepository;
import com.qalens.service.ai.ImpactRow;
import com.qalens.service.ai.ImpactService;
import com.qalens.service.ai.ImpactSuggestions;
import com.qalens.security.ProjectAccess;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ImpactServiceTest {

    private final ImpactMatcher matcher = mock(ImpactMatcher.class);
    private final RequirementRepository requirements = mock(RequirementRepository.class);
    private final RequirementLinkRepository links = mock(RequirementLinkRepository.class);
    private final ImpactService service = new ImpactService(matcher, requirements, links, mock(ProjectAccess.class),
            0.6);

    private static final Requirement REQUIREMENT = new Requirement(1L, 7L, "SHOP-7", "Refund", "Back to the card",
            RequirementStatus.APPROVED, RequirementPriority.HIGH);

    private static ImpactCandidateRow candidate(long caseId, String priority, double similarity) {
        ImpactCandidateRow row = mock(ImpactCandidateRow.class);
        when(row.getCaseId()).thenReturn(caseId);
        when(row.getTitle()).thenReturn("Case " + caseId);
        when(row.getVersionNo()).thenReturn(1);
        when(row.getSuiteName()).thenReturn("Payment");
        when(row.getPriority()).thenReturn(priority);
        when(row.getSimilarity()).thenReturn(similarity);
        return row;
    }

    private static LinkedRequirementRow linked(long caseId, String key, String title) {
        LinkedRequirementRow row = mock(LinkedRequirementRow.class);
        when(row.getCaseId()).thenReturn(caseId);
        when(row.getExtKey()).thenReturn(key);
        when(row.getTitle()).thenReturn(title);
        return row;
    }

    @Test
    void priorityWeightsTheSimilarity() {
        assertThat(ImpactService.rank(0.70, CasePriority.HIGH)).isEqualTo(0.70);
        assertThat(ImpactService.rank(0.70, CasePriority.MEDIUM)).isEqualTo(0.63);
        assertThat(ImpactService.rank(0.70, CasePriority.LOW)).isEqualTo(0.56);
    }

    @Test
    void rowsAreRankedLabelledAndExplained() {
        when(requirements.findByIdAndProjectId(9L, 1L)).thenReturn(Optional.of(REQUIREMENT));
        List<ImpactCandidateRow> found =
                List.of(candidate(11, "LOW", 0.72), candidate(12, "HIGH", 0.66), candidate(13, "MEDIUM", 0.40));
        List<LinkedRequirementRow> covered = List.of(
                linked(12, "SHOP-2", "Pay by card"), linked(12, null, "Receipts"), linked(13, " ", "Coupons"));
        when(matcher.match(1L, 9L, "Refund", "Back to the card")).thenReturn(new ImpactResult(true, null, found, 2));
        when(links.requirementsOfCases(List.of(11L, 12L, 13L), 1L)).thenReturn(covered);

        ImpactSuggestions result = service.suggest(1L, 9L);

        assertThat(result.ok()).isTrue();
        assertThat(result.embedded()).isEqualTo(2);
        assertThat(result.rows()).extracting(ImpactRow::caseId).containsExactly(12L, 11L, 13L);
        ImpactRow top = result.rows().get(0);
        assertThat(top.similarity()).isEqualTo(66);
        assertThat(top.runFirst()).isTrue();
        assertThat(top.covers()).containsExactly("SHOP-2 Pay by card", "Receipts");
        assertThat(result.rows().get(1).runFirst()).isFalse(); // 0.72 x 0.8 = 0.58
        assertThat(result.rows().get(2).covers()).containsExactly("Coupons");
    }

    @Test
    void noCandidatesSkipsTheLinkLookup() {
        when(requirements.findByIdAndProjectId(9L, 1L)).thenReturn(Optional.of(REQUIREMENT));
        when(matcher.match(any(), any(), any(), any())).thenReturn(new ImpactResult(true, null, List.of(), 0));

        assertThat(service.suggest(1L, 9L).rows()).isEmpty();
        verifyNoInteractions(links);
    }

    @Test
    void anUnavailableModelIsPassedOn() {
        when(requirements.findByIdAndProjectId(9L, 1L)).thenReturn(Optional.of(REQUIREMENT));
        when(matcher.match(any(), any(), any(), any())).thenReturn(new ImpactResult(false, "down", List.of(), 0));

        ImpactSuggestions result = service.suggest(1L, 9L);

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).isEqualTo("down");
    }

    @Test
    void anUnknownRequirementIsNotFound() {
        when(requirements.findByIdAndProjectId(9L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suggest(1L, 9L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(matcher);
    }
}
