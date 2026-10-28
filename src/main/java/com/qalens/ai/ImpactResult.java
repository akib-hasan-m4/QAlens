package com.qalens.ai;

import com.qalens.repository.ImpactCandidateRow;
import java.util.List;

/** Outcome of an impact search: the nearest cases, closest first, and how many versions were newly embedded. */
public record ImpactResult(boolean ok, String message, List<ImpactCandidateRow> candidates, int embedded) {

    static ImpactResult ok(List<ImpactCandidateRow> candidates, int embedded) {
        return new ImpactResult(true, null, List.copyOf(candidates), embedded);
    }

    static ImpactResult unavailable(String message) {
        return new ImpactResult(false, message, List.of(), 0);
    }
}
