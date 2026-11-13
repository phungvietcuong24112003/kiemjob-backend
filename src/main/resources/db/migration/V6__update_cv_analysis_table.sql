-- Migration V6: Đồng bộ cấu trúc bảng cv_analysis
-- Thêm các cột: summary, embedding, analysis_status, analyzed_at, updated_at
-- Thêm ràng buộc duy nhất (UNIQUE) trên cv_id (1-1 giữa cvs và cv_analysis)
-- Thêm check constraint và index cho analysis_status

-- 1. Thêm các cột mới nếu chưa tồn tại
ALTER TABLE cv_analysis ADD COLUMN IF NOT EXISTS summary TEXT;
ALTER TABLE cv_analysis ADD COLUMN IF NOT EXISTS embedding TEXT;
ALTER TABLE cv_analysis ADD COLUMN IF NOT EXISTS analysis_status VARCHAR(20) NOT NULL DEFAULT 'pending';
ALTER TABLE cv_analysis ADD COLUMN IF NOT EXISTS analyzed_at TIMESTAMP;
ALTER TABLE cv_analysis ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 2. Check constraint cho analysis_status
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_cv_analysis_status'
    ) THEN
        ALTER TABLE cv_analysis ADD CONSTRAINT ck_cv_analysis_status
            CHECK (analysis_status IN ('pending', 'processing', 'completed', 'failed'));
    END IF;
END $$;

-- 3. Dọn dẹp dữ liệu trùng lặp cv_id trước khi gắn UNIQUE (chỉ giữ bản ghi mới nhất cho mỗi cv_id)
DELETE FROM cv_analysis a
WHERE a.id NOT IN (
    SELECT MAX(id) FROM cv_analysis GROUP BY cv_id
);

-- 4. Ràng buộc UNIQUE cho cv_id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'cv_analysis'::regclass
          AND contype = 'u'
          AND conname = 'uq_cv_analysis_cv_id'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'cv_analysis'::regclass
          AND contype = 'u'
    ) THEN
        ALTER TABLE cv_analysis ADD CONSTRAINT uq_cv_analysis_cv_id UNIQUE (cv_id);
    END IF;
END $$;

-- 5. Index cho analysis_status
CREATE INDEX IF NOT EXISTS idx_cv_analysis_status
    ON cv_analysis (analysis_status);

