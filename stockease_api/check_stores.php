<?php
require_once 'db.php';
$stmt = $conn->query("SELECT * FROM stores");
echo json_encode($stmt->fetchAll(PDO::FETCH_ASSOC), JSON_PRETTY_PRINT);
