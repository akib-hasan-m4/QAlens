package com.qalens.config;

import com.qalens.domain.AppUser;
import com.qalens.domain.Organization;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.OrganizationRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Idempotent dev-only demo data. Password for every account is "password". */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private final OrganizationRepository orgs;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public DevDataSeeder(OrganizationRepository orgs, AppUserRepository users, PasswordEncoder encoder) {
        this.orgs = orgs;
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Organization acme = orgs.findBySlug("acme").orElseGet(() -> orgs.save(new Organization("acme", "Acme Corp")));
        seed(acme, "admin@acme.test", "Ada Admin", true);
        seed(acme, "tester@acme.test", "Tom Tester", true);
        seed(acme, "inactive@acme.test", "Ivy Inactive", false);
    }

    private void seed(Organization org, String email, String name, boolean active) {
        if (!users.existsByEmailIgnoreCase(email)) {
            users.save(new AppUser(org, email, name, encoder.encode("password"), active));
        }
    }
}
