package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qalens.common.ResourceNotFoundException;
import com.qalens.domain.TestSuite;
import com.qalens.repository.TestSuiteRepository;
import com.qalens.service.ProjectAccess;
import com.qalens.service.SuiteForm;
import com.qalens.service.SuiteService;
import com.qalens.service.SuiteView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SuiteServiceTest {

    private final TestSuiteRepository suites = mock(TestSuiteRepository.class);
    private final SuiteService service = new SuiteService(suites, mock(ProjectAccess.class));

    private static TestSuite suite(long id, Long parent, String name, int position) {
        TestSuite s = new TestSuite(1L, parent, name, position);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    @Test
    void treeListsParentsBeforeChildrenWithDepth() {
        when(suites.findByProjectIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of(
                suite(1, null, "Checkout", 0), suite(3, null, "Search", 1),
                suite(2, 1L, "Payments", 0), suite(4, 2L, "Cards", 0)));

        List<SuiteView> tree = service.tree(1L);

        assertThat(tree).extracting(SuiteView::name).containsExactly("Checkout", "Payments", "Cards", "Search");
        assertThat(tree).extracting(SuiteView::depth).containsExactly(0, 1, 2, 0);
    }

    @Test
    void createAppendsAfterItsSiblings() {
        when(suites.countByProjectIdAndParentId(1L, null)).thenReturn(2);
        when(suites.save(any(TestSuite.class))).thenAnswer(inv -> inv.getArgument(0));

        SuiteView created = service.create(1L, new SuiteForm("New", null));

        assertThat(created.name()).isEqualTo("New");
        verify(suites).save(any(TestSuite.class));
    }

    @Test
    void aParentOutsideTheProjectIsNotFound() {
        when(suites.findByIdAndProjectId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, new SuiteForm("Child", 99L)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(suites, never()).save(any());
    }

    @Test
    void renameChangesTheName() {
        TestSuite s = suite(1, null, "Old", 0);
        when(suites.findByIdAndProjectId(1L, 1L)).thenReturn(Optional.of(s));

        service.rename(1L, 1L, new SuiteForm("Renamed", null));

        assertThat(s.getName()).isEqualTo("Renamed");
    }

    @Test
    void viewOfAForeignSuiteIsNotFound() {
        when(suites.findByIdAndProjectId(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.view(1L, 1L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
