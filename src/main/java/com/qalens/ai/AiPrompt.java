package com.qalens.ai;

/** A system instruction plus the user content, tagged with the feature and prompt version used for logging. */
public record AiPrompt(String feature, String version, String system, String user) {
}
