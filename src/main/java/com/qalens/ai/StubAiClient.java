package com.qalens.ai;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Deterministic stand-in for demos and offline work: {@code qalens.ai.provider=stub}. */
@Component
@ConditionalOnProperty(name = "qalens.ai.provider", havingValue = "stub")
class StubAiClient implements AiClient {

    static final String CANNED = """
            {"cases":[
              {"title":"Main flow succeeds","precondition":"User is signed in","priority":"HIGH",
               "steps":[{"action":"Perform the main flow with valid data","expected":"The operation completes"},
                        {"action":"Check the confirmation","expected":"A success message is shown"}]},
              {"title":"Invalid input is rejected","precondition":"","priority":"MEDIUM",
               "steps":[{"action":"Submit the form with invalid data","expected":"An error message names the problem"},
                        {"action":"Check stored data","expected":"Nothing was saved"}]},
              {"title":"Boundary value is accepted","precondition":"","priority":"LOW",
               "steps":[{"action":"Enter the largest allowed value","expected":"The value is accepted"}]}
            ]}""";

    private static final Set<String> STOP_WORDS = Set.of("the", "and", "for", "with", "are", "is", "was", "can",
            "not", "any", "into", "from", "that", "this", "its", "has", "have", "shows", "show");

    @Override
    public AiCompletion complete(AiPrompt prompt) {
        return new AiCompletion(CANNED, "stub", 0, 0);
    }

    @Override
    public AiEmbeddings embed(List<String> texts) {
        return new AiEmbeddings(texts.stream().map(StubAiClient::bagOfWords).toList(), "stub", 0);
    }

    /** Hashes each word into a bucket and normalises, so texts that share words point the same way. */
    static float[] bagOfWords(String text) {
        float[] v = new float[DIMENSIONS];
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= 3 && !STOP_WORDS.contains(word)) {
                v[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1;
            }
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm == 0) {
            v[0] = 1; // a zero vector has no direction, and cosine distance to it is undefined
            return v;
        }
        float scale = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < v.length; i++) {
            v[i] *= scale;
        }
        return v;
    }
}
