package com.qalens.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization org;

    @Column(name = "proj_key", nullable = false, length = 10)
    private String projKey;

    @Column(nullable = false, length = 200)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Project() {
    }

    public Project(Organization org, String projKey, String name, String description) {
        this.org = org;
        this.projKey = projKey;
        this.name = name;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public Organization getOrg() {
        return org;
    }

    public String getProjKey() {
        return projKey;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isArchived() {
        return archived;
    }

    public void edit(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }
}
