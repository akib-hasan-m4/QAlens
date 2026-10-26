package com.qalens.ai;

import java.util.List;

/** Outcome of a generation request. {@code message} is safe to show to the user. */
public record GenerationResult(Status status, List<CaseDraft> drafts, int dropped, String message) {

    public enum Status { OK, UNAVAILABLE, INVALID_OUTPUT }

    static GenerationResult ok(List<CaseDraft> drafts, int dropped) {
        return new GenerationResult(Status.OK, drafts, dropped, null);
    }

    static GenerationResult failed(Status status, String message) {
        return new GenerationResult(status, List.of(), 0, message);
    }
}
