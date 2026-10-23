-- Login looks users up case-insensitively, so uniqueness must be case-insensitive too.
CREATE UNIQUE INDEX uq_app_user_email_lower ON app_user (lower(email));
