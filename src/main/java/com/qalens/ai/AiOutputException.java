package com.qalens.ai;

/** The model answered, but not in a shape that can be used. */
class AiOutputException extends RuntimeException {

    AiOutputException(String message) {
        super(message);
    }
}
