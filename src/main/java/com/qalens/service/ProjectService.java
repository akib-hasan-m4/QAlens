package com.qalens.service;

import com.qalens.common.ResourceNotFoundException;
import com.qalens.repository.ProjectRepository;
import com.qalens.repository.ProjectRow;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projects;

    public ProjectService(ProjectRepository projects) {
        this.projects = projects;
    }

    /** Only projects where the caller holds a role; no role means the project is simply absent. */
    @Transactional(readOnly = true)
    public List<ProjectView> listMine() {
        QalensUserDetails user = ProjectAccess.currentUser();
        return projects.findForUser(user.getId(), user.getOrgId()).stream().map(ProjectService::toView).toList();
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER', 'DEVELOPER', 'VIEWER')")
    @Transactional(readOnly = true)
    public ProjectView view(Long projectId) {
        return load(projectId);
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD')")
    @Transactional(readOnly = true)
    public ProjectView settings(Long projectId) {
        return load(projectId);
    }

    private ProjectView load(Long projectId) {
        QalensUserDetails user = ProjectAccess.currentUser();
        return projects.findForUser(user.getId(), user.getOrgId(), projectId)
                .map(ProjectService::toView)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private static ProjectView toView(ProjectRow row) {
        return new ProjectView(row.getId(), row.getProjKey(), row.getName(), row.getDescription(),
                Boolean.TRUE.equals(row.getArchived()), row.getRoleName());
    }
}
