<?php
/**
 * Inventory & Transfer Migration
 * - products.min_stock: per-product minimum threshold used for low stock alerts
 * - stock_transfer_items.base_price / selling_price: price snapshot recorded for
 *   every requested item (base = cost price, selling = shelf price)
 *
 * Run once: http://localhost/stockease_api/apply_inventory_threshold_migration.php
 */

require_once __DIR__ . '/db.php';

$log = [];

function run_inventory_migration(PDO $conn, string $label, string $sql, array &$log): void
{
    try {
        $conn->exec($sql);
        $log[] = "OK: $label";
    } catch (PDOException $e) {
        $log[] = "SKIP/ERR: $label -> " . $e->getMessage();
    }
}

try {
    // 1. Low stock threshold on products
    run_inventory_migration($conn, 'products.min_stock column', "
        ALTER TABLE products ADD COLUMN IF NOT EXISTS min_stock INT NOT NULL DEFAULT 10
    ", $log);

    run_inventory_migration($conn, 'products.min_stock backfill', "
        UPDATE products SET min_stock = 10 WHERE min_stock IS NULL
    ", $log);

    // 2. Price snapshot on transfer items
    run_inventory_migration($conn, 'stock_transfer_items price columns', "
        ALTER TABLE stock_transfer_items
            ADD COLUMN IF NOT EXISTS base_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
            ADD COLUMN IF NOT EXISTS selling_price DECIMAL(10,2) NOT NULL DEFAULT 0.00
    ", $log);

    run_inventory_migration($conn, 'stock_transfer_items price backfill', "
        UPDATE stock_transfer_items sti
        SET base_price = COALESCE(p.cost_price, 0.00),
            selling_price = COALESCE(p.price, 0.00)
        FROM products p
        WHERE sti.product_id = p.id
          AND sti.base_price = 0.00
          AND sti.selling_price = 0.00
    ", $log);

    // 3. Managers must be able to decline a request (status was not allowed before)
    run_inventory_migration($conn, 'stock_transfers status allows declined', "
        ALTER TABLE stock_transfers DROP CONSTRAINT IF EXISTS chk_stock_transfers_status;
        ALTER TABLE stock_transfers ADD CONSTRAINT chk_stock_transfers_status
            CHECK ((status)::text = ANY (ARRAY['requested', 'declined', 'in_transit', 'completed', 'cancelled']))
    ", $log);

    echo "<h2>Inventory threshold migration finished</h2><pre>" . htmlspecialchars(implode("\n", $log)) . "</pre>";
} catch (PDOException $e) {
    http_response_code(500);
    echo "<h2>Migration failed</h2><pre>" . htmlspecialchars($e->getMessage() . "\n\n" . implode("\n", $log)) . "</pre>";
}
