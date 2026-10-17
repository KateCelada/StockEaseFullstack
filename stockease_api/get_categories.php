<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");

require_once 'db.php';
require_once __DIR__ . '/auth.php';

$requestedStoreId = isset($_GET['store_id']) ? intval($_GET['store_id']) : 0;
$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin', 'store_admin', 'manager', 'staff']);
$store_id = auth_resolve_store_context($conn, $identity, $requestedStoreId, true);

try {
    $query = "SELECT c.id, c.name, COUNT(p.id) as product_count 
              FROM categories c 
              LEFT JOIN products p ON c.id = p.category_id 
              WHERE c.store_id = :store_id 
              GROUP BY c.id, c.name 
              ORDER BY c.name ASC";

    $stmt = $conn->prepare($query);
    $stmt->bindParam(':store_id', $store_id, PDO::PARAM_INT);
    $stmt->execute();

    $categories = $stmt->fetchAll(PDO::FETCH_ASSOC);
    echo json_encode($categories);

} catch (PDOException $e) {
    echo json_encode([]);
}
?>
