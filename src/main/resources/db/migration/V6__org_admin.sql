-- Organisation administration (users, project membership) is a user-level right, not a project role:
-- anyone who creates a project becomes its ADMIN, so "ADMIN on some project" cannot mean "manages the org".
ALTER TABLE app_user ADD COLUMN is_org_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing installations keep someone who can manage users: every current project ADMIN.
UPDATE app_user SET is_org_admin = TRUE
WHERE id IN (SELECT m.user_id FROM user_project_role m JOIN role r ON r.id = m.role_id WHERE r.name = 'ADMIN');
