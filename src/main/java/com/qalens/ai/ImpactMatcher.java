package com.qalens.ai;

import com.qalens.domain.CaseVersionEmbedding;
import com.qalens.domain.TestCaseVersion;
import com.qalens.domain.TestStep;
import com.qalens.repository.CaseVersionEmbeddingRepository;
import com.qalens.repository.ImpactCandidateRow;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Finds the existing cases of a project closest in meaning to a requirement: embed the requirement, embed any current
 * case version that has no embedding yet, then search by cosine similarity. Never throws for model problems.
 */
@Service
public class ImpactMatcher {

    static final String FEATURE = "IMPACT_SUGGESTION";
    static final String VERSION = "impact-v1";
    static final int BATCH = 32;
    static final int MAX_TEXT = 2000;

    private final AiClient client;
    private final AiCallLogger callLog;
    private final CaseVersionEmbeddingRepository embeddings;
    private final double minSimilarity;
    private final int limit;

    ImpactMatcher(AiClient client, AiCallLogger callLog, CaseVersionEmbeddingRepository embeddings,
                  @Value("${qalens.impact.min-similarity:0.35}") double minSimilarity,
                  @Value("${qalens.impact.limit:10}") int limit) {
        this.client = client;
        this.callLog = callLog;
        this.embeddings = embeddings;
        this.minSimilarity = minSimilarity;
        this.limit = limit;
    }

    public ImpactResult match(Long projectId, Long requirementId, String title, String body) {
        String text = requirementText(title, body);
        String hash = CaseGenerator.sha256(VERSION + "\n" + text);
        long started = System.nanoTime();
        String model = "unknown";
        try {
            AiEmbeddings query = client.embed(List.of(text));
            model = query.model();
            int tokens = query.tokensIn() == null ? 0 : query.tokensIn();
            int embedded = 0;
            List<TestCaseVersion> missing = embeddings.currentVersionsWithoutEmbedding(projectId, model);
            for (int from = 0; from < missing.size(); from += BATCH) {
                List<TestCaseVersion> batch = missing.subList(from, Math.min(from + BATCH, missing.size()));
                AiEmbeddings vectors = client.embed(batch.stream().map(ImpactMatcher::caseText).toList());
                tokens += vectors.tokensIn() == null ? 0 : vectors.tokensIn();
                embedded += store(batch, vectors);
            }
            List<ImpactCandidateRow> found = embeddings.nearest(projectId, requirementId, model,
                    literal(query.vectors().get(0)), minSimilarity, limit);
            callLog.record(projectId, FEATURE, model, VERSION, hash, CaseGenerator.elapsedMs(started), tokens, null,
                    "OK", null, "candidates=" + found.size() + ",embedded=" + embedded);
            return ImpactResult.ok(found, embedded);
        } catch (AiUnavailableException e) {
            callLog.record(projectId, FEATURE, model, VERSION, hash, CaseGenerator.elapsedMs(started), null, null,
                    "ERROR", e.getMessage(), null);
            return ImpactResult.unavailable(
                    "Impact suggestions are not available right now because the AI model cannot be reached.");
        }
    }

    /** Saves one embedding per version; a version embedded meanwhile by a parallel request is skipped. */
    private int store(List<TestCaseVersion> versions, AiEmbeddings vectors) {
        int saved = 0;
        for (int i = 0; i < versions.size(); i++) {
            try {
                embeddings.save(new CaseVersionEmbedding(versions.get(i).getId(), vectors.model(),
                        vectors.vectors().get(i)));
                saved++;
            } catch (DataIntegrityViolationException alreadyThere) {
                // the unique (case_version_id, model) constraint caught a concurrent insert; that row is just as good
            }
        }
        return saved;
    }

    static String requirementText(String title, String body) {
        return cut(body == null || body.isBlank() ? title : title + ". " + body);
    }

    static String caseText(TestCaseVersion v) {
        StringBuilder text = new StringBuilder(v.getTitle()).append('.');
        if (v.getPrecondition() != null && !v.getPrecondition().isBlank()) {
            text.append(' ').append(v.getPrecondition());
        }
        for (TestStep step : v.getSteps()) {
            text.append(' ').append(step.action());
            if (step.expected() != null && !step.expected().isBlank()) {
                text.append(' ').append(step.expected());
            }
        }
        return cut(text.toString());
    }

    /** pgvector's text form, e.g. {@code [0.1,-0.2]}. */
    static String literal(float[] vector) {
        List<String> parts = new ArrayList<>(vector.length);
        for (float x : vector) {
            parts.add(Float.toString(x));
        }
        return parts.stream().collect(Collectors.joining(",", "[", "]"));
    }

    private static String cut(String text) {
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }
}
