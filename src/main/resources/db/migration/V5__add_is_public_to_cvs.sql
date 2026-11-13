-- Migration V5: Thêm cột is_public cho bảng cvs và ràng buộc chỉ tối đa 1 CV public cho mỗi ứng viên

ALTER TABLE cvs ADD COLUMN IF NOT EXISTS is_public BOOLEAN NOT NULL DEFAULT FALSE;

-- Ràng buộc Partial Unique Index: Mỗi ứng viên chỉ được có tối đa 1 CV có is_public = TRUE
CREATE UNIQUE INDEX IF NOT EXISTS idx_cvs_one_public_per_candidate 
    ON cvs (candidate_id) 
    WHERE is_public = TRUE;

