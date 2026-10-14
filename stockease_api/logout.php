<?php
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");

require_once __DIR__ . '/auth.php';

auth_clear_session();
echo json_encode(['success' => true, 'message' => 'Logout successful']);
?>
