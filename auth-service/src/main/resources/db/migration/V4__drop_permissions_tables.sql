-- Keep V3 immutable for Flyway checksum; remove unused permission model in a new migration.
DROP TABLE IF EXISTS role_permissions;
DROP TABLE IF EXISTS permissions;
