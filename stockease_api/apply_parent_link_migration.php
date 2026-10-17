<?php
/**
 * RETIRED - this migration targeted PostgreSQL.
 *
 * StockEase now runs on MySQL/MariaDB (XAMPP) only. Every historical
 * one-off PostgreSQL migration (phase 1 columns, profile fields, role
 * hierarchy, owner/parent links, inventory thresholds) is already baked
 * into the consolidated MySQL schema:
 *
 *     stockease_api/schema_mysql.sql  - full schema for fresh installs
 *     stockease_api/seed_mysql.sql    - default seed data
 *
 * See DEVICE_CHECKLIST.md for the import steps. This script intentionally
 * performs no database changes so it can never corrupt a MySQL database.
 */
header("Content-Type: text/plain; charset=UTF-8");
echo "Retired: this migration script targeted PostgreSQL.\n";
echo "StockEase now uses MySQL/MariaDB only (XAMPP).\n";
echo "Schema: stockease_api/schema_mysql.sql | Seed: stockease_api/seed_mysql.sql\n";
echo "All changes from this script are already part of that schema.\n";
?>
