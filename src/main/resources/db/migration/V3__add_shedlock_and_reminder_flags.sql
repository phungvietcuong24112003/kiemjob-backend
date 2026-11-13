-- =====================================================================
-- Migration V3: ShedLock distributed lock table and reminder flags
-- =====================================================================

-- 1. ShedLock distributed lock table (used by ShedLock JDBC provider across multiple ECS instances)
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- 2. Add reminder tracking flags to job_posts
ALTER TABLE job_posts ADD COLUMN IF NOT EXISTS expiration_reminder_sent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE job_posts ADD COLUMN IF NOT EXISTS featured_reminder_sent BOOLEAN NOT NULL DEFAULT FALSE;

-- 3. Composite indexes to optimize reminder queries and scheduler updates
CREATE INDEX IF NOT EXISTS idx_job_posts_expiration_reminder 
    ON job_posts (status, application_deadline, expiration_reminder_sent);

CREATE INDEX IF NOT EXISTS idx_job_posts_featured_reminder 
    ON job_posts (is_featured, featured_until, featured_reminder_sent);

