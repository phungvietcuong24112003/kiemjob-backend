-- =====================================================================
-- Migration V2: Search indexes, filtering, and featured post indexes
-- =====================================================================

-- Ensure columns exist in case the schema was created without them
ALTER TABLE job_posts ADD COLUMN IF NOT EXISTS is_featured BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE job_posts ADD COLUMN IF NOT EXISTS featured_until TIMESTAMP;

-- Index for lower-case title search
CREATE INDEX IF NOT EXISTS idx_job_posts_title_lower ON job_posts (LOWER(title));

-- Composite index for salary range filtering
CREATE INDEX IF NOT EXISTS idx_job_posts_salary ON job_posts (salary_min, salary_max);

-- Index for working mode filtering
CREATE INDEX IF NOT EXISTS idx_job_posts_workingmode ON job_posts (workingmode);

-- Composite index for featured posts priority ordering
CREATE INDEX IF NOT EXISTS idx_job_posts_featured ON job_posts (is_featured, featured_until);

-- Composite index for status and application deadline check (for public/candidate queries)
CREATE INDEX IF NOT EXISTS idx_job_posts_status_deadline ON job_posts (status, application_deadline);

