package com.qalens.ai;

import java.time.Duration;
import java.util.List;
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
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Calls the local Ollama server in JSON mode with a low temperature, bounded by a hard timeout. */
@Component
@ConditionalOnProperty(name = "qalens.ai.provider", havingValue = "ollama", matchIfMissing = true)
class OllamaAiClient implements AiClient {

    private final ChatModel chatModel;
    private final Duration timeout;
    private final String configuredModel;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    OllamaAiClient(ChatModel chatModel, @Value("${qalens.ai.timeout:90s}") Duration timeout,
            @Value("${spring.ai.ollama.chat.options.model:unknown}") String configuredModel) {
        this.chatModel = chatModel;
        this.timeout = timeout;
        this.configuredModel = configuredModel;
    }

    @Override
    public AiCompletion complete(AiPrompt prompt) {
        OllamaOptions options = OllamaOptions.builder().format("json").temperature(0.2).build();
        Prompt request = new Prompt(
                List.of(new SystemMessage(prompt.system()), new UserMessage(prompt.user())), options);
        Future<ChatResponse> call = executor.submit(() -> chatModel.call(request));
        try {
            ChatResponse response = call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new AiUnavailableException("The model returned no content");
            }
            var meta = response.getMetadata();
            String model = meta.getModel() == null || meta.getModel().isBlank() ? configuredModel : meta.getModel();
            var usage = meta.getUsage();
            return new AiCompletion(response.getResult().getOutput().getText(), model,
                    usage == null ? null : usage.getPromptTokens(), usage == null ? null : usage.getCompletionTokens());
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
