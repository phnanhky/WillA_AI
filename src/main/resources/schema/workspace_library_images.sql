-- Chạy nếu upload ảnh báo 500 (bảng chưa được Hibernate tạo)
CREATE TABLE IF NOT EXISTS workspace_library_images (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workspace_library_images_workspace_id
    ON workspace_library_images(workspace_id);
