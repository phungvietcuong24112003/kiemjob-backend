-- Migration V7: Thêm content_hash, prompt_version, attempt_count, last_error và GIN index searchProfile
-- Bổ sung thông tin tracking và phục vụ tìm kiếm hồ sơ ứng viên

-- 1. Thêm các cột theo dõi phiên bản và trạng thái xử lý tự động
ALTER TABLE cv_analysis ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
ALTER TABLE cv_analysis ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(20);
ALTER TABLE cv_analysis ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE cv_analysis ADD COLUMN IF NOT EXISTS last_error VARCHAR(500);

-- 2. Tạo GIN index trên khối JSON searchProfile trong parsed_data để tăng tốc độ tìm kiếm ứng viên
CREATE INDEX IF NOT EXISTS idx_cv_analysis_search_profile
    ON cv_analysis USING gin ((parsed_data -> 'searchProfile'));

