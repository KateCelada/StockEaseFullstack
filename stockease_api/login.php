<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");

require_once 'db.php';
require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/shift_helper.php';

$data = json_decode(file_get_contents("php://input"), true);

$username = isset($data['username']) ? trim($data['username']) : '';
$password = isset($data['password']) ? trim($data['password']) : '';

if (empty($username) || empty($password)) {
    echo json_encode(["success" => false, "message" => "Please provide both username and password."]);
    exit();
}

try {
    $query = "SELECT u.id, u.username, u.email, u.password, u.role, u.store_id, u.full_name, u.is_active, u.must_change_password, s.store_name
              FROM users u
              LEFT JOIN stores s ON u.store_id = s.id
              WHERE u.username = :username
              LIMIT 1";
    $stmt = $conn->prepare($query);
    $stmt->bindParam(':username', $username);
    $stmt->execute();

    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$user || !password_verify($password, $user['password'])) {
        echo json_encode(["success" => false, "message" => "Invalid username or password."]);
        exit();
    }

    $is_active = in_array($user['is_active'], [true, 1, '1', 't', 'true'], true);
    if (!$is_active) {
        echo json_encode(["success" => false, "message" => "This account is inactive."]);
        exit();
    }

    $allowed_roles = ['super_admin', 'store_owner', 'manager', 'store_admin', 'staff', 'inventory_checker'];
    if (!in_array($user['role'], $allowed_roles, true)) {
        echo json_encode(["success" => false, "message" => "This account has an unsupported role."]);
        exit();
    }

    // Check for stores owned or managed by this user
    $ownedStores = [];
    $ownedStmt = $conn->prepare("SELECT id, store_name, location FROM stores WHERE owner_id = :uid OR manager_id = :uid ORDER BY id ASC");
    $ownedStmt->execute(['uid' => $user['id']]);
    $ownedStores = $ownedStmt->fetchAll(PDO::FETCH_ASSOC);

    $store_id = $user['store_id'] !== null ? (int)$user['store_id'] : null;
    if (($user['role'] === 'manager' || $user['role'] === 'store_owner' || $user['role'] === 'store_admin') && (!$store_id || $store_id <= 0) && !empty($ownedStores)) {
        $store_id = (int)$ownedStores[0]['id'];
        $user['store_id'] = $store_id;
        $user['store_name'] = $ownedStores[0]['store_name'];
    }

    // Managers are allowed before their first branch exists: they log in,
    // add the branch, and login.php adopts it on the next sign-in.
    if (!in_array($user['role'], ['super_admin', 'store_owner', 'manager'], true) && (!$store_id || $store_id <= 0)) {
        echo json_encode(["success" => false, "message" => "This account is not assigned to a valid store."]);
        exit();
    }

    if ($store_id !== null && !auth_store_exists($conn, $store_id)) {
        echo json_encode(["success" => false, "message" => "This account is not assigned to a valid store."]);
        exit();
    }

    session_regenerate_id(true);
    auth_store_identity($user);

    // Staff-tier accounts (cashier & inventory) open their shift automatically on
    // the first login of the day - there is no Start Shift button anywhere.
    $dailyShift = null;
    $shiftAutoStarted = false;
    if (in_array($user['role'], ['staff', 'inventory_checker'], true) && $store_id > 0) {
        $shiftResult = shift_start_daily_shift($conn, (int)$user['id'], (int)$store_id);
        $dailyShift = $shiftResult['shift'];
        $shiftAutoStarted = (bool)$shiftResult['started'];
    }

    $must_change = in_array($user['must_change_password'], [true, 1, '1', 't', 'true'], true);

    echo json_encode([
        "success" => true,
        "message" => "Login successful",
        "role" => $user['role'],
        "must_change_password" => $must_change,
        "shift" => $dailyShift,
        "shift_auto_started" => $shiftAutoStarted,
        "user" => [
            "id" => (int)$user['id'],
            "username" => $user['username'],
            "email" => $user['email'] ?? '',
            "role" => $user['role'],
            "store_id" => $store_id,
            "store_name" => $user['store_name'] ?? '',
            "full_name" => $user['full_name'],
            "must_change_password" => $must_change,
            "owned_stores" => $ownedStores
        ]
    ]);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Database error"]);
}
?>
