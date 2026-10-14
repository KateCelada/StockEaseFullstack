<?php
require_once 'c:/xampp/htdocs/stockease_api/db.php';

try {
    $conn->beginTransaction();

    $conn->exec("DELETE FROM transaction_items WHERE transaction_id IN (SELECT id FROM transactions WHERE store_id NOT IN (7, 8))");
    $conn->exec("DELETE FROM transactions WHERE store_id NOT IN (7, 8)");
    $conn->exec("DELETE FROM stock_transfer_items WHERE transfer_id IN (SELECT id FROM stock_transfers WHERE from_store_id NOT IN (7, 8) OR to_store_id NOT IN (7, 8))");
    $conn->exec("DELETE FROM stock_transfers WHERE from_store_id NOT IN (7, 8) OR to_store_id NOT IN (7, 8)");
    $conn->exec("DELETE FROM products WHERE store_id NOT IN (7, 8)");
    $conn->exec("DELETE FROM categories WHERE store_id NOT IN (7, 8)");
    $conn->exec("UPDATE users SET store_id = NULL WHERE store_id NOT IN (7, 8)");
    
    $deleted = $conn->exec("DELETE FROM stores WHERE id NOT IN (7, 8)");
    echo "Deleted stores: $deleted" . PHP_EOL;

    $conn->commit();
    echo "SUCCESS: Deleted old stores 1 to 6 completely!" . PHP_EOL;
} catch (Exception $e) {
    if ($conn->inTransaction()) $conn->rollBack();
    echo "ERROR: " . $e->getMessage() . PHP_EOL;
}
