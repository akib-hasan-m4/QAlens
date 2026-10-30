package com.qalens.common;

/** A request the caller may make in general but that breaks a business rule; the message is shown to the user. */
public class RuleViolationException extends RuntimeException {

    public RuleViolationException(String message) {
        super(message);
    }
}
