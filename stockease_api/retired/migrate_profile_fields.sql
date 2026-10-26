-- StockEase: editable profile fields on users
-- PostgreSQL (same convention as apply_phase1_migration.php / apply_profile_fields_migration.php).
-- Safe to run more than once.

ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(30) DEFAULT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS address VARCHAR(255) DEFAULT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS occupation VARCHAR(100) DEFAULT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS sex VARCHAR(20) DEFAULT NULL;
