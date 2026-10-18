package com.qalens.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestContainer.class)
class ApplicationContextTest {

    @Test
    void contextLoadsAndMigrationsApply() {
    }
}
