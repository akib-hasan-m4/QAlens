-- One embedding per case version and model. Versions are append-only, so a row never goes stale; an edit makes a
-- new version that is embedded on the next impact request. Rows of a previous model are simply ignored.
CREATE TABLE case_version_embedding (
    id              BIGSERIAL PRIMARY KEY,
    case_version_id BIGINT NOT NULL REFERENCES test_case_version(id),
    model           VARCHAR(100) NOT NULL,
    embedding       vector(384) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (case_version_id, model)
);

CREATE INDEX idx_case_version_embedding_hnsw
    ON case_version_embedding USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
