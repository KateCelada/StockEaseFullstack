<?php
require_once 'c:/xampp/htdocs/stockease_api/db.php';

$prods = $conn->query("SELECT id, store_id, name, category, stock FROM products ORDER BY id ASC")->fetchAll(PDO::FETCH_ASSOC);
echo "=== PRODUCTS ===" . PHP_EOL;
print_r($prods);

$txs = $conn->query("SELECT id, store_id, user_id, total_amount, created_at FROM transactions ORDER BY id ASC")->fetchAll(PDO::FETCH_ASSOC);
echo "=== TRANSACTIONS ===" . PHP_EOL;
print_r($txs);
