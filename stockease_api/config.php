<?php
header("Content-Type: application/json; charset=UTF-8");

require_once __DIR__ . '/db.php';

// Preserve the variable name expected by endpoints that historically loaded config.php.
$pdo = $conn;
?>
