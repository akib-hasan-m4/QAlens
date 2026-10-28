package com.qalens.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qalens.domain.CaseOrigin;
import com.qalens.domain.CasePriority;
import com.qalens.domain.CaseVersionEmbedding;
import com.qalens.domain.TestCaseVersion;
import com.qalens.domain.TestStep;
import com.qalens.repository.CaseVersionEmbeddingRepository;
import com.qalens.repository.ImpactCandidateRow;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ImpactMatcherTest {

    private final AiClient client = mock(AiClient.class);
    private final AiCallLogger log = mock(AiCallLogger.class);
    private final CaseVersionEmbeddingRepository repo = mock(CaseVersionEmbeddingRepository.class);
    private final ImpactMatcher matcher = new ImpactMatcher(client, log, repo, 0.35, 10);

    private static TestCaseVersion version(String title) {
        return new TestCaseVersion(1L, 1, title, "Signed in", List.of(new TestStep("Pay", "Paid"),
                new TestStep("Look", null)), CasePriority.HIGH, CaseOrigin.MANUAL, 1L);
    }

    private static AiEmbeddings vectors(int n, String model) {
        return new AiEmbeddings(Collections.nCopies(n, new float[] {0.5f, -0.25f}), model, 3);
    }

    @Test
    void missingVersionsAreEmbeddedInBatchesAndTheCallIsLogged() {
        List<TestCaseVersion> missing = IntStream.range(0, ImpactMatcher.BATCH + 1).mapToObj(i -> version("c" + i))
                .toList();
        when(client.embed(anyList())).thenAnswer(a -> vectors(a.<List<String>>getArgument(0).size(), "m1"));
        when(repo.currentVersionsWithoutEmbedding(7L, "m1")).thenReturn(missing);
        ImpactCandidateRow row = mock(ImpactCandidateRow.class);
        when(repo.nearest(eq(7L), eq(9L), eq("m1"), eq("[0.5,-0.25]"), eq(0.35), eq(10))).thenReturn(List.of(row));

        ImpactResult result = matcher.match(7L, 9L, "Refund", "Back to the card");

        assertThat(result.ok()).isTrue();
        assertThat(result.candidates()).containsExactly(row);
        assertThat(result.embedded()).isEqualTo(ImpactMatcher.BATCH + 1);
        verify(client, times(3)).embed(anyList()); // the requirement, then two batches
        verify(repo, times(ImpactMatcher.BATCH + 1)).save(any(CaseVersionEmbedding.class));
        verify(log).record(eq(7L), eq("IMPACT_SUGGESTION"), eq("m1"), eq(ImpactMatcher.VERSION), anyString(),
                anyLong(), eq(9), isNull(), eq("OK"), isNull(), eq("candidates=1,embedded=" + (ImpactMatcher.BATCH + 1)));
    }

    @Test
    void aVersionEmbeddedMeanwhileIsNotCounted() {
        when(client.embed(anyList())).thenAnswer(a -> vectors(a.<List<String>>getArgument(0).size(), "m1"));
        when(repo.currentVersionsWithoutEmbedding(7L, "m1")).thenReturn(List.of(version("a"), version("b")));
        when(repo.save(any(CaseVersionEmbedding.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate")).thenReturn(null);

        assertThat(matcher.match(7L, 9L, "T", null).embedded()).isEqualTo(1);
    }

    @Test
    void unavailableModelIsLoggedAndDegrades() {
        when(client.embed(anyList())).thenThrow(new AiUnavailableException("connection refused"));

        ImpactResult result = matcher.match(7L, 9L, "T", "B");

        assertThat(result.ok()).isFalse();
        assertThat(result.candidates()).isEmpty();
        assertThat(result.message()).contains("not available");
        verify(repo, never()).nearest(anyLong(), anyLong(), anyString(), anyString(), anyDouble(), anyInt());
        verify(log).record(eq(7L), eq("IMPACT_SUGGESTION"), eq("unknown"), any(), anyString(), anyLong(), isNull(),
                isNull(), eq("ERROR"), eq("connection refused"), isNull());
    }

    @Test
    void textsCombineTheFieldsAndAreCut() {
        assertThat(ImpactMatcher.requirementText("Title", null)).isEqualTo("Title");
        assertThat(ImpactMatcher.requirementText("Title", "Body")).isEqualTo("Title. Body");
        assertThat(ImpactMatcher.caseText(version("Case"))).isEqualTo("Case. Signed in Pay Paid Look");
        assertThat(ImpactMatcher.requirementText("x".repeat(3000), null)).hasSize(ImpactMatcher.MAX_TEXT);
        assertThat(ImpactMatcher.literal(new float[] {1f, -0.5f})).isEqualTo("[1.0,-0.5]");
    }
}
