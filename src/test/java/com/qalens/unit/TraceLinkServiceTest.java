package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qalens.common.ResourceNotFoundException;
import com.qalens.domain.CaseOrigin;
import com.qalens.domain.CasePriority;
import com.qalens.domain.Requirement;
import com.qalens.domain.RequirementLink;
import com.qalens.domain.RequirementPriority;
import com.qalens.domain.RequirementStatus;
import com.qalens.domain.TestCase;
import com.qalens.domain.TestCaseVersion;
import com.qalens.domain.TestStep;
import com.qalens.repository.RequirementLinkRepository;
import com.qalens.repository.RequirementRepository;
import com.qalens.repository.TestCaseRepository;
import com.qalens.repository.TestCaseVersionRepository;
import com.qalens.service.RequirementView;
import com.qalens.service.TraceLinkService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

class TraceLinkServiceTest {

    private final RequirementLinkRepository links = mock(RequirementLinkRepository.class);
    private final RequirementRepository requirements = mock(RequirementRepository.class);
    private final TestCaseRepository cases = mock(TestCaseRepository.class);
    private final TestCaseVersionRepository versions = mock(TestCaseVersionRepository.class);
    private final TraceLinkService service = new TraceLinkService(links, requirements, cases, versions);

    private static Requirement requirement(long id, String title) {
        Requirement r = new Requirement(1L, 7L, null, title, null, RequirementStatus.DRAFT, RequirementPriority.LOW);
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    private static TestCase testCase(long id) {
        TestCase c = new TestCase(5L, 7L);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    @Test
    void linkWritesAPairWhenBothEndsAreInTheProject() {
        when(cases.findInProject(9L, 1L)).thenReturn(Optional.of(testCase(9L)));
        when(requirements.findByIdAndProjectId(3L, 1L)).thenReturn(Optional.of(requirement(3L, "R")));

        service.link(1L, 9L, 3L);

        verify(links).save(any(RequirementLink.class));
    }

    @Test
    void linkingTwiceWritesNothingTheSecondTime() {
        when(cases.findInProject(9L, 1L)).thenReturn(Optional.of(testCase(9L)));
        when(requirements.findByIdAndProjectId(3L, 1L)).thenReturn(Optional.of(requirement(3L, "R")));
        when(links.existsByRequirementIdAndTestCaseId(3L, 9L)).thenReturn(true);

        service.link(1L, 9L, 3L);

        verify(links, never()).save(any());
    }

    @Test
    void aRequirementFromAnotherProjectCannotBeLinked() {
        when(cases.findInProject(9L, 1L)).thenReturn(Optional.of(testCase(9L)));
        when(requirements.findByIdAndProjectId(3L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link(1L, 9L, 3L)).isInstanceOf(ResourceNotFoundException.class);
        verify(links, never()).save(any());
    }

    @Test
    void aCaseFromAnotherProjectCannotBeLinkedOrUnlinked() {
        when(cases.findInProject(9L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link(1L, 9L, 3L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.unlink(1L, 9L, 3L)).isInstanceOf(ResourceNotFoundException.class);
        verify(links, never()).unlink(any(), any());
    }

    @Test
    void unlinkRemovesThePair() {
        when(cases.findInProject(9L, 1L)).thenReturn(Optional.of(testCase(9L)));
        when(requirements.findByIdAndProjectId(3L, 1L)).thenReturn(Optional.of(requirement(3L, "R")));

        service.unlink(1L, 9L, 3L);

        verify(links).unlink(3L, 9L);
    }

    @Test
    void linkableRequirementsExcludeTheOnesAlreadyLinked() {
        when(cases.findInProject(9L, 1L)).thenReturn(Optional.of(testCase(9L)));
        when(links.requirementsOfCase(9L, 1L)).thenReturn(List.of(requirement(3L, "Linked")));
        when(requirements.findByProjectId(1L, Sort.by("id")))
                .thenReturn(List.of(requirement(3L, "Linked"), requirement(4L, "Free")));

        assertThat(service.linkableRequirements(1L, 9L)).extracting(RequirementView::title).containsExactly("Free");
    }

    @Test
    void casesOfARequirementShowTheirCurrentVersionTitle() {
        when(requirements.findByIdAndProjectId(3L, 1L)).thenReturn(Optional.of(requirement(3L, "R")));
        when(links.casesOfRequirement(3L, 1L)).thenReturn(List.of(testCase(9L)));
        when(versions.currentVersionsOf(List.of(9L))).thenReturn(List.of(new TestCaseVersion(9L, 2, "Current title",
                null, List.of(new TestStep("a", "b")), CasePriority.HIGH, CaseOrigin.MANUAL, 7L)));

        var found = service.casesOfRequirement(1L, 3L);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).title()).isEqualTo("Current title");
        assertThat(found.get(0).versionNo()).isEqualTo(2);
    }

    @Test
    void noLinkedCasesGivesAnEmptyList() {
        when(requirements.findByIdAndProjectId(3L, 1L)).thenReturn(Optional.of(requirement(3L, "R")));
        when(links.casesOfRequirement(3L, 1L)).thenReturn(List.of());

        assertThat(service.casesOfRequirement(1L, 3L)).isEmpty();
    }
}
