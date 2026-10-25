package com.qalens.domain;

/** One row of a case's step list, stored inside the {@code steps} JSONB column. */
public record TestStep(String action, String expected) {
}
