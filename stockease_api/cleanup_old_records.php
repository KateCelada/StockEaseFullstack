<?php
require_once 'c:/xampp/htdocs/stockease_api/db.php';

try {
    $conn->beginTransaction();

    // 1. Assign store 8 to owner 12 and manager 13 so it belongs to their branch network
    $conn->exec("UPDATE stores SET owner_id = 12, manager_id = 13 WHERE id = 8");

    // 2. Archive/mark old stores (1, 2, 3, 4, 5, 6) as archived so they don't show up in manager account
    $conn->exec("UPDATE stores SET status = 'archived' WHERE id NOT IN (7, 8)");

    // 3. Delete transactions and transaction_items from old stores
    $conn->exec("DELETE FROM transaction_items WHERE transaction_id IN (SELECT id FROM transactions WHERE store_id NOT IN (7, 8))");
    $deletedTx = $conn->exec("DELETE FROM transactions WHERE store_id NOT IN (7, 8)");
    echo "Deleted old transactions: $deletedTx" . PHP_EOL;

    // 4. Delete old stock transfers that don't belong to store 7 or 8
    $conn->exec("DELETE FROM stock_transfer_items WHERE transfer_id IN (SELECT id FROM stock_transfers WHERE (from_store_id NOT IN (7, 8) AND from_store_id IS NOT NULL) OR to_store_id NOT IN (7, 8))");
    $conn->exec("DELETE FROM stock_transfers WHERE (from_store_id NOT IN (7, 8) AND from_store_id IS NOT NULL) OR to_store_id NOT IN (7, 8)");

    // 5. Delete old products not belonging to store 7 or 8
    $deletedProds = $conn->exec("DELETE FROM products WHERE store_id NOT IN (7, 8)");
    echo "Deleted old products: $deletedProds" . PHP_EOL;

    // 6. Delete old categories for stores not 7 or 8
    $conn->exec("DELETE FROM categories WHERE store_id NOT IN (7, 8)");

    // 7. Make sure must_change_password is FALSE for accounts that already changed password
    $conn->exec("UPDATE users SET must_change_password = FALSE WHERE id IN (12, 13)");

    $conn->commit();
    echo "CLEANUP SUCCESSFUL!" . PHP_EOL;
} catch (Exception $e) {
    if ($conn->inTransaction()) $conn->rollBack();
    echo "ERROR: " . $e->getMessage() . PHP_EOL;
}
