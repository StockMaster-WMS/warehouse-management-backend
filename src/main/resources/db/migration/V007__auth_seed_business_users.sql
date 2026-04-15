-- Migration V7: Drop obsolete roles column and transition to user_roles table.
-- Sample roles and users are seeded by AuthDataLoader on application startup.
-- This migration removes the legacy roles column from users table.
ALTER TABLE users
    DROP COLUMN IF EXISTS roles;
