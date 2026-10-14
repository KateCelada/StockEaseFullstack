<?php
header("Content-Type: application/json; charset=UTF-8");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$requestedStoreId = isset($_GET['store_id']) ? (int)$_GET['store_id'] : 0;
$identity = auth_require_user($conn);
auth_require_roles($identity, ['store_admin']);
$storeId = auth_resolve_store_context($conn, $identity, $requestedStoreId, false);

try {
    $stmt = $conn->prepare("SELECT p.id,
                                  p.store_id,
                                  p.product_name,
                                  COALESCE(c.name, p.category, 'General') AS category,
                                  p.category_id,
                                  p.price,
                                  p.stock,
                                  p.created_at
                           FROM products p
                           LEFT JOIN categories c ON p.category_id = c.id
                           WHERE p.store_id = :store_id AND p.stock < 10
                           ORDER BY p.stock ASC, p.product_name ASC");
    $stmt->execute(['store_id' => $storeId]);
    $products = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode(["success" => true, "count" => count($products), "products" => $products]);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Unable to load low-stock products", "products" => []]);
}
?>
