package com.qalens.ai;

/** The only way application code talks to a model. Implementations: Ollama for real use, a stub for tests and demos. */
public interface AiClient {

    /** @throws AiUnavailableException when the model cannot be reached or does not answer in time */
    AiCompletion complete(AiPrompt prompt);
}
