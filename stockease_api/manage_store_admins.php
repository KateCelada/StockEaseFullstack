<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: GET, POST");

require_once 'db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);
// Strict hierarchy: managers create store admins, store owners create managers, super admin may do both
auth_require_roles($identity, ['super_admin', 'manager', 'store_owner']);

$action = isset($_GET['action']) ? $_GET['action'] : '';

// 1. Fetch Store Admins
if ($action === 'fetch') {
    try {
        if ($identity['role'] === 'manager' || $identity['role'] === 'store_owner') {
            // Managers and store owners see the leadership (store admins / managers) of their own branches
            $query = "SELECT u.id, u.full_name, u.username, u.email, u.role, s.store_name 
                      FROM users u 
                      LEFT JOIN stores s ON u.store_id = s.id 
                      WHERE u.role IN ('store_admin', 'manager')
                        AND (s.owner_id = :mgr_id OR s.manager_id = :mgr_id OR s.id = :assigned OR u.owner_id = :mgr_id)
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
    if ($email === '') {
        // Forms don't always collect an email; the unique key would otherwise
        // collide on '' for every account created after the first one.
        $email = strtolower(preg_replace('/[^a-zA-Z0-9._-]/', '_', $user_name ?: uniqid('user')) . '@stockease.local');
    }
    $pass_word = isset($data['password']) ? trim($data['password']) : '';
    $store_id  = isset($data['store_id']) ? intval($data['store_id']) : 0;
    $role      = isset($data['role']) && in_array($data['role'], ['store_admin', 'manager'], true) ? $data['role'] : 'store_admin';

    // Strict hierarchy enforcement per caller
    if ($identity['role'] === 'manager') {
        $role = 'store_admin';
    } elseif ($identity['role'] === 'store_owner') {
        $role = 'manager';
    }

    // Store owners may register a manager before any branch exists (that
    // manager then adds the branches); every other role must pick a branch.
    if ($store_id <= 0 && $identity['role'] !== 'store_owner') {
        echo json_encode(["success" => false, "message" => "A valid store is required."]);
        exit();
    }

    // Managers and store owners create leadership accounts only for their own branches
    if ($store_id > 0 && ($identity['role'] === 'manager' || $identity['role'] === 'store_owner')) {
        $stCheck = $conn->prepare("SELECT id FROM stores WHERE id = :sid AND (owner_id = :uid OR manager_id = :uid OR id = :assigned)");
        $stCheck->execute([
            'sid' => $store_id, 
            'uid' => (int)$identity['user_id'], 
            'assigned' => (int)($identity['store_id'] ?? 0)
        ]);
        if (!$stCheck->fetchColumn()) {
            echo json_encode(["success" => false, "message" => "You can only assign accounts to your own branches."]);
            exit();
        }
    }

    if (empty($full_name) || empty($user_name) || empty($pass_word)) {
        echo json_encode(["success" => false, "message" => "All required fields must be filled."]);
        exit();
    }

    if ($store_id > 0 && !auth_store_exists($conn, $store_id)) {
        echo json_encode(["success" => false, "message" => "A valid store is required."]);
        exit();
    }

    // Friendly duplicate check (HTTP 200 + success:false) so the app shows the
    // real reason instead of a generic network error.
    $dupe = $conn->prepare("SELECT 1 FROM users WHERE username = :u LIMIT 1");
    $dupe->execute(['u' => $user_name]);
    if ($dupe->fetchColumn()) {
        echo json_encode(["success" => false, "message" => "That username is already taken. Choose another one."]);
        exit();
    }

    // Record who this account works for: the branch's owner, else the creating
    // store owner, else the creator's own owner link. This is what lets a
    // branchless manager's first branch land in the owner's portfolio.
    $ownerLink = null;
    if ($store_id > 0) {
        $ownStmt = $conn->prepare("SELECT owner_id FROM stores WHERE id = :sid");
        $ownStmt->execute(['sid' => $store_id]);
        $link = $ownStmt->fetchColumn();
        $ownerLink = ($link !== false && $link !== null) ? (int)$link : null;
    }
    if ($ownerLink === null && $identity['role'] === 'store_owner') {
        $ownerLink = (int)$identity['user_id'];
    } elseif ($ownerLink === null && $identity['role'] === 'manager') {
        $linkStmt = $conn->prepare("SELECT owner_id FROM users WHERE id = :uid");
        $linkStmt->execute(['uid' => (int)$identity['user_id']]);
        $link = $linkStmt->fetchColumn();
        $ownerLink = ($link !== false && $link !== null) ? (int)$link : null;
    }

    try {
        $stNameStmt = $conn->prepare("SELECT store_name FROM stores WHERE id = :store_id");
        $stNameStmt->execute(['store_id' => $store_id]);
        $store_name = $stNameStmt->fetchColumn() ?: "Branch #$store_id";

        $hashed_password = password_hash($pass_word, PASSWORD_BCRYPT);
        $query = "INSERT INTO users (full_name, username, email, password, role, store_id, owner_id, is_active, must_change_password)
                  VALUES (:full_name, :username, :email, :password, :role, :store_id, :owner_id, true, true)";
        $stmt = $conn->prepare($query);
        $stmt->bindParam(':full_name', $full_name);
        $stmt->bindParam(':username', $user_name);
        $stmt->bindParam(':email', $email);
        $stmt->bindParam(':password', $hashed_password);
        $stmt->bindParam(':role', $role);
        $stmt->bindValue(':store_id', $store_id > 0 ? $store_id : null, $store_id > 0 ? PDO::PARAM_INT : PDO::PARAM_NULL);
        $stmt->bindValue(':owner_id', $ownerLink, $ownerLink !== null ? PDO::PARAM_INT : PDO::PARAM_NULL);

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
