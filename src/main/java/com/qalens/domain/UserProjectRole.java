package com.qalens.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A user's single role on a project. Roles are per project, never global. */
@Entity
@Table(name = "user_project_role")
public class UserProjectRole {

    @EmbeddedId
    private UserProjectRoleId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    protected UserProjectRole() {
    }

    public UserProjectRole(Long userId, Long projectId, Role role) {
        this.id = new UserProjectRoleId(userId, projectId);
        this.role = role;
    }

    public UserProjectRoleId getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }
}
