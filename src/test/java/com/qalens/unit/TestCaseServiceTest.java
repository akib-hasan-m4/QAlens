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
import com.qalens.domain.TestCase;
import com.qalens.domain.TestCaseVersion;
import com.qalens.domain.TestStep;
import com.qalens.domain.TestSuite;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.TestCaseRepository;
import com.qalens.repository.TestCaseVersionRepository;
import com.qalens.repository.TestSuiteRepository;
import com.qalens.service.CaseForm;
import com.qalens.service.ProjectAccess;
import com.qalens.service.QalensUserDetails;
import com.qalens.service.TestCaseService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** The versioning rule without a database: a save appends N+1 and never touches an earlier row. */
class TestCaseServiceTest {

    private final TestCaseRepository cases = mock(TestCaseRepository.class);
    private final TestCaseVersionRepository versions = mock(TestCaseVersionRepository.class);
    private final TestSuiteRepository suites = mock(TestSuiteRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final TestCaseService service =
            new TestCaseService(cases, versions, suites, users, mock(ProjectAccess.class));

    @BeforeEach
    void signIn() {
        var user = new QalensUserDetails(7L, "t@acme.test", "Tess", 1L, "acme", "Acme", "x", true);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        when(versions.save(any(TestCaseVersion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static CaseForm form(String title) {
        return new CaseForm(title, "pre", CasePriority.HIGH, "Do it | It works");
    }

    @Test
    void creatingACaseWritesVersionOneAsManual() {
        when(suites.findByIdAndProjectId(5L, 1L)).thenReturn(Optional.of(new TestSuite(1L, null, "S", 0)));
        when(cases.save(any(TestCase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(versions.latestVersionNo(any())).thenReturn(0);

        service.create(1L, 5L, form("First"));

        ArgumentCaptor<TestCaseVersion> saved = ArgumentCaptor.forClass(TestCaseVersion.class);
        verify(versions).save(saved.capture());
        assertThat(saved.getValue().getVersionNo()).isEqualTo(1);
        assertThat(saved.getValue().getOrigin()).isEqualTo(CaseOrigin.MANUAL);
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(7L);
        assertThat(saved.getValue().getSteps()).containsExactly(new TestStep("Do it", "It works"));
    }

    @Test
    void savingAgainAppendsTheNextVersion() {
        TestCase tc = new TestCase(5L, 7L);
        when(cases.findInProject(9L, 1L)).thenReturn(Optional.of(tc));
        when(versions.latestVersionNo(any())).thenReturn(2);

        int no = service.saveNewVersion(1L, 9L, form("Third"));

        assertThat(no).isEqualTo(3);
        ArgumentCaptor<TestCaseVersion> saved = ArgumentCaptor.forClass(TestCaseVersion.class);
        verify(versions).save(saved.capture());
        assertThat(saved.getValue().getVersionNo()).isEqualTo(3);
        assertThat(saved.getValue().getTitle()).isEqualTo("Third");
    }

    @Test
    void aCaseOutsideTheProjectIsNotFoundAndNothingIsWritten() {
        when(cases.findInProject(9L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveNewVersion(1L, 9L, form("x")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(versions, never()).save(any());
    }

    @Test
    void aSuiteOutsideTheProjectIsNotFoundForCreate() {
        when(suites.findByIdAndProjectId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(1L, 5L, form("x"))).isInstanceOf(ResourceNotFoundException.class);
        verify(cases, never()).save(any());
    }

    @Test
    void anUnknownVersionNumberIsNotFound() {
        TestCase tc = new TestCase(5L, 7L);
        when(cases.findInProject(9L, 1L)).thenReturn(Optional.of(tc));
        when(versions.findByCaseIdOrderByVersionNoDesc(any())).thenReturn(List.of(
                new TestCaseVersion(9L, 1, "t", null, List.of(new TestStep("a", "b")), CasePriority.LOW,
                        CaseOrigin.MANUAL, 7L)));
        when(users.findDisplayNameById(7L)).thenReturn(Optional.of("Tess"));

        assertThatThrownBy(() -> service.view(1L, 9L, 4)).isInstanceOf(ResourceNotFoundException.class);
    }
}
