package com.qalens.repository;

/** A case whose current version is close to a requirement; shaped by the aliases in {@link CaseVersionEmbeddingRepository}. */
public interface ImpactCandidateRow {

    Long getCaseId();

    Integer getVersionNo();

    String getTitle();

    String getPriority();

    String getSuiteName();

    Double getSimilarity();
}
