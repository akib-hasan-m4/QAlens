package com.qalens.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** A statement to be verified. Owned by exactly one project; reached only through a project the caller may access. */
@Entity
@Table(name = "requirement")
public class Requirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "ext_key", length = 50)
    private String extKey;

    @Column(nullable = false, length = 500)
    private String title;

    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequirementStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RequirementPriority priority;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Requirement() {
    }

    public Requirement(Long projectId, Long createdBy, String extKey, String title, String body,
                       RequirementStatus status, RequirementPriority priority) {
        this.projectId = projectId;
        this.createdBy = createdBy;
        edit(extKey, title, body, status, priority);
    }

    public void edit(String extKey, String title, String body, RequirementStatus status,
                     RequirementPriority priority) {
        this.extKey = extKey;
        this.title = title;
        this.body = body;
        this.status = status;
        this.priority = priority;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getExtKey() {
        return extKey;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public RequirementStatus getStatus() {
        return status;
    }

    public RequirementPriority getPriority() {
        return priority;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
