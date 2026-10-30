package com.qalens.service.project;

/** An active user of the organisation who is not on the project yet. */
public record MemberCandidate(Long id, String displayName, String email) {
}
