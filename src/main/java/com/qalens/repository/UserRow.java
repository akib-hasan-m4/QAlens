package com.qalens.repository;

/** A user of the organisation for the administration list; shaped by the aliases in {@link AppUserRepository}. */
public interface UserRow {

    Long getId();

    String getEmail();

    String getDisplayName();

    Boolean getActive();

    Boolean getOrgAdmin();

    Long getProjectCount();
}
