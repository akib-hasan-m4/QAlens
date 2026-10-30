package com.qalens.service.ai;

import java.time.OffsetDateTime;

/**
 * One row of the project's AI call history. {@code drafts}/{@code dropped} are null when the call produced none;
 * {@code candidates} is set only for impact searches.
 */
public record AiCallView(OffsetDateTime createdAt, String feature, String model, String promptVersion, String status,
                         Integer tokensIn, Integer tokensOut, int latencyMs, Integer drafts, Integer dropped,
                         Integer candidates, String errorMessage) {
}
