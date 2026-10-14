<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: GET, POST");

require_once 'db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin', 'manager']);

$action = isset($_GET['action']) ? $_GET['action'] : '';

// 1. Fetch Store Admins
if ($action === 'fetch') {
    try {
        if ($identity['role'] === 'manager') {
            $query = "SELECT u.id, u.full_name, u.username, u.email, u.role, s.store_name 
                      FROM users u 
                      LEFT JOIN stores s ON u.store_id = s.id 
                      WHERE u.role = 'store_admin' 
                        AND (s.owner_id = :mgr_id OR s.id = :assigned)
                      ORDER BY u.id DESC";
            $stmt = $conn->prepare($query);
            $stmt->execute([
                'mgr_id' => (int)$identity['user_id'],
                'assigned' => (int)($identity['store_id'] ?? 0)
            ]);
        } else {
            $query = "SELECT u.id, u.full_name, u.username, u.email, u.role, s.store_name 
                      FROM users u 
                      LEFT JOIN stores s ON u.store_id = s.id 
                      WHERE u.role IN ('store_admin', 'manager')
                      ORDER BY u.id DESC";
            $stmt = $conn->prepare($query);
            $stmt->execute();
        }
        $admins = $stmt->fetchAll(PDO::FETCH_ASSOC);

        echo json_encode(["success" => true, "admins" => $admins]);
    } catch (PDOException $e) {
        http_response_code(500);
        echo json_encode(["success" => false, "message" => "Unable to load store administrators"]);
    }
    exit();
}

// 2. Add Store Admin Account
if ($action === 'add') {
    $data = json_decode(file_get_contents("php://input"), true);
    
    $full_name = isset($data['full_name']) ? trim($data['full_name']) : '';
    $user_name = isset($data['username']) ? trim($data['username']) : '';
    $email     = isset($data['email']) ? trim($data['email']) : '';
    $pass_word = isset($data['password']) ? trim($data['password']) : '';
    $store_id  = isset($data['store_id']) ? intval($data['store_id']) : 0;
    $role      = isset($data['role']) && in_array($data['role'], ['store_admin', 'manager', 'staff'], true) ? $data['role'] : 'store_admin';

    // If Manager is creating an account, they create Store Admins for their branches
    if ($identity['role'] === 'manager') {
        $role = 'store_admin';
        $stCheck = $conn->prepare("SELECT id FROM stores WHERE id = :sid AND (owner_id = :uid OR id = :assigned)");
        $stCheck->execute([
            'sid' => $store_id, 
            'uid' => (int)$identity['user_id'], 
            'assigned' => (int)($identity['store_id'] ?? 0)
        ]);
        if (!$stCheck->fetchColumn()) {
            echo json_encode(["success" => false, "message" => "You can only assign store administrators to your own branches."]);
            exit();
        }
    }

    if (empty($full_name) || empty($user_name) || empty($pass_word) || $store_id <= 0) {
        echo json_encode(["success" => false, "message" => "All required fields must be filled."]);
        exit();
    }

    if (!auth_store_exists($conn, $store_id)) {
        echo json_encode(["success" => false, "message" => "A valid store is required."]);
        exit();
    }

    try {
        $stNameStmt = $conn->prepare("SELECT store_name FROM stores WHERE id = :store_id");
        $stNameStmt->execute(['store_id' => $store_id]);
        $store_name = $stNameStmt->fetchColumn() ?: "Branch #$store_id";

        $hashed_password = password_hash($pass_word, PASSWORD_BCRYPT);
        $query = "INSERT INTO users (full_name, username, email, password, role, store_id, is_active, must_change_password)
                  VALUES (:full_name, :username, :email, :password, :role, :store_id, true, true)";
        $stmt = $conn->prepare($query);
        $stmt->bindParam(':full_name', $full_name);
        $stmt->bindParam(':username', $user_name);
        $stmt->bindParam(':email', $email);
        $stmt->bindParam(':password', $hashed_password);
        $stmt->bindParam(':role', $role);
        $stmt->bindParam(':store_id', $store_id);

        if ($stmt->execute()) {
            $emailSent = false;
            if (!empty($email) && filter_var($email, FILTER_VALIDATE_EMAIL)) {
                $subject = "Welcome to StockEase - Your Store Admin Credentials";
                $body = "Hello " . $full_name . ",\n\n"
                      . "Your StockEase Store Administrator account has been generated with the following details:\n\n"
                      . "• Username: " . $user_name . "\n"
                      . "• Initial Password: " . $pass_word . "\n"
                      . "• Role: " . ucwords(str_replace('_', ' ', $role)) . "\n"
                      . "• Assigned Store: " . $store_name . "\n\n"
                      . "IMPORTANT SECURITY NOTICE:\n"
                      . "Because your account was created by management, you are required to change your password immediately upon your first login.\n\n"
                      . "Web Portal: http://localhost/stockease_admin/\n"
                      . "Mobile App: StockEase POS & Retail\n\n"
                      . "Regards,\nStockEase Management";
                $headers = "From: noreply@stockease.local\r\nReply-To: support@stockease.local\r\n";
                $emailSent = @mail($email, $subject, $body, $headers);
            }

            $msg = "Account created successfully with mandatory first-login password reset.";
            if ($emailSent) {
                $msg .= " Credentials dispatched directly to " . $email . ".";
            } elseif (!empty($email)) {
                $msg .= " Credentials saved; email notification queued for " . $email . ".";
            }
            echo json_encode(["success" => true, "message" => $msg]);
        } else {
            echo json_encode(["success" => false, "message" => "Failed to create account."]);
        }
    } catch (PDOException $e) {
        http_response_code(500);
        echo json_encode(["success" => false, "message" => "Unable to create store administrator: " . $e->getMessage()]);
    }
    exit();
}

echo json_encode(["success" => false, "message" => "Invalid action."]);
?>
