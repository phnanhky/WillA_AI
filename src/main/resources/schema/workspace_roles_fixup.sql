-- Chuẩn hóa workspace role cũ (ADMIN, EDITOR, VIEWER) → OWNER/MEMBER
-- Chạy trên VPS nếu backend crash vì workspace_invites_role_check:
-- docker exec -i willa-ai-postgres psql -U postgres -d willa_ai < workspace_roles_fixup.sql

ALTER TABLE workspace_members DROP CONSTRAINT IF EXISTS workspace_members_role_check;
ALTER TABLE workspace_invites DROP CONSTRAINT IF EXISTS workspace_invites_role_check;

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
