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
import java.util.List;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One saved state of a case. Append-only: the entity has no setters and is {@link Immutable}, so Hibernate never
 * issues an UPDATE for it, and no repository method deletes it.
 */
@Entity
@Immutable
@Table(name = "test_case_version")
public class TestCaseVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false, length = 500)
    private String title;

    private String precondition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<TestStep> steps;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CasePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaseOrigin origin;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected TestCaseVersion() {
    }

    public TestCaseVersion(Long caseId, int versionNo, String title, String precondition, List<TestStep> steps,
                           CasePriority priority, CaseOrigin origin, Long createdBy) {
        this.caseId = caseId;
        this.versionNo = versionNo;
        this.title = title;
        this.precondition = precondition;
        this.steps = List.copyOf(steps);
        this.priority = priority;
        this.origin = origin;
        this.createdBy = createdBy;
    }

    public Long getId() {
        return id;
    }

    public Long getCaseId() {
        return caseId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getTitle() {
        return title;
    }

    public String getPrecondition() {
        return precondition;
    }

    public List<TestStep> getSteps() {
        return steps;
    }

    public CasePriority getPriority() {
        return priority;
    }

    public CaseOrigin getOrigin() {
        return origin;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
