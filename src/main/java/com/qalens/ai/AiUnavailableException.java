package com.qalens.ai;

/** The model could not be reached, timed out, or returned an error. Callers degrade to manual authoring. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiUnavailableException(String message) {
        super(message);
    }
}
