package com.qalens.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record UserProjectRoleId(
        @Column(name = "user_id") Long userId,
        @Column(name = "project_id") Long projectId) implements Serializable {
}
