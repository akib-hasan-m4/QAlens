package com.qalens.security;

import com.qalens.common.ResourceNotFoundException;
import com.qalens.repository.ProjectRepository;
import com.qalens.repository.UserProjectRoleRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Used from {@code @PreAuthorize("@projectAccess.hasRole(#projectId, ...)")}.
 * A project outside the caller's organisation, or one where they hold no role, is a 404 (existence is not
 * disclosed). Holding a role that is not sufficient is a 403.
 */
@Component("projectAccess")
public class ProjectAccess {

    private final ProjectRepository projects;
    private final UserProjectRoleRepository memberships;

    public ProjectAccess(ProjectRepository projects, UserProjectRoleRepository memberships) {
        this.projects = projects;
        this.memberships = memberships;
    }

    public boolean hasRole(Long projectId, String... allowedRoles) {
        QalensUserDetails user = currentUser();
        projects.findByIdAndOrgId(projectId, user.getOrgId()).orElseThrow(ResourceNotFoundException::new);
        String role = memberships.findRoleName(user.getId(), projectId).orElseThrow(ResourceNotFoundException::new);
        return Arrays.asList(allowedRoles).contains(role);
    }

    /**
     * Roles are per project, so "may create a project" means holding ADMIN or QA_LEAD on any project of the
     * caller's organisation. Insufficient rights answer 403: the caller is already inside their own organisation.
     */
    public boolean canCreateProject() {
        QalensUserDetails user = currentUser();
        return memberships.holdsRoleInOrg(user.getId(), user.getOrgId(), List.of("ADMIN", "QA_LEAD"));
    }

    public static QalensUserDetails currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof QalensUserDetails user) {
            return user;
        }
        throw new IllegalStateException("No authenticated user");
    }
}
