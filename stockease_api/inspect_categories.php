<?php
require_once 'c:/xampp/htdocs/stockease_api/db.php';
$cols = $conn->query("SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'categories'")->fetchAll(PDO::FETCH_ASSOC);
echo "=== CATEGORIES COLUMNS ===" . PHP_EOL;
foreach ($cols as $c) {
    echo $c['column_name'] . " (" . $c['data_type'] . ")" . PHP_EOL;
}

$cats = $conn->query("SELECT * FROM categories")->fetchAll(PDO::FETCH_ASSOC);
echo "=== CATEGORIES DATA ===" . PHP_EOL;
print_r($cats);
