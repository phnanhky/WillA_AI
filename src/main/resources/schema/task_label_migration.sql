-- Nhãn ưu tiên trên thẻ task
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS label_priority VARCHAR(20) NOT NULL DEFAULT 'NONE';
