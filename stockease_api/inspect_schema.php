<?php
require_once 'c:/xampp/htdocs/stockease_api/db.php';

$cols = $conn->query("SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'products'")->fetchAll(PDO::FETCH_ASSOC);
echo "=== PRODUCTS COLUMNS ===" . PHP_EOL;
foreach ($cols as $c) {
    echo $c['column_name'] . " (" . $c['data_type'] . ")" . PHP_EOL;
}

$prods = $conn->query("SELECT id, store_id, product_name, category, stock FROM products ORDER BY id ASC")->fetchAll(PDO::FETCH_ASSOC);
echo "=== PRODUCTS ===" . PHP_EOL;
print_r($prods);

$txs = $conn->query("SELECT id, store_id, user_id, total_amount, created_at FROM transactions ORDER BY id ASC")->fetchAll(PDO::FETCH_ASSOC);
echo "=== TRANSACTIONS ===" . PHP_EOL;
print_r($txs);
