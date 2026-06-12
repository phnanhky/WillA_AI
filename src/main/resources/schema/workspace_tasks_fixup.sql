-- Idempotent fixup for existing DB (name→title, roles, is_important).
-- Safe to re-run.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'workspaces' AND column_name = 'name'
  ) AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'workspaces' AND column_name = 'title'
  ) THEN
    ALTER TABLE workspaces RENAME COLUMN name TO title;
  END IF;
END $$;

ALTER TABLE workspaces DROP COLUMN IF EXISTS notes;
ALTER TABLE workspaces DROP COLUMN IF EXISTS storage_used;
ALTER TABLE workspaces DROP COLUMN IF EXISTS is_public;
ALTER TABLE workspaces DROP COLUMN IF EXISTS likes_count;
ALTER TABLE workspaces DROP COLUMN IF EXISTS clones_count;
ALTER TABLE workspaces DROP COLUMN IF EXISTS library_image_url;
ALTER TABLE workspaces DROP COLUMN IF EXISTS library_file_size_bytes;

ALTER TABLE workspaces ADD COLUMN IF NOT EXISTS invite_code VARCHAR(12);
ALTER TABLE workspaces ADD COLUMN IF NOT EXISTS qr_code_url VARCHAR(500);

CREATE UNIQUE INDEX IF NOT EXISTS uk_workspaces_invite_code ON workspaces (invite_code) WHERE invite_code IS NOT NULL;

ALTER TABLE workspace_members DROP CONSTRAINT IF EXISTS workspace_members_role_check;

UPDATE workspace_members wm
SET role = 'OWNER'
FROM workspaces w
WHERE w.id = wm.workspace_id
  AND w.owner_id = wm.user_id
  AND wm.role IN ('ADMIN', 'EDITOR', 'VIEWER', 'OWNER');

UPDATE workspace_members
SET role = 'MEMBER'
WHERE role NOT IN ('OWNER', 'MEMBER');

ALTER TABLE workspace_members
  ADD COLUMN IF NOT EXISTS is_important BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE chat_sessions DROP CONSTRAINT IF EXISTS fk369rlbq82nsh6v7fsuw6g0l9d;
ALTER TABLE chat_sessions DROP COLUMN IF EXISTS workspace_id;

DROP TABLE IF EXISTS page_comments CASCADE;
DROP TABLE IF EXISTS workspace_pages CASCADE;
DROP TABLE IF EXISTS workspace_note_messages CASCADE;
DROP TABLE IF EXISTS workspace_library_images CASCADE;

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

ALTER TABLE tasks ADD COLUMN IF NOT EXISTS meet_link VARCHAR(512);
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS label_priority VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS completed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP NULL;

UPDATE tasks
SET completed_at = updated_at
WHERE completed = TRUE AND completed_at IS NULL;

CREATE TABLE IF NOT EXISTS task_checklists (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    position INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS task_checklist_items (
    id BIGSERIAL PRIMARY KEY,
    checklist_id BIGINT NOT NULL REFERENCES task_checklists(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    position INT NOT NULL DEFAULT 0,
    due_date TIMESTAMP NULL,
    assignee_user_id BIGINT NULL REFERENCES users(id),
    priority VARCHAR(20) NOT NULL DEFAULT 'NONE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE task_checklist_items ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP NULL;

UPDATE task_checklist_items
SET completed_at = updated_at
WHERE completed = TRUE AND completed_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_task_checklists_task ON task_checklists(task_id);
CREATE INDEX IF NOT EXISTS idx_task_checklist_items_checklist ON task_checklist_items(checklist_id);
