package com.qalens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qalens.domain.AppUser;
import com.qalens.domain.Organization;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/** Migration V3: login is case-insensitive, so uniqueness must be as well. */
@SpringBootTest
@Import(PostgresTestContainer.class)
class EmailUniquenessTest {

    @Autowired OrganizationRepository orgs;
    @Autowired AppUserRepository users;

    @Test
    void emailsDifferingOnlyByCaseCannotCoexist() {
        Organization org = orgs.findBySlug("acme").orElseGet(() -> orgs.save(new Organization("acme", "Acme")));
        users.saveAndFlush(new AppUser(org, "Case.Test@acme.test", "Case", "x", true));

        assertThat(users.findByEmailIgnoreCase("case.test@ACME.test")).isPresent();
        assertThatThrownBy(() -> users.saveAndFlush(new AppUser(org, "case.test@acme.test", "Dup", "x", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
