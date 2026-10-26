package com.qalens.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qalens.domain.CasePriority;
import org.junit.jupiter.api.Test;

class DraftParserTest {

    private static final String VALID = """
            {"cases":[{"title":"Pay with a valid card","precondition":"Cart has items","priority":"high",
            "steps":[{"action":"Enter card","expected":"Accepted"},{"action":"Submit","expected":"Order placed"}]}]}""";

    @Test
    void parsesAWellFormedAnswer() {
        var result = DraftParser.parse(VALID);

        assertThat(result.dropped()).isZero();
        assertThat(result.drafts()).singleElement().satisfies(d -> {
            assertThat(d.title()).isEqualTo("Pay with a valid card");
            assertThat(d.priority()).isEqualTo(CasePriority.HIGH);
            assertThat(d.steps()).hasSize(2);
            assertThat(d.steps().get(0).expected()).isEqualTo("Accepted");
        });
    }

    @Test
    void acceptsAMarkdownFence() {
        assertThat(DraftParser.parse("```json\n" + VALID + "\n```").drafts()).hasSize(1);
    }

    @Test
    void acceptsABareArrayAndASingleCase() {
        String one = "{\"title\":\"A\",\"steps\":[{\"action\":\"x\",\"expected\":\"y\"}]}";
        assertThat(DraftParser.parse("[" + one + "]").drafts()).hasSize(1);
        assertThat(DraftParser.parse(one).drafts()).hasSize(1);
    }

    @Test
    void repairsUnknownPriorityAndBlankPrecondition() {
        var d = DraftParser.parse("""
                {"cases":[{"title":"A","precondition":"  ","priority":"URGENT","steps":["Just do it"]}]}""")
                .drafts().get(0);

        assertThat(d.priority()).isEqualTo(CasePriority.MEDIUM);
        assertThat(d.precondition()).isNull();
        assertThat(d.steps().get(0).action()).isEqualTo("Just do it");
        assertThat(d.steps().get(0).expected()).isEmpty();
    }

    @Test
    void dropsCasesWithoutTitleOrUsableSteps() {
        var result = DraftParser.parse("""
                {"cases":[{"title":"","steps":[{"action":"x"}]},
                          {"title":"No steps","steps":[]},
                          {"title":"Blank steps","steps":[{"action":" ","expected":"y"}]},
                          "not an object",
                          {"title":"Good","steps":[{"action":"x","expected":"y"}]}]}""");

        assertThat(result.drafts()).extracting(CaseDraft::title).containsExactly("Good");
        assertThat(result.dropped()).isEqualTo(4);
    }

    @Test
    void dropsDuplicateTitlesIgnoringCase() {
        var result = DraftParser.parse("""
                {"cases":[{"title":"Same","steps":[{"action":"a"}]},{"title":"SAME","steps":[{"action":"b"}]}]}""");

        assertThat(result.drafts()).hasSize(1);
        assertThat(result.dropped()).isEqualTo(1);
    }

    @Test
    void cutsOverlongTextAndCapsCounts() {
        String longText = "x".repeat(CaseDraft.MAX_STEP_TEXT + 50);
        var d = DraftParser.parse("{\"cases\":[{\"title\":\"" + "t".repeat(600) + "\",\"steps\":[{\"action\":\""
                + longText + "\",\"expected\":\"ok\"}]}]}").drafts().get(0);
        assertThat(d.title()).hasSize(CaseDraft.MAX_TITLE);
        assertThat(d.steps().get(0).action()).hasSize(CaseDraft.MAX_STEP_TEXT);

        StringBuilder many = new StringBuilder("{\"cases\":[");
        for (int i = 0; i < 15; i++) {
            many.append(i > 0 ? "," : "").append("{\"title\":\"T").append(i).append("\",\"steps\":[{\"action\":\"a\"}]}");
        }
        var capped = DraftParser.parse(many.append("]}").toString());
        assertThat(capped.drafts()).hasSize(DraftParser.MAX_DRAFTS);
        assertThat(capped.dropped()).isEqualTo(5);
    }

    @Test
    void stepsAreCappedAt50() {
        StringBuilder steps = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            steps.append(i > 0 ? "," : "").append("{\"action\":\"a").append(i).append("\"}");
        }
        var d = DraftParser.parse("{\"cases\":[{\"title\":\"T\",\"steps\":[" + steps + "]}]}").drafts().get(0);
        assertThat(d.steps()).hasSize(CaseDraft.MAX_STEPS);
    }

    @Test
    void controlCharactersInTitleBecomeSpaces() {
        var d = DraftParser.parse("{\"cases\":[{\"title\":\"Line one\\nLine two\",\"steps\":[{\"action\":\"a\"}]}]}")
                .drafts().get(0);
        assertThat(d.title()).isEqualTo("Line one Line two");
    }

    @Test
    void rejectsAnswersThatAreNotUsableJson() {
        assertThatThrownBy(() -> DraftParser.parse(null)).isInstanceOf(AiOutputException.class);
        assertThatThrownBy(() -> DraftParser.parse("  ")).isInstanceOf(AiOutputException.class);
        assertThatThrownBy(() -> DraftParser.parse("Sure! Here are your tests")).isInstanceOf(AiOutputException.class);
        assertThatThrownBy(() -> DraftParser.parse("{\"answer\":42}")).isInstanceOf(AiOutputException.class);
        assertThatThrownBy(() -> DraftParser.parse("{\"cases\":[{\"title\":\"cut off")).isInstanceOf(AiOutputException.class);
    }

    @Test
    void stubAnswerIsValid() {
        assertThat(DraftParser.parse(StubAiClient.CANNED).drafts()).hasSize(3);
    }
}
