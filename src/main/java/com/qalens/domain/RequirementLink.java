package com.qalens.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;

/** A requirement is verified by a test case: one row of the many-to-many {@code requirement_test_case} table. */
@Entity
@Table(name = "requirement_test_case")
@IdClass(RequirementLink.Key.class)
public class RequirementLink {

    public record Key(Long requirementId, Long testCaseId) implements Serializable {
    }

    @Id
    @Column(name = "requirement_id")
    private Long requirementId;

    @Id
    @Column(name = "test_case_id")
    private Long testCaseId;

    protected RequirementLink() {
    }

    public RequirementLink(Long requirementId, Long testCaseId) {
        this.requirementId = requirementId;
        this.testCaseId = testCaseId;
    }

    public Long getRequirementId() {
        return requirementId;
    }

    public Long getTestCaseId() {
        return testCaseId;
    }
}
