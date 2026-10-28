package com.qalens.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One row per model call. Holds a hash of the input and a short summary of the output, never the raw text. */
@Entity
@Immutable
@Table(name = "ai_call_log")
public class AiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", updatable = false)
    private Long projectId;

    @Column(nullable = false, updatable = false, length = 50)
    private String feature;

    @Column(nullable = false, updatable = false, length = 100)
    private String model;

    @Column(name = "prompt_version", updatable = false, length = 20)
    private String promptVersion;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "input_hash", nullable = false, updatable = false, length = 64)
    private String inputHash;

    @Column(name = "latency_ms", nullable = false, updatable = false)
    private int latencyMs;

    @Column(name = "tokens_in", updatable = false)
    private Integer tokensIn;

    @Column(name = "tokens_out", updatable = false)
    private Integer tokensOut;

    @Column(nullable = false, updatable = false, length = 20)
    private String status;

    @Column(name = "error_message", updatable = false)
    private String errorMessage;

    @Column(name = "output_ref", updatable = false)
    private String outputRef;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AiCallLog() {
    }

    public AiCallLog(Long projectId, String feature, String model, String promptVersion, String inputHash, int latencyMs,
            Integer tokensIn, Integer tokensOut, String status, String errorMessage, String outputRef) {
        this.projectId = projectId;
        this.feature = feature;
        this.model = model;
        this.promptVersion = promptVersion;
        this.inputHash = inputHash;
        this.latencyMs = latencyMs;
        this.tokensIn = tokensIn;
        this.tokensOut = tokensOut;
        this.status = status;
        this.errorMessage = errorMessage;
        this.outputRef = outputRef;
    }

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public String getFeature() {
        return feature;
    }

    public String getModel() {
        return model;
    }

    public String getStatus() {
        return status;
    }

    public String getInputHash() {
        return inputHash;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public Integer getTokensIn() {
        return tokensIn;
    }

    public Integer getTokensOut() {
        return tokensOut;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getOutputRef() {
        return outputRef;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
