<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$rawInput = file_get_contents("php://input");
$data = json_decode($rawInput, true);
if (!is_array($data)) {
    $data = $_POST;
}

// Authenticate caller (via session or user_id in payload)
$callerId = isset($_SESSION['user_id']) ? (int)$_SESSION['user_id'] : (int)($data['caller_user_id'] ?? ($data['user_id'] ?? 0));
$callerRole = (string)($_SESSION['role'] ?? ($data['caller_role'] ?? ''));

if ($callerId > 0 && empty($callerRole)) {
    $cStmt = $conn->prepare("SELECT role, store_id FROM users WHERE id = :id");
    $cStmt->execute(['id' => $callerId]);
    $cRow = $cStmt->fetch(PDO::FETCH_ASSOC);
    if ($cRow) {
        $callerRole = $cRow['role'];
    }
}

$allowedCallers = ['super_admin', 'store_owner', 'manager', 'store_admin'];
if (!in_array($callerRole, $allowedCallers, true)) {
    // If session active, test standard auth
    if (isset($_SESSION['user_id'])) {
        $identity = auth_require_user($conn);
        auth_require_roles($identity, $allowedCallers);
        $callerId = (int)$identity['user_id'];
        $callerRole = $identity['role'];
    } else {
        http_response_code(403);
        echo json_encode(["success" => false, "message" => "Only Store Admins, Managers, and Super Admins can add staff."]);
        exit;
    }
}

$requestedStoreId = isset($data['store_id']) ? (int)$data['store_id'] : 0;
if ($requestedStoreId <= 0 && isset($_SESSION['store_id'])) {
    $requestedStoreId = (int)$_SESSION['store_id'];
}

$fullName = trim($data['full_name'] ?? '');
$username = trim($data['username'] ?? '');
$email    = trim($data['email'] ?? '');
$password = trim($data['password'] ?? '');
$role     = trim($data['role'] ?? 'staff'); // 'staff' (Cashier) or 'inventory_checker'

if (!in_array($role, ['staff', 'cashier', 'inventory_checker'], true)) {
    $role = 'staff';
}

if (empty($fullName) || empty($username) || empty($password)) {
    echo json_encode(["success" => false, "message" => "Please fill in all required fields (Name, Username, Password)."]);
    exit;
}

if ($requestedStoreId <= 0) {
    echo json_encode(["success" => false, "message" => "A valid store branch assignment is required."]);
    exit;
}

try {
    // Check if username already exists
    $checkStmt = $conn->prepare("SELECT id FROM users WHERE LOWER(username) = LOWER(:u)");
    $checkStmt->execute(['u' => $username]);
    if ($checkStmt->fetchColumn()) {
        echo json_encode(["success" => false, "message" => "Username '@" . $username . "' is already taken. Please choose another."]);
        exit;
    }

    $stNameStmt = $conn->prepare("SELECT store_name FROM stores WHERE id = :store_id");
    $stNameStmt->execute(['store_id' => $requestedStoreId]);
    $storeName = $stNameStmt->fetchColumn() ?: "Store #$requestedStoreId";

    // Hash password securely
    $hashedPassword = password_hash($password, PASSWORD_DEFAULT);

    $stmt = $conn->prepare("
        INSERT INTO users (full_name, username, email, password, role, store_id, is_active, must_change_password)
        VALUES (:full_name, :username, :email, :password, :role, :store_id, true, true)
        RETURNING id, full_name, username, role, store_id
    ");
    $stmt->execute([
        'full_name' => $fullName,
        'username'  => $username,
        'email'     => $email !== '' ? $email : null,
        'password'  => $hashedPassword,
        'role'      => $role,
        'store_id'  => $requestedStoreId
    ]);
    $newUser = $stmt->fetch(PDO::FETCH_ASSOC);

    $roleDisplayName = ($role === 'inventory_checker') ? 'Inventory Checker' : 'Cashier / Staff';

    // Log in audit log
    try {
        $audit = $conn->prepare("
            INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
            VALUES (?, ?, ?, ?, ?, ?)
        ");
        $audit->execute([
            $username,
            $callerRole,
            'STAFF_CREATED',
            $fullName . ' (' . $roleDisplayName . ')',
            $storeName,
            'Staff created with initial temporary password.'
        ]);
    } catch (Exception $ignored) {}

    echo json_encode([
        "success" => true,
        "message" => "$roleDisplayName added successfully!",
        "user" => $newUser
    ]);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Unable to add staff account: " . $e->getMessage()]);
}
