package com.qalens.service;

import com.qalens.common.LikeEscaper;
import com.qalens.common.ResourceNotFoundException;
import com.qalens.domain.Requirement;
import com.qalens.repository.RequirementRepository;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requirements live inside a project, so every method authorizes against the project first
 * ({@code projectAccess}: 404 for a foreign or role-less project, 403 for a weak role) and then
 * loads by id and project, so a requirement id from another project is simply not found.
 */
@Service
public class RequirementService {

    /** Request value to sort; anything not listed falls back to the default (newest first). */
    private static final Map<String, Sort> SORTS = Map.of(
            "title", Sort.by("title").ascending().and(Sort.by("id")),
            "status", Sort.by("status").ascending().and(Sort.by("id")),
            "created", Sort.by("createdAt").descending().and(Sort.by("id").descending()));
    private static final Sort DEFAULT_SORT = SORTS.get("created");

    private final RequirementRepository requirements;
    private final ProjectAccess access;

    public RequirementService(RequirementRepository requirements, ProjectAccess access) {
        this.requirements = requirements;
        this.access = access;
    }

    /** Presentation hint for showing edit controls; the write methods enforce the same rule. */
    @Transactional(readOnly = true)
    public boolean mayEdit(Long projectId) {
        return access.hasRole(projectId, "ADMIN", "QA_LEAD", "TESTER");
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER', 'DEVELOPER', 'VIEWER')")
    @Transactional(readOnly = true)
    public List<RequirementView> list(Long projectId, String query, String sort) {
        Sort order = SORTS.getOrDefault(sort == null ? "" : sort, DEFAULT_SORT);
        List<Requirement> found = query == null || query.isBlank()
                ? requirements.findByProjectId(projectId, order)
                : requirements.search(projectId, LikeEscaper.contains(query.strip()), order);
        return found.stream().map(RequirementService::toView).toList();
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER', 'DEVELOPER', 'VIEWER')")
    @Transactional(readOnly = true)
    public RequirementView view(Long projectId, Long requirementId) {
        return toView(scoped(projectId, requirementId));
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER')")
    @Transactional
    public RequirementView create(Long projectId, RequirementForm form) {
        Requirement saved = requirements.save(new Requirement(projectId, ProjectAccess.currentUser().getId(),
                form.extKey(), form.title(), form.body(), form.status(), form.priority()));
        return toView(saved);
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER')")
    @Transactional
    public void update(Long projectId, Long requirementId, RequirementForm form) {
        scoped(projectId, requirementId)
                .edit(form.extKey(), form.title(), form.body(), form.status(), form.priority());
    }

    private Requirement scoped(Long projectId, Long requirementId) {
        return requirements.findByIdAndProjectId(requirementId, projectId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private static RequirementView toView(Requirement r) {
        return new RequirementView(r.getId(), r.getProjectId(), r.getExtKey(), r.getTitle(), r.getBody(),
                r.getStatus(), r.getPriority(), r.getCreatedAt());
    }
}
