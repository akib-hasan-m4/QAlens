package com.qalens.service.ai;

/** Result of asking the model for drafts. When {@code ok} is false, {@code message} explains it to the user. */
public record GeneratedDrafts(boolean ok, String message, int dropped, DraftsForm form) {
}
