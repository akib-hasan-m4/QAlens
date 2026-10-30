package com.qalens.service.requirement;

import com.qalens.common.ResourceNotFoundException;
import com.qalens.domain.Requirement;
import com.qalens.domain.RequirementLink;
import com.qalens.domain.TestCase;
import com.qalens.domain.TestCaseVersion;
import com.qalens.repository.RequirementLinkRepository;
import com.qalens.repository.RequirementRepository;
import com.qalens.repository.TestCaseRepository;
import com.qalens.repository.TestCaseVersionRepository;
import com.qalens.service.testcase.CaseSummary;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Requirement-to-case traceability. Both ends must belong to the project the caller is authorized on. */
@Service
public class TraceLinkService {

    private final RequirementLinkRepository links;
    private final RequirementRepository requirements;
    private final TestCaseRepository cases;
    private final TestCaseVersionRepository versions;

    public TraceLinkService(RequirementLinkRepository links, RequirementRepository requirements,
                            TestCaseRepository cases, TestCaseVersionRepository versions) {
        this.links = links;
        this.requirements = requirements;
        this.cases = cases;
        this.versions = versions;
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER', 'DEVELOPER', 'VIEWER')")
    @Transactional(readOnly = true)
    public List<RequirementView> requirementsOfCase(Long projectId, Long caseId) {
        cases.findInProject(caseId, projectId).orElseThrow(ResourceNotFoundException::new);
        return links.requirementsOfCase(caseId, projectId).stream().map(TraceLinkService::toView).toList();
    }

    /** Requirements of the project that this case is not yet linked to, for the "link" picker. */
    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER', 'DEVELOPER', 'VIEWER')")
    @Transactional(readOnly = true)
    public List<RequirementView> linkableRequirements(Long projectId, Long caseId) {
        Set<Long> linked = requirementsOfCase(projectId, caseId).stream()
                .map(RequirementView::id).collect(Collectors.toSet());
        return requirements.findByProjectId(projectId, Sort.by("id")).stream()
                .filter(r -> !linked.contains(r.getId())).map(TraceLinkService::toView).toList();
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER', 'DEVELOPER', 'VIEWER')")
    @Transactional(readOnly = true)
    public List<CaseSummary> casesOfRequirement(Long projectId, Long requirementId) {
        requirements.findByIdAndProjectId(requirementId, projectId).orElseThrow(ResourceNotFoundException::new);
        List<TestCase> found = links.casesOfRequirement(requirementId, projectId);
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

    /** Idempotent: linking an existing pair is a no-op. */
    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER')")
    @Transactional
    public void link(Long projectId, Long caseId, Long requirementId) {
        checkBothEnds(projectId, caseId, requirementId);
        if (!links.existsByRequirementIdAndTestCaseId(requirementId, caseId)) {
            links.save(new RequirementLink(requirementId, caseId));
        }
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER')")
    @Transactional
    public void unlink(Long projectId, Long caseId, Long requirementId) {
        checkBothEnds(projectId, caseId, requirementId);
        links.unlink(requirementId, caseId);
    }

    private void checkBothEnds(Long projectId, Long caseId, Long requirementId) {
        cases.findInProject(caseId, projectId).orElseThrow(ResourceNotFoundException::new);
        requirements.findByIdAndProjectId(requirementId, projectId).orElseThrow(ResourceNotFoundException::new);
    }

    private static RequirementView toView(Requirement r) {
        return new RequirementView(r.getId(), r.getProjectId(), r.getExtKey(), r.getTitle(), r.getBody(),
                r.getStatus(), r.getPriority(), r.getCreatedAt());
    }
}
