<?php
header("Content-Type: application/json; charset=UTF-8");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin']);

$input = json_decode(file_get_contents("php://input"), true);
$userId = isset($input['user_id']) ? (int)$input['user_id'] : 0;
$newRole = trim($input['role'] ?? '');
$allowedRoles = ['super_admin', 'store_admin', 'staff'];

if ($userId <= 0 || !in_array($newRole, $allowedRoles, true)) {
    echo json_encode(["success" => false, "message" => "Invalid user ID or role choice"]);
    exit;
}

if ($userId === $identity['user_id'] && $newRole !== 'super_admin') {
    auth_json_error(403, 'You cannot remove the role from your authenticated Super Admin account');
}

try {
    $userStmt = $conn->prepare("SELECT store_id FROM users WHERE id = :id");
    $userStmt->execute(['id' => $userId]);
    $targetUser = $userStmt->fetch(PDO::FETCH_ASSOC);

    if (!$targetUser) {
        echo json_encode(["success" => false, "message" => "User not found"]);
        exit;
    }

    $storeId = isset($input['store_id']) && $input['store_id'] !== '' 
        ? (int)$input['store_id'] 
        : ($targetUser['store_id'] !== null ? (int)$targetUser['store_id'] : null);

    if ($newRole !== 'super_admin' && (empty($storeId) || $storeId <= 0)) {
        echo json_encode([
            "success" => false,
            "message" => "Store administrators and staff must be assigned to a store"
        ]);
        exit;
    }

    if ($newRole === 'super_admin') {
        $storeId = null;
    }

    $stmt = $conn->prepare("UPDATE users SET role = :role, store_id = :store_id WHERE id = :id");
    $stmt->execute(['role' => $newRole, 'store_id' => $storeId, 'id' => $userId]);

    echo json_encode(["success" => true, "message" => "User role and store assignment updated successfully"]);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Failed to update role"]);
}
?>
