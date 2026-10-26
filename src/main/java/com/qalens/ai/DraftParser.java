package com.qalens.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.qalens.domain.CasePriority;
import com.qalens.domain.TestStep;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns model output into validated drafts. Model output is untrusted: anything that is not valid JSON is rejected
 * as a whole, while individual bad drafts are repaired where that is safe (unknown priority becomes MEDIUM, overlong
 * text is cut) or dropped (no title, no usable steps, duplicate title).
 */
final class DraftParser {

    static final int MAX_DRAFTS = 10;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    record Result(List<CaseDraft> drafts, int dropped) {
    }

    private DraftParser() {
    }

    /** @throws AiOutputException when the text is not JSON in a recognised shape */
    static Result parse(String text) {
        if (text == null || text.isBlank()) {
            throw new AiOutputException("The model returned an empty answer");
        }
        JsonNode root;
        try {
            root = JSON.readTree(stripFence(text));
        } catch (Exception e) {
            throw new AiOutputException("The model answer is not valid JSON");
        }
        JsonNode cases = root.isArray() ? root : root.path("cases");
        if (!cases.isArray()) {
            cases = root.isObject() && root.has("title") ? JSON.createArrayNode().add(root) : null;
        }
        if (cases == null) {
            throw new AiOutputException("The model answer has no list of cases");
        }
        List<CaseDraft> drafts = new ArrayList<>();
        Set<String> titles = new HashSet<>();
        int dropped = 0;
        for (JsonNode node : cases) {
            CaseDraft draft = drafts.size() >= MAX_DRAFTS ? null : toDraft(node);
            if (draft == null || !titles.add(draft.title().toLowerCase(Locale.ROOT))) {
                dropped++;
            } else {
                drafts.add(draft);
            }
        }
        return new Result(List.copyOf(drafts), dropped);
    }

    private static CaseDraft toDraft(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        String title = singleLine(text(node, "title"), CaseDraft.MAX_TITLE);
        if (title.isEmpty()) {
            return null;
        }
        List<TestStep> steps = new ArrayList<>();
        JsonNode rawSteps = node.path("steps");
        if (rawSteps.isArray()) {
            for (JsonNode step : rawSteps) {
                if (steps.size() >= CaseDraft.MAX_STEPS) {
                    break;
                }
                String action = step.isTextual() ? clip(step.asText(), CaseDraft.MAX_STEP_TEXT)
                        : clip(text(step, "action"), CaseDraft.MAX_STEP_TEXT);
                if (!action.isEmpty()) {
                    steps.add(new TestStep(action, clip(text(step, "expected"), CaseDraft.MAX_STEP_TEXT)));
                }
            }
        }
        if (steps.isEmpty()) {
            return null;
        }
        String precondition = clip(text(node, "precondition"), CaseDraft.MAX_PRECONDITION);
        return new CaseDraft(title, precondition.isEmpty() ? null : precondition, priority(text(node, "priority")),
                List.copyOf(steps));
    }

    private static CasePriority priority(String raw) {
        try {
            return CasePriority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CasePriority.MEDIUM;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() ? value.asText() : "";
    }

    private static String singleLine(String value, int max) {
        return clip(value.replaceAll("\\p{Cntrl}+", " "), max);
    }

    private static String clip(String value, int max) {
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max).strip();
    }

    /** Models sometimes wrap JSON in a markdown fence even in JSON mode. */
    private static String stripFence(String text) {
        String t = text.strip();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            int lastFence = t.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return t.substring(firstNewline + 1, lastFence).strip();
            }
        }
        return t;
    }
}
