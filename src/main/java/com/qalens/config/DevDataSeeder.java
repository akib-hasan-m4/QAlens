package com.qalens.config;

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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent dev-only demo data. Password for every account is "password".
 *
 * <pre>
 * acme   DEMO  admin=ADMIN lead=QA_LEAD tester=TESTER dev=DEVELOPER viewer=VIEWER
 * acme   SEC   admin=ADMIN only (everyone else has no role: 404)
 * globex GLB   admin@globex.test=ADMIN (another organisation: 404 for acme users)
 * </pre>
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private final OrganizationRepository orgs;
    private final AppUserRepository users;
    private final ProjectRepository projects;
    private final RoleRepository roles;
    private final UserProjectRoleRepository memberships;
    private final PasswordEncoder encoder;

    public DevDataSeeder(OrganizationRepository orgs, AppUserRepository users, ProjectRepository projects,
                         RoleRepository roles, UserProjectRoleRepository memberships, PasswordEncoder encoder) {
        this.orgs = orgs;
        this.users = users;
        this.projects = projects;
        this.roles = roles;
        this.memberships = memberships;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Organization acme = org("acme", "Acme Corp");
        AppUser admin = user(acme, "admin@acme.test", "Ada Admin", true);
        AppUser lead = user(acme, "lead@acme.test", "Lena Lead", true);
        AppUser tester = user(acme, "tester@acme.test", "Tom Tester", true);
        AppUser dev = user(acme, "dev@acme.test", "Dan Developer", true);
        AppUser viewer = user(acme, "viewer@acme.test", "Vera Viewer", true);
        user(acme, "inactive@acme.test", "Ivy Inactive", false);

        Project demo = project(acme, "DEMO", "Demo Project", "Sample project for the showcase.");
        grant(admin, demo, "ADMIN");
        grant(lead, demo, "QA_LEAD");
        grant(tester, demo, "TESTER");
        grant(dev, demo, "DEVELOPER");
        grant(viewer, demo, "VIEWER");

        Project sec = project(acme, "SEC", "Security Review", "Only the admin can see this project.");
        grant(admin, sec, "ADMIN");

        Organization globex = org("globex", "Globex Inc");
        AppUser globexAdmin = user(globex, "admin@globex.test", "Gus Globex", true);
        Project glb = project(globex, "GLB", "Globex Project", "Belongs to another organisation.");
        grant(globexAdmin, glb, "ADMIN");
    }

    private Organization org(String slug, String name) {
        return orgs.findBySlug(slug).orElseGet(() -> orgs.save(new Organization(slug, name)));
    }

    private AppUser user(Organization org, String email, String name, boolean active) {
        return users.findByEmailIgnoreCase(email)
                .orElseGet(() -> users.save(new AppUser(org, email, name, encoder.encode("password"), active)));
    }

    private Project project(Organization org, String key, String name, String description) {
        return projects.findByOrgIdAndProjKey(org.getId(), key)
                .orElseGet(() -> projects.save(new Project(org, key, name, description)));
    }

    private void grant(AppUser user, Project project, String roleName) {
        if (!memberships.existsById(new UserProjectRoleId(user.getId(), project.getId()))) {
            memberships.save(new UserProjectRole(user.getId(), project.getId(),
                    roles.findByName(roleName).orElseThrow()));
        }
    }
}
