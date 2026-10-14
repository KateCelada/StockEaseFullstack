<?php
require_once 'db.php';
$stmt = $conn->query("SELECT id, username, email, role, password, must_change_password FROM users WHERE id=1");
echo json_encode($stmt->fetch(PDO::FETCH_ASSOC), JSON_PRETTY_PRINT);
