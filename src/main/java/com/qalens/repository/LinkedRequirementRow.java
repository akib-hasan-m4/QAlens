package com.qalens.repository;

/** A requirement a case is linked to; shaped by the aliases in {@link RequirementLinkRepository}. */
public interface LinkedRequirementRow {

    Long getCaseId();

    Long getRequirementId();

    String getExtKey();

    String getTitle();
}
