-- Thời điểm tick hoàn thành (so sánh với due_date cho thống kê đúng hạn)

ALTER TABLE tasks ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP NULL;
ALTER TABLE task_checklist_items ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP NULL;

UPDATE tasks
SET completed_at = updated_at
WHERE completed = TRUE AND completed_at IS NULL;

UPDATE task_checklist_items
SET completed_at = updated_at
WHERE completed = TRUE AND completed_at IS NULL;
