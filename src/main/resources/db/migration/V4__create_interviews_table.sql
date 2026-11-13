-- =====================================================================
-- Migration V4: Create interviews table
-- =====================================================================

CREATE TABLE IF NOT EXISTS interviews (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    recruiter_id   BIGINT NOT NULL REFERENCES recruiter(id),
    title          VARCHAR(255) NOT NULL,
    scheduled_at   TIMESTAMP NOT NULL,
    note           TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_interviews_application_id ON interviews (application_id);
CREATE INDEX IF NOT EXISTS idx_interviews_recruiter_id ON interviews (recruiter_id);
CREATE INDEX IF NOT EXISTS idx_interviews_scheduled_at ON interviews (scheduled_at);

