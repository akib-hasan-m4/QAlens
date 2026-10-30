package com.qalens.service.testcase;

import com.qalens.common.ResourceNotFoundException;
import com.qalens.domain.TestSuite;
import com.qalens.repository.TestSuiteRepository;
import com.qalens.security.ProjectAccess;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Suites live in a project: authorize against the project first, then resolve every id inside that project. */
@Service
public class SuiteService {

    private final TestSuiteRepository suites;
    private final ProjectAccess access;

    public SuiteService(TestSuiteRepository suites, ProjectAccess access) {
        this.suites = suites;
        this.access = access;
    }

    /** Presentation hint for showing edit controls; the write methods enforce the same rule. */
    @Transactional(readOnly = true)
    public boolean mayEdit(Long projectId) {
        return access.hasRole(projectId, "ADMIN", "QA_LEAD", "TESTER");
    }

    /** All suites of the project in tree order (parents before children, siblings by position). */
    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER', 'DEVELOPER', 'VIEWER')")
    @Transactional(readOnly = true)
    public List<SuiteView> tree(Long projectId) {
        List<TestSuite> all = suites.findByProjectIdOrderByPositionAscIdAsc(projectId);
        return flatten(all);
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER', 'DEVELOPER', 'VIEWER')")
    @Transactional(readOnly = true)
    public SuiteView view(Long projectId, Long suiteId) {
        TestSuite s = scoped(projectId, suiteId);
        return new SuiteView(s.getId(), s.getParentId(), s.getName(), 0);
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER')")
    @Transactional
    public SuiteView create(Long projectId, SuiteForm form) {
        Long parentId = form.parentId();
        if (parentId != null) {
            scoped(projectId, parentId);
        }
        int position = suites.countByProjectIdAndParentId(projectId, parentId);
        TestSuite saved = suites.save(new TestSuite(projectId, parentId, form.name(), position));
        return new SuiteView(saved.getId(), saved.getParentId(), saved.getName(), 0);
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER')")
    @Transactional
    public void rename(Long projectId, Long suiteId, SuiteForm form) {
        scoped(projectId, suiteId).rename(form.name());
    }

    private TestSuite scoped(Long projectId, Long suiteId) {
        return suites.findByIdAndProjectId(suiteId, projectId).orElseThrow(ResourceNotFoundException::new);
    }

    /** Depth-first walk; input is already ordered by position then id. */
    static List<SuiteView> flatten(List<TestSuite> all) {
        Map<Long, List<TestSuite>> children = all.stream()
                .filter(s -> s.getParentId() != null)
                .collect(Collectors.groupingBy(TestSuite::getParentId));
        List<SuiteView> out = new ArrayList<>();
        all.stream().filter(s -> s.getParentId() == null)
                .sorted(Comparator.comparingInt(TestSuite::getPosition).thenComparing(TestSuite::getId))
                .forEach(root -> walk(root, 0, children, out));
        return out;
    }

    private static void walk(TestSuite suite, int depth, Map<Long, List<TestSuite>> children, List<SuiteView> out) {
        out.add(new SuiteView(suite.getId(), suite.getParentId(), suite.getName(), depth));
        children.getOrDefault(suite.getId(), List.of()).forEach(c -> walk(c, depth + 1, children, out));
    }
}
