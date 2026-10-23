package com.qalens.repository;

/** A project together with the caller's role on it; shaped by the aliases in {@link ProjectRepository}. */
public interface ProjectRow {

    Long getId();

    String getProjKey();

    String getName();

    String getDescription();

    Boolean getArchived();

    String getRoleName();
}
