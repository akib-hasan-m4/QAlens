package com.qalens.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A folder of test cases inside one project; suites nest through {@code parentId}. */
@Entity
@Table(name = "test_suite")
public class TestSuite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "parent_id", updatable = false)
    private Long parentId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private int position;

    protected TestSuite() {
    }

    public TestSuite(Long projectId, Long parentId, String name, int position) {
        this.projectId = projectId;
        this.parentId = parentId;
        this.name = name;
        this.position = position;
    }

    public void rename(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }
}
