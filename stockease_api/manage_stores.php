<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: GET, POST");

require_once 'db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin', 'store_admin', 'manager']);

$action = isset($_GET['action']) ? $_GET['action'] : '';

// 1. Fetch Stores
if ($action === 'fetch') {
    try {
        if ($identity['role'] === 'store_admin' || $identity['role'] === 'manager') {
            $assignedStoreId = (int)($identity['store_id'] ?? 0);
            $userId = (int)($identity['user_id'] ?? 0);
            $ownerStmt = $conn->prepare("SELECT owner_id FROM stores WHERE id = :store_id");
            $ownerStmt->execute(['store_id' => $assignedStoreId]);
            $ownerId = (int)$ownerStmt->fetchColumn();

            if ($ownerId <= 0) {
                $ownerId = $userId;
            }

            $query = "SELECT s.id, s.store_name, s.location, COALESCE(s.description, '') AS description, s.status, s.owner_id, s.manager_id,
                             ow.full_name as owner_name, ow.username as owner_username,
                             mgr.full_name as manager_name, mgr.username as manager_username
                      FROM stores s
                      LEFT JOIN users ow ON s.owner_id = ow.id
                      LEFT JOIN users mgr ON s.manager_id = mgr.id
                      WHERE (s.owner_id = :owner_id OR s.owner_id = :user_id OR s.manager_id = :user_id OR s.id = :store_id) AND s.status ILIKE 'active'
                      ORDER BY s.id ASC";
            $stmt = $conn->prepare($query);
            $stmt->execute(['owner_id' => $ownerId, 'user_id' => $userId, 'store_id' => $assignedStoreId]);
            $stores = $stmt->fetchAll(PDO::FETCH_ASSOC);
        } else {
            $query = "SELECT s.id, s.store_name, s.location, COALESCE(s.description, '') AS description, s.status, s.owner_id, s.manager_id,
                             COUNT(u.id) as admin_count,
                             ow.full_name as owner_name, ow.username as owner_username,
                             mgr.full_name as manager_name, mgr.username as manager_username
                      FROM stores s 
                      LEFT JOIN users u ON s.id = u.store_id AND u.role = 'store_admin'
                      LEFT JOIN users ow ON s.owner_id = ow.id
                      LEFT JOIN users mgr ON s.manager_id = mgr.id
                      GROUP BY s.id, s.store_name, s.location, s.description, s.status, s.owner_id, s.manager_id, ow.full_name, ow.username, mgr.full_name, mgr.username
                      ORDER BY s.id ASC";
            $stmt = $conn->prepare($query);
            $stmt->execute();
            $stores = $stmt->fetchAll(PDO::FETCH_ASSOC);
        }

        echo json_encode(["success" => true, "stores" => $stores]);
    } catch (PDOException $e) {
        http_response_code(500);
        echo json_encode(["success" => false, "message" => "Unable to load stores: " . $e->getMessage()]);
    }
    exit();
}

// 2. Add New Store (Super admin or Manager)
if ($action === 'add') {
    auth_require_roles($identity, ['super_admin', 'manager']);

    $data = json_decode(file_get_contents("php://input"), true);
    
    $store_name = isset($data['store_name']) ? trim($data['store_name']) : '';
    $location = isset($data['location']) ? trim($data['location']) : '';
    $description = isset($data['description']) ? trim($data['description']) : '';
    $owner_id = null;
    $manager_id = null;

    if (empty($store_name)) {
        echo json_encode(["success" => false, "message" => "Store name is required."]);
        exit();
    }

    try {
        $conn->beginTransaction();

        // Handle Store Owner and Manager
        if ($identity['role'] === 'manager') {
            $manager_id = (int)$identity['user_id'];
            $findOwner = $conn->prepare("SELECT owner_id FROM stores WHERE (manager_id = :mid OR id = :sid) AND owner_id IS NOT NULL LIMIT 1");
            $findOwner->execute(['mid' => $manager_id, 'sid' => (int)($identity['store_id'] ?? 0)]);
            $existingOwner = $findOwner->fetchColumn();
            $owner_id = $existingOwner ? (int)$existingOwner : $manager_id;
        } elseif (!empty($data['owner_id'])) {
            $owner_id = (int)$data['owner_id'];
        } elseif (!empty($data['new_owner_name']) && !empty($data['new_owner_username'])) {
            // Create brand new Store Owner user
            $check = $conn->prepare("SELECT id FROM users WHERE username = :u");
            $check->execute(['u' => trim($data['new_owner_username'])]);
            if ($check->fetch()) {
                $conn->rollBack();
                echo json_encode(["success" => false, "message" => "Owner username already exists."]);
                exit();
            }

            $pwdHash = password_hash($data['new_owner_password'] ?? 'admin123', PASSWORD_BCRYPT);
            $ownerRole = !empty($data['owner_role']) ? $data['owner_role'] : 'manager';
            $insOwner = $conn->prepare("
                INSERT INTO users (full_name, username, email, password, role, is_active, must_change_password)
                VALUES (:fn, :un, :em, :pwd, :ro, true, true) RETURNING id
            ");
            $insOwner->execute([
                'fn' => trim($data['new_owner_name']),
                'un' => trim($data['new_owner_username']),
                'em' => !empty($data['new_owner_email']) ? trim($data['new_owner_email']) : null,
                'pwd' => $pwdHash,
                'ro' => $ownerRole
            ]);
            $owner_id = (int)$insOwner->fetchColumn();
        }

        // Handle Store Manager
        if (!empty($data['manager_id'])) {
            $manager_id = (int)$data['manager_id'];
        } elseif (!empty($data['new_manager_name']) && !empty($data['new_manager_username'])) {
            // Create brand new Manager user
            $check = $conn->prepare("SELECT id FROM users WHERE username = :u");
            $check->execute(['u' => trim($data['new_manager_username'])]);
            if ($check->fetch()) {
                $conn->rollBack();
                echo json_encode(["success" => false, "message" => "Manager username already exists."]);
                exit();
            }

            $pwdHash = password_hash($data['new_manager_password'] ?? 'admin123', PASSWORD_BCRYPT);
            $insMgr = $conn->prepare("
                INSERT INTO users (full_name, username, email, password, role, is_active, must_change_password)
                VALUES (:fn, :un, :em, :pwd, 'manager', true, true) RETURNING id
            ");
            $insMgr->execute([
                'fn' => trim($data['new_manager_name']),
                'un' => trim($data['new_manager_username']),
                'em' => !empty($data['new_manager_email']) ? trim($data['new_manager_email']) : null,
                'pwd' => $pwdHash
            ]);
            $manager_id = (int)$insMgr->fetchColumn();
        }

        $query = "INSERT INTO stores (store_name, location, description, status, owner_id, manager_id)
                  VALUES (:store_name, :location, :description, 'Active', :owner_id, :manager_id) RETURNING id";
        $stmt = $conn->prepare($query);
        $stmt->bindParam(':store_name', $store_name);
        $stmt->bindParam(':location', $location);
        $stmt->bindParam(':description', $description);
        $stmt->bindValue(':owner_id', $owner_id, $owner_id !== null ? PDO::PARAM_INT : PDO::PARAM_NULL);
        $stmt->bindValue(':manager_id', $manager_id, $manager_id !== null ? PDO::PARAM_INT : PDO::PARAM_NULL);
        $stmt->execute();
        $createdStoreId = (int)$stmt->fetchColumn();

        // Assign store_id to owner / manager if they didn't have one
        if ($owner_id) {
            $conn->prepare("UPDATE users SET store_id = COALESCE(store_id, :sid) WHERE id = :uid")->execute(['sid' => $createdStoreId, 'uid' => $owner_id]);
        }
        if ($manager_id) {
            $conn->prepare("UPDATE users SET store_id = COALESCE(store_id, :sid) WHERE id = :uid")->execute(['sid' => $createdStoreId, 'uid' => $manager_id]);
        }

        $conn->commit();
        echo json_encode(["success" => true, "message" => "Store registered successfully.", "store_id" => $createdStoreId]);
    } catch (PDOException $e) {
        if ($conn->inTransaction()) {
            $conn->rollBack();
        }
        http_response_code(500);
        echo json_encode(["success" => false, "message" => "Unable to add store: " . $e->getMessage()]);
    }
    exit();
}

// 3. Update Store Description (Super admin or Manager)
if ($action === 'update_description') {
    auth_require_roles($identity, ['super_admin', 'manager', 'store_admin']);

    $data = json_decode(file_get_contents("php://input"), true);
    $store_id = isset($data['store_id']) ? (int)$data['store_id'] : 0;
    $description = isset($data['description']) ? trim($data['description']) : '';

    if ($store_id <= 0) {
        echo json_encode(["success" => false, "message" => "Valid store ID is required."]);
        exit();
    }

    try {
        $stmt = $conn->prepare("UPDATE stores SET description = :description WHERE id = :store_id");
        $stmt->execute(['description' => $description, 'store_id' => $store_id]);
        echo json_encode(["success" => true, "message" => "Store description updated successfully."]);
    } catch (PDOException $e) {
        http_response_code(500);
        echo json_encode(["success" => false, "message" => "Unable to update store description: " . $e->getMessage()]);
    }
    exit();
}

// 4. Assign / Update Store Owner (Super admin only)
if ($action === 'assign_owner') {
    auth_require_roles($identity, ['super_admin']);

    $data = json_decode(file_get_contents("php://input"), true);
    $store_id = isset($data['store_id']) ? (int)$data['store_id'] : 0;
    $owner_id = (!empty($data['owner_id'])) ? (int)$data['owner_id'] : null;

    if ($store_id <= 0) {
        echo json_encode(["success" => false, "message" => "Valid store ID is required."]);
        exit();
    }

    try {
        $stmt = $conn->prepare("UPDATE stores SET owner_id = :owner_id WHERE id = :store_id");
        $stmt->bindValue(':owner_id', $owner_id, $owner_id !== null ? PDO::PARAM_INT : PDO::PARAM_NULL);
        $stmt->bindValue(':store_id', $store_id, PDO::PARAM_INT);
        $stmt->execute();
        echo json_encode(["success" => true, "message" => "Store owner updated successfully."]);
    } catch (PDOException $e) {
        http_response_code(500);
        echo json_encode(["success" => false, "message" => "Unable to update store owner."]);
    }
    exit();
}

// 5. Archive Store (Super admin)
if ($action === 'archive') {
    auth_require_roles($identity, ['super_admin']);

    $data = json_decode(file_get_contents("php://input"), true);
    $store_id = isset($data['store_id']) ? (int)$data['store_id'] : 0;
    $status = !empty($data['status']) ? trim($data['status']) : 'Archived';

    if ($store_id <= 0) {
        echo json_encode(["success" => false, "message" => "Valid store ID is required."]);
        exit();
    }

    try {
        $stmt = $conn->prepare("UPDATE stores SET status = :status WHERE id = :store_id");
        $stmt->execute(['status' => $status, 'store_id' => $store_id]);
        echo json_encode(["success" => true, "message" => "Store has been " . strtolower($status) . "."]);
    } catch (PDOException $e) {
        http_response_code(500);
        echo json_encode(["success" => false, "message" => "Unable to archive store: " . $e->getMessage()]);
    }
    exit();
}

// 6. Delete Store (Super admin)
if ($action === 'delete') {
    auth_require_roles($identity, ['super_admin']);

    $data = json_decode(file_get_contents("php://input"), true);
    $store_id = isset($data['store_id']) ? (int)$data['store_id'] : 0;

    if ($store_id <= 0) {
        echo json_encode(["success" => false, "message" => "Valid store ID is required."]);
        exit();
    }

    try {
        $conn->beginTransaction();
        $conn->prepare("UPDATE users SET store_id = NULL WHERE store_id = :sid")->execute(['sid' => $store_id]);
        $conn->prepare("DELETE FROM stock_transfers WHERE from_store_id = :sid OR to_store_id = :sid")->execute(['sid' => $store_id]);
        $conn->prepare("DELETE FROM audit_logs WHERE store_id = :sid")->execute(['sid' => $store_id]);
        $conn->prepare("DELETE FROM transactions WHERE store_id = :sid")->execute(['sid' => $store_id]);
        $conn->prepare("DELETE FROM products WHERE store_id = :sid")->execute(['sid' => $store_id]);
        $conn->prepare("DELETE FROM stores WHERE id = :sid")->execute(['sid' => $store_id]);
        $conn->commit();
        echo json_encode(["success" => true, "message" => "Store and its records were permanently deleted."]);
    } catch (PDOException $e) {
        if ($conn->inTransaction()) {
            $conn->rollBack();
        }
        http_response_code(500);
        echo json_encode(["success" => false, "message" => "Unable to delete store: " . $e->getMessage()]);
    }
    exit();
}

echo json_encode(["success" => false, "message" => "Invalid action parameter."]);

