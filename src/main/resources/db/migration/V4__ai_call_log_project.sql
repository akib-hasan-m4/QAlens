-- Scope model calls to a project so they can be listed per project without crossing tenants.
-- Earlier rows keep a NULL project and stay out of every project's list.
ALTER TABLE ai_call_log ADD COLUMN project_id BIGINT REFERENCES project(id);

CREATE INDEX idx_ai_call_log_project_time
    ON ai_call_log (project_id, created_at DESC);
