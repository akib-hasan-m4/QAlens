package com.qalens.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Identity of a case. Its content lives in {@link TestCaseVersion}; the only mutable fact here is which version is
 * current.
 */
@Entity
@Table(name = "test_case")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suite_id", nullable = false, updatable = false)
    private Long suiteId;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected TestCase() {
    }

    public TestCase(Long suiteId, Long ownerId) {
        this.suiteId = suiteId;
        this.ownerId = ownerId;
    }

    public void pointTo(TestCaseVersion version) {
        this.currentVersionId = version.getId();
    }

    public Long getId() {
        return id;
    }

    public Long getSuiteId() {
        return suiteId;
    }

    public Long getCurrentVersionId() {
        return currentVersionId;
    }

    public String getStatus() {
        return status;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
