package com.qalens.service.project;

import java.util.List;

/** {@code grantable} is what the caller may hand out: all roles for an ADMIN, all but ADMIN for a QA_LEAD. */
public record MembersView(List<MemberView> members, List<MemberCandidate> candidates, List<String> grantable) {
}
