package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.qalens.tenant.SlugPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SlugPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"admin", "api", "login", "logout", "static", "assets", "health", "actuator", "signup", "o"})
    void reservedWordsNeverResolve(String slug) {
        assertThat(SlugPolicy.isAllowed(slug)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"acme", "globex", "my-org", "org42", "a1b"})
    void ordinarySlugsAreAllowed(String slug) {
        assertThat(SlugPolicy.isAllowed(slug)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "A", "ACME", "-acme", "acme-", "has space", "a/b", "..", "ac_me", "x"})
    void malformedSlugsAreRejected(String slug) {
        assertThat(SlugPolicy.isAllowed(slug)).isFalse();
    }

    @Test
    void nullIsRejected() {
        assertThat(SlugPolicy.isAllowed(null)).isFalse();
    }
}
