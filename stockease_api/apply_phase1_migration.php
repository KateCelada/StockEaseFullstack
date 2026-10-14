<?php
require_once __DIR__ . '/db.php';

try {
    echo "Running Phase 1 Migrations...\n";

    // 1. users: must_change_password
    $conn->exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN DEFAULT TRUE;");
    echo "✓ users.must_change_password column added or verified.\n";

    // 2. products: cost_price
    $conn->exec("ALTER TABLE products ADD COLUMN IF NOT EXISTS cost_price DECIMAL(10,2) DEFAULT 0.00;");
    echo "✓ products.cost_price column added or verified.\n";

    // Seed default cost_price for existing products if 0 (e.g. 60-70% of price so profit is immediately realistic)
    $conn->exec("UPDATE products SET cost_price = ROUND(price * 0.65, 2) WHERE cost_price = 0.00 OR cost_price IS NULL;");
    echo "✓ existing products seeded with sensible cost_price.\n";

    // 3. transaction_items: cost_price
    $conn->exec("ALTER TABLE transaction_items ADD COLUMN IF NOT EXISTS cost_price DECIMAL(10,2) DEFAULT 0.00;");
    echo "✓ transaction_items.cost_price column added or verified.\n";

    // Seed historical transaction_items with product cost_price
    $conn->exec("UPDATE transaction_items ti SET cost_price = p.cost_price FROM products p WHERE ti.product_id = p.id AND (ti.cost_price = 0.00 OR ti.cost_price IS NULL);");
    echo "✓ historical transaction_items seeded with cost_price.\n";

    // 4. transactions: status
    $conn->exec("ALTER TABLE transactions ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'completed';");
    $conn->exec("UPDATE transactions SET status = 'completed' WHERE status IS NULL OR status = '';");
    echo "✓ transactions.status column added or verified.\n";

    echo "Phase 1 Migrations completed successfully!\n";
} catch (PDOException $e) {
    echo "Migration Error: " . $e->getMessage() . "\n";
}
?>
