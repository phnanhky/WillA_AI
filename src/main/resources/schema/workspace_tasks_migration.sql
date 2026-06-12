-- Workspace v2: task board (replaces design-tool tables)
-- Run manually on existing DB before deploy, or use ddl-auto=update on dev.

DROP TABLE IF EXISTS page_comments CASCADE;
DROP TABLE IF EXISTS workspace_pages CASCADE;
DROP TABLE IF EXISTS workspace_note_messages CASCADE;
DROP TABLE IF EXISTS workspace_library_images CASCADE;
DROP TABLE IF EXISTS task_comments CASCADE;
DROP TABLE IF EXISTS task_attachments CASCADE;
DROP TABLE IF EXISTS task_assignees CASCADE;
DROP TABLE IF EXISTS tasks CASCADE;

ALTER TABLE chat_sessions DROP COLUMN IF EXISTS workspace_id;

ALTER TABLE workspaces DROP COLUMN IF EXISTS notes;
ALTER TABLE workspaces DROP COLUMN IF EXISTS storage_used;
ALTER TABLE workspaces DROP COLUMN IF EXISTS is_public;
ALTER TABLE workspaces DROP COLUMN IF EXISTS likes_count;
ALTER TABLE workspaces DROP COLUMN IF EXISTS clones_count;
ALTER TABLE workspaces DROP COLUMN IF EXISTS library_image_url;
ALTER TABLE workspaces DROP COLUMN IF EXISTS library_file_size_bytes;

ALTER TABLE workspaces RENAME COLUMN name TO title;

ALTER TABLE workspaces ADD COLUMN IF NOT EXISTS invite_code VARCHAR(12) UNIQUE;
ALTER TABLE workspaces ADD COLUMN IF NOT EXISTS qr_code_url VARCHAR(500);

ALTER TABLE workspace_members DROP CONSTRAINT IF EXISTS workspace_members_role_check;
UPDATE workspace_members wm
SET role = 'OWNER'
FROM workspaces w
WHERE w.id = wm.workspace_id
  AND w.owner_id = wm.user_id
  AND wm.role IN ('ADMIN', 'EDITOR', 'VIEWER', 'OWNER');
UPDATE workspace_members SET role = 'MEMBER' WHERE role NOT IN ('OWNER', 'MEMBER');
ALTER TABLE workspace_members ADD COLUMN IF NOT EXISTS is_important BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS tasks (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    due_date TIMESTAMP NULL,
    position INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_assignees (
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    PRIMARY KEY (task_id, user_id)
);

CREATE TABLE IF NOT EXISTS task_attachments (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    file_type VARCHAR(50),
    uploaded_by BIGINT NOT NULL REFERENCES users(id),
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_comments (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tasks_workspace_status ON tasks(workspace_id, status);
