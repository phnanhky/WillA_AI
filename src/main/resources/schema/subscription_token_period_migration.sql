-- Lưu số dư trước khi cộng plan + số token grant mỗi chu kỳ (MONTHLY/YEARLY).
-- Chạy trên VPS nếu ddl-auto không tự thêm cột:
--   docker exec -i willa-ai-postgres psql -U postgres -d willa_ai < subscription_token_period_migration.sql

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS period_start_token_balance BIGINT NULL;

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS period_token_grant BIGINT NULL;
