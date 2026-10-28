package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.qalens.ai.CaseDraft;
import com.qalens.ai.CaseGenerator;
import com.qalens.ai.GenerationResult;
import com.qalens.common.ResourceNotFoundException;
import com.qalens.domain.CasePriority;
import com.qalens.domain.Requirement;
import com.qalens.domain.RequirementPriority;
import com.qalens.domain.RequirementStatus;
import com.qalens.domain.TestStep;
import com.qalens.domain.TestSuite;
import com.qalens.repository.RequirementRepository;
import com.qalens.repository.TestSuiteRepository;
import com.qalens.service.AiDraftService;
import com.qalens.service.CaseForm;
import com.qalens.service.GeneratedDrafts;
import com.qalens.service.TestCaseService;
import com.qalens.service.TraceLinkService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiDraftServiceTest {

    private final CaseGenerator generator = mock(CaseGenerator.class);
    private final RequirementRepository requirements = mock(RequirementRepository.class);
    private final TestSuiteRepository suites = mock(TestSuiteRepository.class);
    private final TestCaseService testCases = mock(TestCaseService.class);
    private final TraceLinkService links = mock(TraceLinkService.class);
    private final AiDraftService service = new AiDraftService(generator, requirements, suites, testCases, links);

    private static final Requirement REQUIREMENT = new Requirement(1L, 7L, "SHOP-2", "Pay by card", "Cards only",
            RequirementStatus.APPROVED, RequirementPriority.HIGH);

    private static CaseForm form(String title) {
        return CaseForm.of(title, null, CasePriority.MEDIUM, List.of(new TestStep("a", "b")));
    }

    @Test
    void generateSendsTheRequirementAndReturnsSelectedDrafts() {
        when(requirements.findByIdAndProjectId(3L, 1L)).thenReturn(Optional.of(REQUIREMENT));
        when(generator.generate(1L, "SHOP-2", "Pay by card", "Cards only")).thenReturn(new GenerationResult(
                GenerationResult.Status.OK, List.of(new CaseDraft("Valid card", "Cart has items", CasePriority.HIGH,
                        List.of(new TestStep("Pay", "Accepted")))), 2, null));

        GeneratedDrafts result = service.generate(1L, 3L);

        assertThat(result.ok()).isTrue();
        assertThat(result.dropped()).isEqualTo(2);
        assertThat(result.form().getDrafts()).singleElement().satisfies(d -> {
            assertThat(d.isSelected()).isTrue();
            assertThat(d.getTitle()).isEqualTo("Valid card");
            assertThat(d.getPriority()).isEqualTo(CasePriority.HIGH);
            assertThat(d.parsedSteps()).containsExactly(new TestStep("Pay", "Accepted"));
            assertThat(d.isStepsValid()).isTrue();
        });
    }

    @Test
    void generateDegradesWithTheGeneratorsMessage() {
        when(requirements.findByIdAndProjectId(3L, 1L)).thenReturn(Optional.of(REQUIREMENT));
        when(generator.generate(any(), any(), any(), any())).thenReturn(new GenerationResult(
                GenerationResult.Status.UNAVAILABLE, List.of(), 0, "Model offline"));

        GeneratedDrafts result = service.generate(1L, 3L);

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).isEqualTo("Model offline");
        assertThat(result.form().getDrafts()).isEmpty();
    }

    @Test
    void aRequirementOutsideTheProjectIsNotFoundAndTheModelIsNotCalled() {
        when(requirements.findByIdAndProjectId(3L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(1L, 3L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(generator);
    }

    @Test
    void acceptCreatesAiCasesAndLinksEachOne() {
        when(requirements.findByIdAndProjectId(3L, 1L)).thenReturn(Optional.of(REQUIREMENT));
        when(suites.findByIdAndProjectId(9L, 1L)).thenReturn(Optional.of(new TestSuite(1L, null, "S", 0)));
        CaseForm first = form("One");
        CaseForm second = form("Two");
        when(testCases.createGenerated(1L, 9L, first)).thenReturn(20L);
        when(testCases.createGenerated(1L, 9L, second)).thenReturn(21L);

        assertThat(service.accept(1L, 3L, 9L, List.of(first, second))).isEqualTo(2);

        verify(links).link(1L, 20L, 3L);
        verify(links).link(1L, 21L, 3L);
        verify(testCases, never()).create(anyLong(), anyLong(), any());
    }

    @Test
    void acceptChecksBothTheRequirementAndTheSuite() {
        when(requirements.findByIdAndProjectId(3L, 1L)).thenReturn(Optional.of(REQUIREMENT));
        when(suites.findByIdAndProjectId(9L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.accept(1L, 3L, 9L, List.of(form("x"))))
                .isInstanceOf(ResourceNotFoundException.class);

        when(requirements.findByIdAndProjectId(4L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.accept(1L, 4L, 9L, List.of(form("x"))))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(testCases);
    }

    @Test
    void acceptRefusesAnEmptyOrOversizedBatch() {
        when(requirements.findByIdAndProjectId(3L, 1L)).thenReturn(Optional.of(REQUIREMENT));
        when(suites.findByIdAndProjectId(9L, 1L)).thenReturn(Optional.of(new TestSuite(1L, null, "S", 0)));

        assertThatThrownBy(() -> service.accept(1L, 3L, 9L, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.accept(1L, 3L, 9L, Collections.nCopies(11, form("x"))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(testCases, never()).createGenerated(anyLong(), eq(9L), any());
    }
}
