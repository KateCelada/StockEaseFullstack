<?php
require_once 'c:/xampp/htdocs/stockease_api/db.php';
$stmt = $conn->query("
    SELECT conname, pg_get_constraintdef(c.oid) 
    FROM pg_constraint c 
    JOIN pg_class t ON c.conrelid = t.oid 
    WHERE t.relname = 'users'
");
print_r($stmt->fetchAll(PDO::FETCH_ASSOC));
