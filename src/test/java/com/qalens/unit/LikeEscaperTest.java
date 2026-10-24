package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.qalens.common.LikeEscaper;
import org.junit.jupiter.api.Test;

class LikeEscaperTest {

    @Test
    void wildcardsAndEscapeCharacterAreEscaped() {
        assertThat(LikeEscaper.escape("100%")).isEqualTo("100\\%");
        assertThat(LikeEscaper.escape("a_b")).isEqualTo("a\\_b");
        assertThat(LikeEscaper.escape("c:\\dir")).isEqualTo("c:\\\\dir");
    }

    @Test
    void backslashIsEscapedFirstSoOtherEscapesSurvive() {
        assertThat(LikeEscaper.escape("\\%")).isEqualTo("\\\\\\%");
    }

    @Test
    void plainTextIsUnchangedAndContainsWrapsIt() {
        assertThat(LikeEscaper.escape("login")).isEqualTo("login");
        assertThat(LikeEscaper.contains("50%_")).isEqualTo("%50\\%\\_%");
    }
}
