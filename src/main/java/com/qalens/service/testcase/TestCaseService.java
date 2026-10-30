package com.qalens.service.testcase;

import com.qalens.common.ResourceNotFoundException;
import com.qalens.domain.CaseOrigin;
import com.qalens.domain.TestCase;
import com.qalens.domain.TestCaseVersion;
import com.qalens.domain.TestSuite;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.TestCaseRepository;
import com.qalens.repository.TestCaseVersionRepository;
import com.qalens.repository.TestSuiteRepository;
import com.qalens.security.ProjectAccess;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cases are versioned: creating a case writes version 1, and every later save writes version N+1 and moves the
 * case's current pointer. Existing version rows are never changed.
 */
@Service
public class TestCaseService {

    private final TestCaseRepository cases;
    private final TestCaseVersionRepository versions;
    private final TestSuiteRepository suites;
    private final AppUserRepository users;
    private final ProjectAccess access;

    public TestCaseService(TestCaseRepository cases, TestCaseVersionRepository versions, TestSuiteRepository suites,
                           AppUserRepository users, ProjectAccess access) {
        this.cases = cases;
        this.versions = versions;
        this.suites = suites;
        this.users = users;
        this.access = access;
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER', 'DEVELOPER', 'VIEWER')")
    @Transactional(readOnly = true)
    public List<CaseSummary> listInSuite(Long projectId, Long suiteId) {
        suite(projectId, suiteId);
        List<TestCase> found = cases.findBySuiteIdOrderByIdAsc(suiteId);
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, TestCaseVersion> current = versions
                .currentVersionsOf(found.stream().map(TestCase::getId).toList()).stream()
                .collect(Collectors.toMap(TestCaseVersion::getCaseId, Function.identity()));
        return found.stream().map(c -> {
            TestCaseVersion v = current.get(c.getId());
            return new CaseSummary(c.getId(), v.getTitle(), v.getPriority(), v.getVersionNo());
        }).toList();
    }

    /** {@code versionNo} null shows the current version; an unknown number is a 404. */
    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER', 'DEVELOPER', 'VIEWER')")
    @Transactional(readOnly = true)
    public CaseView view(Long projectId, Long caseId, Integer versionNo) {
        TestCase tc = scoped(projectId, caseId);
        List<CaseVersionView> history = versions.findByCaseIdOrderByVersionNoDesc(tc.getId()).stream()
                .map(this::toView).toList();
        CaseVersionView current = history.get(0);
        CaseVersionView shown = versionNo == null ? current : history.stream()
                .filter(v -> v.versionNo() == versionNo).findFirst().orElseThrow(ResourceNotFoundException::new);
        String suiteName = suites.findByIdAndProjectId(tc.getSuiteId(), projectId)
                .map(TestSuite::getName).orElseThrow(ResourceNotFoundException::new);
        return new CaseView(tc.getId(), tc.getSuiteId(), suiteName, shown, current.versionNo(), history);
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER')")
    @Transactional
    public Long create(Long projectId, Long suiteId, CaseForm form) {
        return createAs(projectId, suiteId, form, CaseOrigin.MANUAL);
    }

    /** Saves an accepted AI draft (possibly edited by the user) as version 1 with origin AI_GENERATED. */
    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER')")
    @Transactional
    public Long createGenerated(Long projectId, Long suiteId, CaseForm form) {
        return createAs(projectId, suiteId, form, CaseOrigin.AI_GENERATED);
    }

    /** Saves the form as the next version. Returns the new version number. */
    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER')")
    @Transactional
    public int saveNewVersion(Long projectId, Long caseId, CaseForm form) {
        TestCase tc = scoped(projectId, caseId);
        return appendVersion(tc, form, CaseOrigin.MANUAL, ProjectAccess.currentUser().getId());
    }

    /** Presentation hint for showing edit controls; the write methods enforce the same rule. */
    @Transactional(readOnly = true)
    public boolean mayEdit(Long projectId) {
        return access.hasRole(projectId, "ADMIN", "QA_LEAD", "TESTER");
    }

    private Long createAs(Long projectId, Long suiteId, CaseForm form, CaseOrigin origin) {
        suite(projectId, suiteId);
        Long userId = ProjectAccess.currentUser().getId();
        TestCase tc = cases.save(new TestCase(suiteId, userId));
        appendVersion(tc, form, origin, userId);
        return tc.getId();
    }

    private int appendVersion(TestCase tc, CaseForm form, CaseOrigin origin, Long userId) {
        int next = versions.latestVersionNo(tc.getId()) + 1;
        TestCaseVersion saved = versions.save(new TestCaseVersion(tc.getId(), next, form.getTitle(),
                form.getPrecondition(), form.parsedSteps(), form.getPriority(), origin, userId));
        tc.pointTo(saved);
        return next;
    }

    private TestSuite suite(Long projectId, Long suiteId) {
        return suites.findByIdAndProjectId(suiteId, projectId).orElseThrow(ResourceNotFoundException::new);
    }

    private TestCase scoped(Long projectId, Long caseId) {
        return cases.findInProject(caseId, projectId).orElseThrow(ResourceNotFoundException::new);
    }

    private CaseVersionView toView(TestCaseVersion v) {
        String author = users.findDisplayNameById(v.getCreatedBy()).orElse("unknown");
        return new CaseVersionView(v.getVersionNo(), v.getTitle(), v.getPrecondition(), v.getSteps(),
                v.getPriority(), v.getOrigin(), author, v.getCreatedAt());
    }
}
