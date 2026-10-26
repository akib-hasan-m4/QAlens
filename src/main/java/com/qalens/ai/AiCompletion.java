package com.qalens.ai;

/** Raw model output with the model name and token counts when the backend reports them. */
public record AiCompletion(String text, String model, Integer tokensIn, Integer tokensOut) {
}
