<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin']);

$data = json_decode(file_get_contents("php://input"), true);
$target_user_id = isset($data['user_id']) ? (int)$data['user_id'] : 0;

if ($target_user_id <= 0) {
    echo json_encode(["success" => false, "message" => "Valid user ID is required."]);
    exit();
}

if ($target_user_id === (int)$identity['user_id'] || $target_user_id === 1) {
    echo json_encode(["success" => false, "message" => "Primary Super Admin account cannot be deleted."]);
    exit();
}

try {
    $conn->beginTransaction();

    // 1. Check if user exists
    $chk = $conn->prepare("SELECT id, username, role FROM users WHERE id = :uid");
    $chk->execute(['uid' => $target_user_id]);
    $user = $chk->fetch(PDO::FETCH_ASSOC);
    if (!$user) {
        $conn->rollBack();
        echo json_encode(["success" => false, "message" => "User not found."]);
        exit();
    }

    // 2. Unlink from stores (owner_id / manager_id)
    $conn->prepare("UPDATE stores SET owner_id = NULL WHERE owner_id = :uid")->execute(['uid' => $target_user_id]);
    $conn->prepare("UPDATE stores SET manager_id = NULL WHERE manager_id = :uid")->execute(['uid' => $target_user_id]);

    // 3. Delete dependent rows
    $conn->prepare("DELETE FROM stock_transfers WHERE requested_by = :uid OR approved_by = :uid")->execute(['uid' => $target_user_id]);
    $conn->prepare("DELETE FROM subscription_requests WHERE user_id = :uid OR reviewed_by = :uid")->execute(['uid' => $target_user_id]);
    $conn->prepare("DELETE FROM subscriptions WHERE owner_user_id = :uid")->execute(['uid' => $target_user_id]);
    $conn->prepare("DELETE FROM transactions WHERE user_id = :uid")->execute(['uid' => $target_user_id]);
    $conn->prepare("DELETE FROM audit_logs WHERE user_id = :uid")->execute(['uid' => $target_user_id]);

    // 4. Delete user
    $del = $conn->prepare("DELETE FROM users WHERE id = :uid");
    $del->execute(['uid' => $target_user_id]);

    $conn->commit();
    echo json_encode([
        "success" => true,
        "message" => "Account @" . $user['username'] . " has been permanently deleted."
    ]);
} catch (PDOException $e) {
    if ($conn->inTransaction()) {
        $conn->rollBack();
    }
    http_response_code(500);
    error_log('delete_user caught exception: ' . $e->getMessage());
    echo json_encode(["success" => false, "message" => "Failed to delete account. Please try again."]);
}

