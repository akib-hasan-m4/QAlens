package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qalens.common.ResourceNotFoundException;
import com.qalens.domain.Requirement;
import com.qalens.domain.RequirementPriority;
import com.qalens.domain.RequirementStatus;
import com.qalens.repository.RequirementRepository;
import com.qalens.service.ProjectAccess;
import com.qalens.service.RequirementForm;
import com.qalens.service.RequirementService;
import com.qalens.service.RequirementView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;

class RequirementServiceTest {

    private final RequirementRepository requirements = mock(RequirementRepository.class);
    private final ProjectAccess access = mock(ProjectAccess.class);
    private final RequirementService service = new RequirementService(requirements, access);

    @BeforeEach
    void signIn() {
        ServiceTestSupport.signIn(7, 1);
    }

    @AfterEach
    void signOut() {
        ServiceTestSupport.signOut();
    }

    private static RequirementForm form(String title) {
        return new RequirementForm("K-1", title, "body", RequirementStatus.DRAFT, RequirementPriority.HIGH);
    }

    @Test
    void listWithoutAQueryUsesTheDefaultSortNewestFirst() {
        when(requirements.findByProjectId(eq(1L), any(Sort.class))).thenReturn(List.of());
        service.list(1L, null, "no-such-sort");
        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(requirements).findByProjectId(eq(1L), sort.capture());
        assertThat(sort.getValue().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void listWithAQueryEscapesWildcardsAndHonoursAKnownSort() {
        when(requirements.search(eq(1L), any(), any(Sort.class))).thenReturn(List.of());
        service.list(1L, " 100%_ ", "title");
        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
        verify(requirements).search(eq(1L), pattern.capture(), sort.capture());
        assertThat(pattern.getValue()).isEqualTo("%100\\%\\_%");
        assertThat(sort.getValue().getOrderFor("title")).isNotNull();
    }

    @Test
    void createRecordsTheCallerAsAuthor() {
        when(requirements.save(any(Requirement.class))).thenAnswer(inv -> inv.getArgument(0));
        RequirementView view = service.create(1L, form("Reset password"));
        assertThat(view.title()).isEqualTo("Reset password");
        assertThat(view.projectId()).isEqualTo(1L);
        ArgumentCaptor<Requirement> saved = ArgumentCaptor.forClass(Requirement.class);
        verify(requirements).save(saved.capture());
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(7L);
    }

    @Test
    void updateEditsTheScopedRequirement() {
        Requirement r = new Requirement(1L, 7L, null, "Old", null, RequirementStatus.DRAFT, RequirementPriority.LOW);
        when(requirements.findByIdAndProjectId(4L, 1L)).thenReturn(Optional.of(r));
        service.update(1L, 4L, form("New"));
        assertThat(r.getTitle()).isEqualTo("New");
        assertThat(r.getPriority()).isEqualTo(RequirementPriority.HIGH);
        assertThat(service.view(1L, 4L).title()).isEqualTo("New");
    }

    @Test
    void aRequirementOfAnotherProjectIsNotFound() {
        when(requirements.findByIdAndProjectId(4L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.view(1L, 4L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.update(1L, 4L, form("x"))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void mayEditAsksTheAccessBean() {
        when(access.hasRole(1L, "ADMIN", "QA_LEAD", "TESTER")).thenReturn(true);
        assertThat(service.mayEdit(1L)).isTrue();
    }
}
