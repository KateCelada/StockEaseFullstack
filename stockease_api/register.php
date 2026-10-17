<?php
header("Content-Type: application/json; charset=UTF-8");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/password_policy.php';

$identity = auth_require_user($conn);
auth_require_roles($identity, ['store_admin', 'manager', 'super_admin']);

$input = json_decode(file_get_contents("php://input"), true);

$fullName = trim($input['full_name'] ?? '');
$username = trim($input['username'] ?? '');
$email = trim($input['email'] ?? '');
$password = trim($input['password'] ?? '');
$requestedStoreId = isset($input['store_id']) ? (int)$input['store_id'] : 0;
$storeId = auth_resolve_store_context($conn, $identity, $requestedStoreId, false);

if ($fullName === '' || $username === '' || $email === '' || $password === '') {
    echo json_encode([
        "success" => false,
        "message" => "Full name, username, email, password, and a valid store ID are required"
    ]);
    exit;
}

// Password strength policy (same rules as the app and the web panel meters)
if (!password_is_acceptable($password)) {
    password_policy_fail($password);
    exit;
}

try {
    $duplicateStmt = $conn->prepare("SELECT id FROM users WHERE username = :username OR email = :email");
    $duplicateStmt->execute(['username' => $username, 'email' => $email]);
    if ($duplicateStmt->fetch()) {
        echo json_encode(["success" => false, "message" => "Username or email already taken"]);
        exit;
    }

    $stNameStmt = $conn->prepare("SELECT store_name FROM stores WHERE id = :store_id");
    $stNameStmt->execute(['store_id' => $storeId]);
    $storeName = $stNameStmt->fetchColumn() ?: "Branch #$storeId";

    $hashedPassword = password_hash($password, PASSWORD_BCRYPT);
    $stmt = $conn->prepare("INSERT INTO users
        (full_name, username, email, password, role, store_id, is_active, must_change_password)
        VALUES (:full_name, :username, :email, :password, 'staff', :store_id, true, true)");

    $stmt->execute([
        'full_name' => $fullName,
        'username' => $username,
        'email' => $email,
        'password' => $hashedPassword,
        'store_id' => $storeId
    ]);

    $emailSent = false;
    if (filter_var($email, FILTER_VALIDATE_EMAIL)) {
        $subject = "Welcome to StockEase - Cashier Account Details";
        $body = "Hello " . $fullName . ",\n\n"
              . "Your StockEase cashier account has been registered for " . $storeName . ".\n\n"
              . "• Username: " . $username . "\n"
              . "• Initial Password: " . $password . "\n"
              . "• Store Branch: " . $storeName . "\n\n"
              . "MANDATORY FIRST-LOGIN ACTION:\n"
              . "You are required to change your password immediately upon your first login.\n\n"
              . "Login through the StockEase Mobile Application.\n\n"
              . "Regards,\nStockEase Administration";
        $headers = "From: noreply@stockease.local\r\nReply-To: support@stockease.local\r\n";
        $emailSent = @mail($email, $subject, $body, $headers);
    }

    $msg = "Registration successful with mandatory password reset.";
    if ($emailSent) {
        $msg .= " Credentials emailed directly to " . $email . ".";
    }

    echo json_encode(["success" => true, "message" => $msg]);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Registration failed. Please try again."]);
}
?>
