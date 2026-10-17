<?php
header("Access-Control-Allow-Origin: *");
header("Content-Control-Allow-Credentials: true");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: GET, POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);

$action = $_GET['action'] ?? 'list';

// 1. List businesses visible to the caller
if ($action === 'list') {
    auth_require_roles($identity, ['super_admin', 'store_owner', 'manager', 'store_admin']);

    try {
        if ($identity['role'] === 'super_admin') {
            $stmt = $conn->query("
                SELECT b.id, b.business_name, b.owner_id, ow.full_name AS owner_name,
                       COUNT(s.id) FILTER (WHERE s.status ILIKE 'active') AS branch_count
                FROM businesses b
                LEFT JOIN users ow ON b.owner_id = ow.id
                LEFT JOIN stores s ON s.business_id = b.id
                GROUP BY b.id, b.business_name, b.owner_id, ow.full_name
                ORDER BY b.id ASC
            ");
        } elseif ($identity['role'] === 'store_owner') {
            $stmt = $conn->prepare("
                SELECT b.id, b.business_name, b.owner_id, ow.full_name AS owner_name,
                       COUNT(s.id) FILTER (WHERE s.status ILIKE 'active') AS branch_count
                FROM businesses b
                LEFT JOIN users ow ON b.owner_id = ow.id
                LEFT JOIN stores s ON s.business_id = b.id
                WHERE b.owner_id = :uid
                GROUP BY b.id, b.business_name, b.owner_id, ow.full_name
                ORDER BY b.id ASC
            ");
            $stmt->execute(['uid' => (int)$identity['user_id']]);
        } else {
            // Managers / store admins see the businesses of the branches they belong to
            $stmt = $conn->prepare("
                SELECT DISTINCT b.id, b.business_name, b.owner_id, ow.full_name AS owner_name, 0 AS branch_count
                FROM businesses b
                LEFT JOIN users ow ON b.owner_id = ow.id
                JOIN stores s ON s.business_id = b.id
                WHERE s.owner_id = :uid OR s.manager_id = :uid OR s.id = :sid
                ORDER BY b.id ASC
            ");
            $stmt->execute(['uid' => (int)$identity['user_id'], 'sid' => (int)($identity['store_id'] ?? 0)]);
        }

        echo json_encode(['success' => true, 'businesses' => $stmt->fetchAll(PDO::FETCH_ASSOC)]);
    } catch (PDOException $e) {
        http_response_code(500);
        echo json_encode(['success' => false, 'message' => 'Unable to load businesses']);
    }
    exit();
}

// 2. Add a business (System Super Admin only)
if ($action === 'add') {
    auth_require_roles($identity, ['super_admin']);

    $data = json_decode(file_get_contents('php://input'), true);
    if (!is_array($data)) {
        $data = $_POST;
    }

    $name = trim($data['business_name'] ?? '');
    $ownerId = !empty($data['owner_id']) ? (int)$data['owner_id'] : null;

    // Optional: create the store owner account together with the business
    $newOwnerName = trim($data['new_owner_name'] ?? '');
    $newOwnerUsername = trim($data['new_owner_username'] ?? '');
    $newOwnerEmail = trim($data['new_owner_email'] ?? '');
    $newOwnerPassword = $data['new_owner_password'] ?? '';
    $createNewOwner = ($newOwnerName !== '' && $newOwnerUsername !== '');

    if ($name === '') {
        echo json_encode(['success' => false, 'message' => 'Business name is required.']);
        exit();
    }

    if (!$createNewOwner && ($newOwnerName !== '' || $newOwnerUsername !== '')) {
        echo json_encode(['success' => false, 'message' => 'Provide both the owner full name and username to create a new store owner account.']);
        exit();
    }

    try {
        if ($createNewOwner) {
            $check = $conn->prepare("SELECT id FROM users WHERE username = :u");
            $check->execute(['u' => $newOwnerUsername]);
            if ($check->fetch()) {
                echo json_encode(['success' => false, 'message' => 'Owner username already exists.']);
                exit();
            }

            // Owner account + business are created as one unit so a failure cannot
            // leave a store owner behind without their business.
            $conn->beginTransaction();

            $pwdHash = password_hash($newOwnerPassword !== '' ? $newOwnerPassword : 'admin123', PASSWORD_BCRYPT);
            $insOwner = $conn->prepare("
                INSERT INTO users (full_name, username, email, password, role, is_active, must_change_password)
                VALUES (:fn, :un, :em, :pwd, 'store_owner', true, true) RETURNING id
            ");
            $insOwner->execute([
                'fn' => $newOwnerName,
                'un' => $newOwnerUsername,
                'em' => $newOwnerEmail !== '' ? $newOwnerEmail : null,
                'pwd' => $pwdHash
            ]);
            $ownerId = (int)$insOwner->fetchColumn();
        }

        if ($ownerId !== null) {
            $ownerStmt = $conn->prepare("SELECT id FROM users WHERE id = :id AND role IN ('store_owner', 'manager')");
            $ownerStmt->execute(['id' => $ownerId]);
            if (!$ownerStmt->fetchColumn()) {
                if ($conn->inTransaction()) {
                    $conn->rollBack();
                }
                echo json_encode(['success' => false, 'message' => 'The selected owner account does not exist.']);
                exit();
            }
        }

        $stmt = $conn->prepare("INSERT INTO businesses (business_name, owner_id) VALUES (:bn, :oid) RETURNING id");
        $stmt->execute(['bn' => $name, 'oid' => $ownerId]);
        $businessId = (int)$stmt->fetchColumn();

        if ($conn->inTransaction()) {
            $conn->commit();
        }

        $message = $createNewOwner
            ? 'Business registered and store owner account created.'
            : 'Business registered successfully.';
        echo json_encode(['success' => true, 'message' => $message, 'business_id' => $businessId]);
    } catch (PDOException $e) {
        if ($conn->inTransaction()) {
            $conn->rollBack();
        }
        http_response_code(500);
        echo json_encode(['success' => false, 'message' => 'Unable to register business.']);
    }
    exit();
}

// 5. Rename a business (super admin only)
if ($action === 'update') {
    auth_require_roles($identity, ['super_admin']);

    $data = json_decode(file_get_contents('php://input'), true);
    if (!is_array($data)) {
        $data = $_POST;
    }

    $businessId = !empty($data['business_id']) ? (int)$data['business_id'] : 0;
    $name = trim($data['business_name'] ?? '');

    if ($businessId <= 0) {
        echo json_encode(['success' => false, 'message' => 'Valid business ID is required.']);
        exit();
    }

    if ($name === '') {
        echo json_encode(['success' => false, 'message' => 'Business name is required.']);
        exit();
    }

    try {
        $check = $conn->prepare("SELECT id FROM businesses WHERE id = :id");
        $check->execute(['id' => $businessId]);
        if (!$check->fetchColumn()) {
            echo json_encode(['success' => false, 'message' => 'Business not found.']);
            exit();
        }

        $stmt = $conn->prepare("UPDATE businesses SET business_name = :bn WHERE id = :id");
        $stmt->execute(['bn' => $name, 'id' => $businessId]);

        echo json_encode(['success' => true, 'message' => 'Business name updated successfully.']);
    } catch (PDOException $e) {
        http_response_code(500);
        echo json_encode(['success' => false, 'message' => 'Unable to update business.']);
    }
    exit();
}

echo json_encode(['success' => false, 'message' => 'Invalid action.']);
