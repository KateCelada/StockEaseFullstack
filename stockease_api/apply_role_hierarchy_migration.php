<?php
/**
 * Role Hierarchy Migration
 * - businesses table (each business is a separate portfolio, even for the same owner)
 * - stores.business_id linking branches to a business
 * - stock_transfers: nullable from_store_id (donor picked by manager on approval),
 *   decline tracking columns
 * - staff_shifts table (cashier/inventory shift start & end tracking)
 *
 * Run once: http://localhost/stockease_api/apply_role_hierarchy_migration.php
 */

require_once __DIR__ . '/db.php';

$log = [];

function run_migration(PDO $conn, string $label, string $sql, array &$log): void
{
    try {
        $conn->exec($sql);
        $log[] = "OK: $label";
    } catch (PDOException $e) {
        $log[] = "SKIP/ERR: $label -> " . $e->getMessage();
    }
}

try {
    // 1. Businesses table
    run_migration($conn, 'Create businesses table', "
        CREATE TABLE IF NOT EXISTS businesses (
            id SERIAL PRIMARY KEY,
            business_name VARCHAR(150) NOT NULL,
            owner_id INT REFERENCES users(id) ON DELETE SET NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
    ", $log);

    // 2. Link stores to a business
    run_migration($conn, 'Add stores.business_id', "
        ALTER TABLE stores ADD COLUMN IF NOT EXISTS business_id INT REFERENCES businesses(id) ON DELETE SET NULL
    ", $log);

    // 3. Transfers: donor branch may be chosen later by the manager
    run_migration($conn, 'stock_transfers.from_store_id nullable', "
        ALTER TABLE stock_transfers ALTER COLUMN from_store_id DROP NOT NULL
    ", $log);
    run_migration($conn, 'stock_transfers decline columns', "
        ALTER TABLE stock_transfers
            ADD COLUMN IF NOT EXISTS declined_at TIMESTAMP,
            ADD COLUMN IF NOT EXISTS declined_by INT REFERENCES users(id) ON DELETE SET NULL,
            ADD COLUMN IF NOT EXISTS decline_reason TEXT
    ", $log);

    // 4. Staff shifts
    run_migration($conn, 'Create staff_shifts table', "
        CREATE TABLE IF NOT EXISTS staff_shifts (
            id SERIAL PRIMARY KEY,
            user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            store_id INT NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
            status VARCHAR(20) NOT NULL DEFAULT 'active',
            started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            ended_at TIMESTAMP,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
    ", $log);
    run_migration($conn, 'staff_shifts unique active index', "
        CREATE UNIQUE INDEX IF NOT EXISTS idx_staff_shifts_active
        ON staff_shifts (user_id) WHERE status = 'active'
    ", $log);

    // 5. Backfill: one default business per owner that has branches but no business link.
    //    The portfolio takes a real branch name, never the owner's personal name.
    try {
        $conn->exec("
            INSERT INTO businesses (business_name, owner_id)
            SELECT DISTINCT COALESCE(
                       (SELECT s.store_name FROM stores s
                        WHERE s.owner_id = ow.id
                        ORDER BY s.id ASC LIMIT 1),
                       'Business'
                   ), ow.id
            FROM stores s
            JOIN users ow ON s.owner_id = ow.id
            WHERE s.business_id IS NULL
              AND NOT EXISTS (
                  SELECT 1 FROM businesses b WHERE b.owner_id = ow.id
              )
        ");
        $conn->exec("
            UPDATE stores s
            SET business_id = (
                SELECT b.id FROM businesses b WHERE b.owner_id = s.owner_id ORDER BY b.id ASC LIMIT 1
            )
            WHERE s.business_id IS NULL AND s.owner_id IS NOT NULL
        ");
        $log[] = "OK: Backfilled default businesses for existing owners";
    } catch (PDOException $e) {
        $log[] = "SKIP/ERR: Business backfill -> " . $e->getMessage();
    }

    echo "<h2>Role hierarchy migration finished</h2><pre>" . htmlspecialchars(implode("\n", $log)) . "</pre>";
} catch (PDOException $e) {
    http_response_code(500);
    echo "<h2>Migration failed</h2><pre>" . htmlspecialchars($e->getMessage() . "\n\n" . implode("\n", $log)) . "</pre>";
}
