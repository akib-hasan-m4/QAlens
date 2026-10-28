package com.qalens.ai;

import java.util.List;

/** The only way application code talks to a model. Implementations: Ollama for real use, a stub for tests and demos. */
public interface AiClient {

    /** @throws AiUnavailableException when the model cannot be reached or does not answer in time */
    AiCompletion complete(AiPrompt prompt);

    /**
     * Embeds each text into a {@value #DIMENSIONS}-dimensional vector.
     *
     * @throws AiUnavailableException when the model cannot be reached or does not answer in time
     */
    AiEmbeddings embed(List<String> texts);

    /** Width of the {@code vector} columns; the embedding model must produce exactly this many dimensions. */
    int DIMENSIONS = 384;
}
