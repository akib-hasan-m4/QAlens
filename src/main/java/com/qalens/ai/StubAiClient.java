package com.qalens.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Deterministic stand-in for demos and offline work: {@code qalens.ai.provider=stub}. */
@Component
@ConditionalOnProperty(name = "qalens.ai.provider", havingValue = "stub")
class StubAiClient implements AiClient {

    static final String CANNED = """
            {"cases":[
              {"title":"Main flow succeeds","precondition":"User is signed in","priority":"HIGH",
               "steps":[{"action":"Perform the main flow with valid data","expected":"The operation completes"},
                        {"action":"Check the confirmation","expected":"A success message is shown"}]},
              {"title":"Invalid input is rejected","precondition":"","priority":"MEDIUM",
               "steps":[{"action":"Submit the form with invalid data","expected":"An error message names the problem"},
                        {"action":"Check stored data","expected":"Nothing was saved"}]},
              {"title":"Boundary value is accepted","precondition":"","priority":"LOW",
               "steps":[{"action":"Enter the largest allowed value","expected":"The value is accepted"}]}
            ]}""";

    @Override
    public AiCompletion complete(AiPrompt prompt) {
        return new AiCompletion(CANNED, "stub", 0, 0);
    }
}
