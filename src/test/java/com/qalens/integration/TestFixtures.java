package com.qalens.integration;

import com.qalens.domain.AppUser;
import com.qalens.domain.Organization;
import com.qalens.domain.Project;
import com.qalens.domain.UserProjectRole;
import com.qalens.domain.UserProjectRoleId;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.OrganizationRepository;
import com.qalens.repository.ProjectRepository;
import com.qalens.repository.RoleRepository;
import com.qalens.repository.UserProjectRoleRepository;
import com.qalens.security.QalensUserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Idempotent test data helpers; every call returns the existing row if it is already there. */
class TestFixtures {

    private final OrganizationRepository orgs;
    private final AppUserRepository users;
    private final ProjectRepository projects;
    private final RoleRepository roles;
    private final UserProjectRoleRepository memberships;
    private final PasswordEncoder encoder;

    TestFixtures(OrganizationRepository orgs, AppUserRepository users, ProjectRepository projects,
                 RoleRepository roles, UserProjectRoleRepository memberships, PasswordEncoder encoder) {
        this.orgs = orgs;
        this.users = users;
        this.projects = projects;
        this.roles = roles;
        this.memberships = memberships;
        this.encoder = encoder;
    }

    Organization org(String slug) {
        return orgs.findBySlug(slug).orElseGet(() -> orgs.save(new Organization(slug, slug + " inc")));
    }

    AppUser user(Organization org, String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseGet(() -> users.save(new AppUser(org, email, email, encoder.encode("secret"), true)));
    }

    Project project(Organization org, String key) {
        return projects.findByOrgIdAndProjKey(org.getId(), key)
                .orElseGet(() -> projects.save(new Project(org, key, key + " project", "desc")));
    }

    AppUser orgAdmin(AppUser user) {
        user.setOrgAdmin(true);
        return users.save(user);
    }

    void grant(AppUser user, Project project, String roleName) {
        if (!memberships.existsById(new UserProjectRoleId(user.getId(), project.getId()))) {
            memberships.save(new UserProjectRole(user.getId(), project.getId(), roles.findByName(roleName).orElseThrow()));
        }
    }

    QalensUserDetails principal(AppUser user) {
        Organization org = orgs.findById(user.getOrg().getId()).orElseThrow();
        return new QalensUserDetails(user.getId(), user.getEmail(), user.getDisplayName(), org.getId(),
                org.getSlug(), org.getName(), user.getPasswordHash(), true);
    }
}
