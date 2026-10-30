package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.qalens.service.project.ProjectKeys;
import org.junit.jupiter.api.Test;

class ProjectKeysTest {

    private static final String VALID = "^[A-Z][A-Z0-9]{1,9}$";

    @Test
    void multiWordNamesUseInitials() {
        assertThat(ProjectKeys.base("Payment Gateway")).isEqualTo("PG");
        assertThat(ProjectKeys.base("Customer self-service portal")).isEqualTo("CSSP");
    }

    @Test
    void singleWordUsesFirstFiveLetters() {
        assertThat(ProjectKeys.base("Checkout")).isEqualTo("CHECK");
        assertThat(ProjectKeys.base("QA")).isEqualTo("QA");
    }

    @Test
    void accentsAreStrippedAndDigitFirstGetsAPrefix() {
        assertThat(ProjectKeys.base("Café Ordering")).isEqualTo("CO");
        assertThat(ProjectKeys.base("2025 Migration")).isEqualTo("P2M");
    }

    @Test
    void unusableNamesStillProduceAValidKey() {
        assertThat(ProjectKeys.base("日本語")).isEqualTo("PRJ");
        assertThat(ProjectKeys.base("!!!")).isEqualTo("PRJ");
        assertThat(ProjectKeys.base("X")).isEqualTo("XX");
    }

    @Test
    void everyGeneratedKeyMatchesTheSchemaPattern() {
        for (String name : new String[] {"a", "A b c d e f g h i j k l", "9 9 9", "x".repeat(300), "-_-", "É"}) {
            assertThat(ProjectKeys.base(name)).matches(VALID);
        }
    }

    @Test
    void collisionsAppendASuffixAndStayWithinTenCharacters() {
        assertThat(ProjectKeys.candidate("PG", 1)).isEqualTo("PG");
        assertThat(ProjectKeys.candidate("PG", 2)).isEqualTo("PG2");
        assertThat(ProjectKeys.candidate("ABCDEFGHIJ", 2)).isEqualTo("ABCDEFGHI2");
        assertThat(ProjectKeys.candidate("ABCDEFGHIJ", 12)).isEqualTo("ABCDEFGH12").hasSize(10);
    }
}
