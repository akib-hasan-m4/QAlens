package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.qalens.service.user.NewUserForm;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class NewUserFormTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(String email, String name, String password) {
        return validator.validate(new NewUserForm(email, name, password, false)).isEmpty();
    }

    @Test
    void ordinaryInputPasses() {
        assertThat(valid("nina@acme.test", "Nina O'Neil-Smith", "correct horse")).isTrue();
        assertThat(valid("jose@acme.test", "José Müller", "0123456789")).isTrue();
    }

    @Test
    void emailIsTrimmedAndLowerCased() {
        assertThat(new NewUserForm("  Nina@ACME.test ", "Nina", "x", false).email()).isEqualTo("nina@acme.test");
    }

    @Test
    void badEmailOrNameFails() {
        assertThat(valid("not-an-email", "Nina", "correct horse")).isFalse();
        assertThat(valid("nina@acme.test", "<script>", "correct horse")).isFalse();
        assertThat(valid("nina@acme.test", "  ", "correct horse")).isFalse();
    }

    @Test
    void passwordMustBeLongEnoughButFitBcrypt() {
        assertThat(valid("nina@acme.test", "Nina", "short")).isFalse();
        assertThat(valid("nina@acme.test", "Nina", "a".repeat(72))).isTrue();
        assertThat(valid("nina@acme.test", "Nina", "a".repeat(73))).isFalse();
        assertThat(valid("nina@acme.test", "Nina", "é".repeat(37))).isFalse();
    }
}
