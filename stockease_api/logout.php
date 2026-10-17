<?php
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/shift_helper.php';

// Closing the session also closes the shift that was opened on today's login
$logoutUserId = isset($_SESSION['user_id']) ? (int)$_SESSION['user_id'] : 0;
$shiftEnded = false;
if ($logoutUserId > 0) {
    $shiftEnded = shift_end_active_shift($conn, $logoutUserId) !== null;
}

auth_clear_session();
echo json_encode(['success' => true, 'message' => 'Logout successful', 'shift_ended' => $shiftEnded]);
?>
