<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$rawBody = file_get_contents('php://input');
$jsonBody = json_decode($rawBody, true);
if (!is_array($jsonBody)) {
    $jsonBody = [];
}

$input = !empty($jsonBody) ? $jsonBody : $_POST;

$userId = isset($_SESSION['user_id']) ? (int)$_SESSION['user_id'] : 0;
if ($userId <= 0 && !empty($input['user_id'])) {
    $userId = (int)$input['user_id'];
}
$usernameInput = trim($input['username'] ?? '');

$currentPassword = (string)($input['current_password'] ?? '');
$newPassword = (string)($input['new_password'] ?? '');
$confirmPassword = (string)($input['confirm_password'] ?? '');

if (empty($currentPassword) || empty($newPassword)) {
    echo json_encode(['success' => false, 'message' => 'Please provide both your current and new password.']);
    exit;
}

if (strlen($newPassword) < 6) {
    echo json_encode(['success' => false, 'message' => 'New password must be at least 6 characters long.']);
    exit;
}

if (!empty($confirmPassword) && $newPassword !== $confirmPassword) {
    echo json_encode(['success' => false, 'message' => 'New password and confirmation do not match.']);
    exit;
}

try {
    if ($userId > 0) {
        $stmt = $conn->prepare("SELECT id, password, full_name, role, username FROM users WHERE id = :id LIMIT 1");
        $stmt->execute(['id' => $userId]);
    } else {
        $stmt = $conn->prepare("SELECT id, password, full_name, role, username FROM users WHERE username = :u LIMIT 1");
        $stmt->execute(['u' => $usernameInput]);
    }
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$user) {
        auth_json_error(404, 'User account not found.');
    }

    if (!password_verify($currentPassword, $user['password'])) {
        echo json_encode(['success' => false, 'message' => 'The current password you entered is incorrect.']);
        exit;
    }

    $newHash = password_hash($newPassword, PASSWORD_DEFAULT);

    $upStmt = $conn->prepare("UPDATE users SET password = :password, must_change_password = FALSE WHERE id = :id");
    $upStmt->execute([
        'password' => $newHash,
        'id' => $user['id']
    ]);

    // Record in audit log
    $auditStmt = $conn->prepare("
        INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
        VALUES (?, ?, ?, ?, ?, ?)
    ");
    $auditStmt->execute([
        $user['full_name'],
        $user['role'],
        'PASSWORD_CHANGED',
        "User #{$user['id']} ({$user['username']})",
        $identity['store_name'] ?? 'System',
        'User successfully changed their account password.'
    ]);

    echo json_encode(['success' => true, 'message' => 'Password updated successfully.']);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'message' => 'Database error updating password.']);
}
