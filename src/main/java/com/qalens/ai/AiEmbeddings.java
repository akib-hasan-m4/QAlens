package com.qalens.ai;

import java.util.List;

/** One vector per input text, in input order, with the model name and prompt token count when reported. */
public record AiEmbeddings(List<float[]> vectors, String model, Integer tokensIn) {
}
