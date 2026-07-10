-- Chạy thủ công trên VPS nếu cần khôi phục nhanh trước khi deploy bản fix:
-- docker exec -i willa-ai-postgres psql -U postgres -d willa_ai < task_deadline_notifications.sql

CREATE TABLE IF NOT EXISTS task_deadline_notifications (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  notification_type VARCHAR(20) NOT NULL,
  due_date TIMESTAMP NOT NULL,
  sent_at TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_task_deadline_notification UNIQUE (task_id, notification_type, due_date)
);

CREATE INDEX IF NOT EXISTS idx_task_deadline_notifications_task
  ON task_deadline_notifications(task_id);
