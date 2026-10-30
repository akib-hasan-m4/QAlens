package com.qalens.repository;

/** A project member with their role; shaped by the aliases in {@link UserProjectRoleRepository}. */
public interface MemberRow {

    Long getUserId();

    String getEmail();

    String getDisplayName();

    Boolean getActive();

    String getRoleName();
}
