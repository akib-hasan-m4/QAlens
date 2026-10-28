package com.qalens.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StubEmbeddingTest {

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    @Test
    void vectorsAreDeterministicNormalisedAndOfTheColumnWidth() {
        float[] v = StubAiClient.bagOfWords("Pay by card");
        assertThat(v).hasSize(AiClient.DIMENSIONS).containsExactly(StubAiClient.bagOfWords("pay BY card!"));
        assertThat(cosine(v, v)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void textsSharingWordsAreCloserThanUnrelatedOnes() {
        float[] refund = StubAiClient.bagOfWords("Refund a card payment");
        assertThat(cosine(refund, StubAiClient.bagOfWords("Valid card payment is accepted")))
                .isGreaterThan(cosine(refund, StubAiClient.bagOfWords("Wishlist can be shared by link")));
    }

    @Test
    void aTextWithoutWordsStillHasADirection() {
        assertThat(cosine(StubAiClient.bagOfWords("a !"), StubAiClient.bagOfWords("--"))).isEqualTo(1.0);
    }

    @Test
    void embedKeepsInputOrderAndNamesTheStubModel() {
        AiEmbeddings result = new StubAiClient().embed(List.of("one card", "two cards"));
        assertThat(result.model()).isEqualTo("stub");
        assertThat(result.vectors()).hasSize(2);
        assertThat(result.vectors().get(0)).containsExactly(StubAiClient.bagOfWords("one card"));
    }
}
