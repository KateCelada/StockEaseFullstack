<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$requestedStoreId = isset($_GET['store_id']) ? (int)$_GET['store_id'] : 0;
$categoryId = isset($_GET['category_id']) ? (int)$_GET['category_id'] : 0;
$identity = auth_require_user($conn);
auth_require_roles($identity, ['store_admin']);
$storeId = auth_resolve_store_context($conn, $identity, $requestedStoreId, false);

try {
    $sql = "SELECT p.id,
                   p.store_id,
                   p.product_name,
                   COALESCE(c.name, p.category, 'General') AS category,
                   p.category_id,
                   p.price,
                   p.stock,
                   p.created_at
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            WHERE p.store_id = :store_id";
    $params = ['store_id' => $storeId];

    if ($categoryId > 0) {
        $sql .= " AND p.category_id = :category_id";
        $params['category_id'] = $categoryId;
    } else {
        $sql .= " AND p.category_id IS NULL";
    }

    $sql .= " ORDER BY p.product_name ASC";
    $stmt = $conn->prepare($sql);
    $stmt->execute($params);

    echo json_encode($stmt->fetchAll(PDO::FETCH_ASSOC));
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode([]);
}
?>
