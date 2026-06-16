-- Chuẩn hóa workspace role cũ (ADMIN, EDITOR, VIEWER) → OWNER/MEMBER
-- Chạy trên VPS nếu chưa deploy bản BE mới:
-- docker exec -i willa-ai-postgres psql -U postgres -d willa_ai < workspace_roles_fixup.sql

UPDATE workspace_members wm
SET role = 'OWNER'
FROM workspaces w
WHERE w.id = wm.workspace_id
  AND w.owner_id = wm.user_id
  AND wm.role <> 'OWNER';

UPDATE workspace_members
SET role = 'MEMBER'
WHERE role NOT IN ('OWNER', 'MEMBER');

UPDATE workspace_invites
SET role = 'MEMBER'
WHERE role NOT IN ('OWNER', 'MEMBER');
