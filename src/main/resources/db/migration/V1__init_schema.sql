CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE organization (
    id          BIGSERIAL PRIMARY KEY,
    slug        VARCHAR(40) NOT NULL UNIQUE,
    name        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    org_id        BIGINT NOT NULL REFERENCES organization(id),
    email         VARCHAR(255) NOT NULL UNIQUE,
    display_name  VARCHAR(200) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE project (
    id          BIGSERIAL PRIMARY KEY,
    org_id      BIGINT NOT NULL REFERENCES organization(id),
    proj_key    VARCHAR(10) NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    archived    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (org_id, proj_key)
);

CREATE TABLE role (
    id   SMALLSERIAL PRIMARY KEY,
    name VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE user_project_role (
    user_id    BIGINT NOT NULL REFERENCES app_user(id),
    project_id BIGINT NOT NULL REFERENCES project(id),
    role_id    SMALLINT NOT NULL REFERENCES role(id),
    PRIMARY KEY (user_id, project_id)
);

CREATE TABLE requirement (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id),
    ext_key    VARCHAR(50),
    title      VARCHAR(500) NOT NULL,
    body       TEXT,
    status     VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    priority   VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE test_suite (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id),
    parent_id  BIGINT REFERENCES test_suite(id),
    name       VARCHAR(200) NOT NULL,
    position   INT NOT NULL DEFAULT 0
);

CREATE TABLE test_case (
    id                 BIGSERIAL PRIMARY KEY,
    suite_id           BIGINT NOT NULL REFERENCES test_suite(id),
    current_version_id BIGINT,
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    owner_id           BIGINT REFERENCES app_user(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only. No UPDATE or DELETE is ever issued against this table.
CREATE TABLE test_case_version (
    id           BIGSERIAL PRIMARY KEY,
    case_id      BIGINT NOT NULL REFERENCES test_case(id),
    version_no   INT NOT NULL,
    title        VARCHAR(500) NOT NULL,
    precondition TEXT,
    steps        JSONB NOT NULL,
    priority     VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    tags         TEXT[],
    origin       VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    created_by   BIGINT NOT NULL REFERENCES app_user(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (case_id, version_no)
);

ALTER TABLE test_case
    ADD CONSTRAINT fk_current_version
    FOREIGN KEY (current_version_id) REFERENCES test_case_version(id);

CREATE TABLE requirement_test_case (
    requirement_id BIGINT NOT NULL REFERENCES requirement(id),
    test_case_id   BIGINT NOT NULL REFERENCES test_case(id),
    PRIMARY KEY (requirement_id, test_case_id)
);

CREATE TABLE milestone (
    id           BIGSERIAL PRIMARY KEY,
    project_id   BIGINT NOT NULL REFERENCES project(id),
    name         VARCHAR(200) NOT NULL,
    due_date     DATE,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE test_plan (
    id           BIGSERIAL PRIMARY KEY,
    project_id   BIGINT NOT NULL REFERENCES project(id),
    milestone_id BIGINT REFERENCES milestone(id),
    name         VARCHAR(200) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE test_run (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id),
    plan_id    BIGINT REFERENCES test_plan(id),
    name       VARCHAR(200) NOT NULL,
    build_ref  VARCHAR(100),
    status     VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_by BIGINT NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at  TIMESTAMPTZ
);

-- case_version_id is the snapshot: frozen when the case joins the run.
CREATE TABLE test_execution (
    id              BIGSERIAL PRIMARY KEY,
    run_id          BIGINT NOT NULL REFERENCES test_run(id),
    case_id         BIGINT NOT NULL REFERENCES test_case(id),
    case_version_id BIGINT NOT NULL REFERENCES test_case_version(id),
    assignee_id     BIGINT REFERENCES app_user(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'UNTESTED',
    UNIQUE (run_id, case_id)
);

CREATE TABLE result (
    id           BIGSERIAL PRIMARY KEY,
    execution_id BIGINT NOT NULL REFERENCES test_execution(id),
    outcome      VARCHAR(10) NOT NULL
                 CHECK (outcome IN ('PASS','FAIL','BLOCKED','SKIPPED')),
    comment      TEXT,
    elapsed_ms   BIGINT,
    executed_by  BIGINT NOT NULL REFERENCES app_user(id),
    executed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE result_attachment (
    id           BIGSERIAL PRIMARY KEY,
    result_id    BIGINT NOT NULL REFERENCES result(id),
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    sha256       CHAR(64) NOT NULL
);

CREATE TABLE defect (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES project(id),
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    severity        VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    priority        VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    component       VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'NEW',
    duplicate_of_id BIGINT REFERENCES defect(id),
    reporter_id     BIGINT NOT NULL REFERENCES app_user(id),
    assignee_id     BIGINT REFERENCES app_user(id),
    external_ref    VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status <> 'DUPLICATE' OR duplicate_of_id IS NOT NULL)
);

CREATE TABLE result_defect (
    result_id BIGINT NOT NULL REFERENCES result(id),
    defect_id BIGINT NOT NULL REFERENCES defect(id),
    PRIMARY KEY (result_id, defect_id)
);

CREATE TABLE defect_embedding (
    defect_id  BIGINT PRIMARY KEY REFERENCES defect(id),
    model      VARCHAR(100) NOT NULL,
    embedding  vector(384) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_defect_embedding_hnsw
    ON defect_embedding USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE TABLE ai_call_log (
    id             BIGSERIAL PRIMARY KEY,
    feature        VARCHAR(50) NOT NULL,
    model          VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(20),
    input_hash     CHAR(64) NOT NULL,
    latency_ms     INT NOT NULL,
    tokens_in      INT,
    tokens_out     INT,
    cost_usd       NUMERIC(10,6),
    status         VARCHAR(20) NOT NULL,
    error_message  TEXT,
    output_ref     TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_call_log_feature_time
    ON ai_call_log (feature, created_at DESC);

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT REFERENCES app_user(id),
    entity_type VARCHAR(50) NOT NULL,
    entity_id   BIGINT NOT NULL,
    action      VARCHAR(30) NOT NULL,
    diff        JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
