package com.qalens.service.project;

import com.qalens.common.ResourceNotFoundException;
import com.qalens.domain.Project;
import com.qalens.domain.UserProjectRole;
import com.qalens.repository.OrganizationRepository;
import com.qalens.repository.ProjectRepository;
import com.qalens.repository.ProjectRow;
import com.qalens.repository.RoleRepository;
import com.qalens.repository.UserProjectRoleRepository;
import com.qalens.security.ProjectAccess;
import com.qalens.security.QalensUserDetails;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private static final int MAX_KEY_ATTEMPTS = 100;

    private final ProjectRepository projects;
    private final OrganizationRepository orgs;
    private final RoleRepository roles;
    private final UserProjectRoleRepository memberships;
    private final ProjectAccess access;

    public ProjectService(ProjectRepository projects, OrganizationRepository orgs, RoleRepository roles,
                          UserProjectRoleRepository memberships, ProjectAccess access) {
        this.projects = projects;
        this.orgs = orgs;
        this.roles = roles;
        this.memberships = memberships;
        this.access = access;
    }

    /** Presentation hint for hiding the create form; {@link #create} enforces the same rule. */
    @Transactional(readOnly = true)
    public boolean mayCreate() {
        return access.canCreateProject();
    }

    /** Generates a unique key and makes the creator ADMIN of the new project, in one transaction. */
    @PreAuthorize("@projectAccess.canCreateProject()")
    @Transactional
    public ProjectView create(ProjectForm form) {
        QalensUserDetails user = ProjectAccess.currentUser();
        String base = ProjectKeys.base(form.name());
        for (int attempt = 1; attempt <= MAX_KEY_ATTEMPTS; attempt++) {
            String key = ProjectKeys.candidate(base, attempt);
            if (projects.findByOrgIdAndProjKey(user.getOrgId(), key).isEmpty()) {
                Project project = projects.save(
                        new Project(orgs.getReferenceById(user.getOrgId()), key, form.name(), form.description()));
                var admin = roles.findByName("ADMIN").orElseThrow();
                memberships.save(new UserProjectRole(user.getId(), project.getId(), admin));
                return new ProjectView(project.getId(), key, project.getName(), project.getDescription(), false,
                        admin.getName());
            }
        }
        throw new IllegalStateException("No free project key for " + base);
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD')")
    @Transactional
    public void update(Long projectId, ProjectForm form) {
        scoped(projectId).edit(form.name(), form.description());
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD')")
    @Transactional
    public void archive(Long projectId) {
        scoped(projectId).setArchived(true);
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD')")
    @Transactional
    public void restore(Long projectId) {
        scoped(projectId).setArchived(false);
    }

    private Project scoped(Long projectId) {
        return projects.findByIdAndOrgId(projectId, ProjectAccess.currentUser().getOrgId())
                .orElseThrow(ResourceNotFoundException::new);
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
