package com.qalens.service;

import java.time.OffsetDateTime;

/** One row of the project's AI call history. {@code drafts}/{@code dropped} are null when the call produced none. */
public record AiCallView(OffsetDateTime createdAt, String feature, String model, String promptVersion, String status,
                         Integer tokensIn, Integer tokensOut, int latencyMs, Integer drafts, Integer dropped,
                         String errorMessage) {
}
