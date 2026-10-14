<?php
require_once 'c:/xampp/htdocs/stockease_api/db.php';

$users = $conn->query("SELECT id, username, role, store_id, must_change_password FROM users ORDER BY id ASC")->fetchAll(PDO::FETCH_ASSOC);
echo "=== USERS ===" . PHP_EOL;
print_r($users);

$stores = $conn->query("SELECT id, store_name, owner_id, manager_id FROM stores ORDER BY id ASC")->fetchAll(PDO::FETCH_ASSOC);
echo "=== STORES ===" . PHP_EOL;
print_r($stores);

$productsCount = $conn->query("SELECT count(*) FROM products")->fetchColumn();
echo "Products count: $productsCount" . PHP_EOL;

$transactionsCount = $conn->query("SELECT count(*) FROM transactions")->fetchColumn();
echo "Transactions count: $transactionsCount" . PHP_EOL;
