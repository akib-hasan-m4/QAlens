package com.qalens.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The embedding of one case version under one model. Written once, never changed. */
@Entity
@Immutable
@Table(name = "case_version_embedding")
public class CaseVersionEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_version_id", nullable = false)
    private Long caseVersionId;

    @Column(nullable = false, length = 100)
    private String model;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 384)
    @Column(nullable = false)
    private float[] embedding;

    protected CaseVersionEmbedding() {
    }

    public CaseVersionEmbedding(Long caseVersionId, String model, float[] embedding) {
        this.caseVersionId = caseVersionId;
        this.model = model;
        this.embedding = embedding.clone();
    }

    public Long getId() {
        return id;
    }

    public Long getCaseVersionId() {
        return caseVersionId;
    }

    public String getModel() {
        return model;
    }
}
