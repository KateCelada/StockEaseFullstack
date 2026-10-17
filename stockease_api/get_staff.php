<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
require_once "db.php";
require_once __DIR__ . '/auth.php';

$requestedStoreId = isset($_GET['store_id']) ? (int)$_GET['store_id'] : 0;
$identity = auth_require_user($conn);
auth_require_roles($identity, ['store_admin', 'manager', 'super_admin']);
$storeId = auth_resolve_store_context($conn, $identity, $requestedStoreId, true);

try {
    $stmt = $conn->prepare("
        SELECT id, full_name, username, email, role, store_id, is_active, created_at
        FROM users 
        WHERE store_id = :store_id 
          AND (role IN ('store_admin', 'staff', 'inventory_checker', 'manager'))
          AND role != 'super_admin'
        ORDER BY CASE role WHEN 'store_admin' THEN 1 WHEN 'manager' THEN 2 WHEN 'inventory_checker' THEN 3 ELSE 4 END, id ASC
    ");
    $stmt->execute(['store_id' => $storeId]);
    $staff = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode([
        "success" => true,
        "staff" => $staff
    ]);
} catch (PDOException $e) {
    http_response_code(500);
    error_log('get_staff caught exception: ' . $e->getMessage());
    echo json_encode(["success" => false, "message" => "Unable to load staff accounts. Please try again."]);
}
?>

