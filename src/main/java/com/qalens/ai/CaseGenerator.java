package com.qalens.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/**
 * Generates draft test cases for one requirement: build the prompt, call the model, validate the answer, log the
 * call. Never throws for model problems; the caller gets a status and falls back to manual authoring.
 */
@Service
public class CaseGenerator {

    private final AiClient client;
    private final AiCallLogger callLog;

    CaseGenerator(AiClient client, AiCallLogger callLog) {
        this.client = client;
        this.callLog = callLog;
    }

    public GenerationResult generate(Long projectId, String requirementKey, String title, String body) {
        AiPrompt prompt = CaseGenerationPrompt.of(requirementKey, title, body);
        String hash = sha256(prompt.system() + "\n" + prompt.user());
        long started = System.nanoTime();
        AiCompletion completion;
        try {
            completion = client.complete(prompt);
        } catch (AiUnavailableException e) {
            callLog.record(projectId, prompt.feature(), "unknown", prompt.version(), hash, elapsedMs(started), null, null,
                    "ERROR", e.getMessage(), null);
            return GenerationResult.failed(GenerationResult.Status.UNAVAILABLE,
                    "The AI model is not available right now. You can still write test cases by hand.");
        }
        long latency = elapsedMs(started);
        try {
            DraftParser.Result parsed = DraftParser.parse(completion.text());
            if (parsed.drafts().isEmpty()) {
                throw new AiOutputException("The model answer contained no usable test cases");
            }
            callLog.record(projectId, prompt.feature(), completion.model(), prompt.version(), hash, latency,
                    completion.tokensIn(), completion.tokensOut(), "OK", null,
                    "drafts=" + parsed.drafts().size() + ",dropped=" + parsed.dropped());
            return GenerationResult.ok(parsed.drafts(), parsed.dropped());
        } catch (AiOutputException e) {
            callLog.record(projectId, prompt.feature(), completion.model(), prompt.version(), hash, latency,
                    completion.tokensIn(), completion.tokensOut(), "INVALID_OUTPUT", e.getMessage(), null);
            return GenerationResult.failed(GenerationResult.Status.INVALID_OUTPUT,
                    "The AI model gave an answer that could not be used. Try again, or write the test cases by hand.");
        }
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
