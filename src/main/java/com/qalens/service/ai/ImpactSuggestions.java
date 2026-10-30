package com.qalens.service.ai;

import java.util.List;

/** Result of an impact search. When {@code ok} is false, {@code message} explains it to the user. */
public record ImpactSuggestions(boolean ok, String message, List<ImpactRow> rows, int embedded) {
}
