package com.qalens.ai;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Calls the local Ollama server in JSON mode with a low temperature, bounded by a hard timeout. */
@Component
@ConditionalOnProperty(name = "qalens.ai.provider", havingValue = "ollama", matchIfMissing = true)
class OllamaAiClient implements AiClient {

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final Duration timeout;
    private final String configuredModel;
    private final String configuredEmbeddingModel;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    OllamaAiClient(ChatModel chatModel, EmbeddingModel embeddingModel,
            @Value("${qalens.ai.timeout:90s}") Duration timeout,
            @Value("${spring.ai.ollama.chat.options.model:unknown}") String configuredModel,
            @Value("${spring.ai.ollama.embedding.options.model:unknown}") String configuredEmbeddingModel) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.timeout = timeout;
        this.configuredModel = configuredModel;
        this.configuredEmbeddingModel = configuredEmbeddingModel;
    }

    @Override
    public AiCompletion complete(AiPrompt prompt) {
        OllamaOptions options = OllamaOptions.builder().format("json").temperature(0.2).build();
        Prompt request = new Prompt(
                List.of(new SystemMessage(prompt.system()), new UserMessage(prompt.user())), options);
        ChatResponse response = withTimeout(() -> chatModel.call(request));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new AiUnavailableException("The model returned no content");
        }
        var meta = response.getMetadata();
        String model = meta.getModel() == null || meta.getModel().isBlank() ? configuredModel : meta.getModel();
        var usage = meta.getUsage();
        return new AiCompletion(response.getResult().getOutput().getText(), model,
                usage == null ? null : usage.getPromptTokens(), usage == null ? null : usage.getCompletionTokens());
    }

    @Override
    public AiEmbeddings embed(List<String> texts) {
        EmbeddingResponse response = withTimeout(() -> embeddingModel.embedForResponse(texts));
        if (response == null || response.getResults().size() != texts.size()) {
            throw new AiUnavailableException("The embedding model returned the wrong number of vectors");
        }
        List<float[]> vectors = response.getResults().stream().map(Embedding::getOutput).toList();
        if (vectors.stream().anyMatch(v -> v == null || v.length != DIMENSIONS)) {
            throw new AiUnavailableException("The embedding model must produce " + DIMENSIONS + " dimensions");
        }
        var meta = response.getMetadata();
        String model = meta.getModel() == null || meta.getModel().isBlank() ? configuredEmbeddingModel : meta.getModel();
        var usage = meta.getUsage();
        return new AiEmbeddings(vectors, model, usage == null ? null : usage.getPromptTokens());
    }

    private <T> T withTimeout(Callable<T> work) {
        Future<T> call = executor.submit(work);
        try {
            return call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            throw new AiUnavailableException("The model did not answer within " + timeout.toSeconds() + " seconds", e);
        } catch (InterruptedException e) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("Interrupted while waiting for the model", e);
        } catch (ExecutionException e) {
            throw new AiUnavailableException("The model call failed: " + e.getCause().getClass().getSimpleName(), e);
        }
    }
}
