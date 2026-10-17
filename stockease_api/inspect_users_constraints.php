<?php
/**
 * RETIRED - this diagnostic targeted PostgreSQL's catalog tables
 * (pg_constraint / pg_get_constraintdef).
 *
 * StockEase now runs on MySQL/MariaDB (XAMPP) only. Foreign keys are
 * declared at the end of stockease_api/schema_mysql.sql via ALTER TABLE,
 * and can be inspected in MySQL with:
 *
 *   SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
 *   FROM information_schema.KEY_COLUMN_USAGE
 *   WHERE TABLE_SCHEMA = 'stockease' AND REFERENCED_TABLE_NAME IS NOT NULL;
 */
header("Content-Type: text/plain; charset=UTF-8");
echo "Retired: this diagnostic queried PostgreSQL catalogs (pg_constraint).\n";
echo "StockEase now uses MySQL/MariaDB only (XAMPP).\n";
echo "See the FK block at the end of stockease_api/schema_mysql.sql.\n";
?>
