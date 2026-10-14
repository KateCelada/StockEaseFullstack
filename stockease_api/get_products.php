<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/subscription_helper.php';

$requestedStoreId = isset($_GET['store_id']) ? (int)$_GET['store_id'] : 0;
$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin', 'store_admin', 'manager', 'staff']);
$storeId = auth_resolve_store_context($conn, $identity, $requestedStoreId, true);
$isPremium = subscription_is_premium($conn, $identity);

try {
    $stmt = $conn->prepare("SELECT p.id,
                                  p.store_id,
                                  p.product_name,
                                  COALESCE(c.name, p.category, 'General') AS category,
                                  p.category_id,
                                  p.price,
                                  COALESCE(p.cost_price, 0.00) AS cost_price,
                                  p.stock,
                                  p.created_at,
                                  COALESCE(SUM(CASE WHEN (t.status IS NULL OR t.status != 'voided') THEN ti.quantity ELSE 0 END), 0) AS total_sold_units,
                                  COALESCE(SUM(CASE WHEN (t.status IS NULL OR t.status != 'voided') THEN ti.quantity * ti.unit_price ELSE 0 END), 0.0) AS progressive_gross,
                                  COALESCE(SUM(CASE WHEN (t.status IS NULL OR t.status != 'voided') THEN ti.quantity * (ti.unit_price - COALESCE(ti.cost_price, p.cost_price, 0.0)) ELSE 0 END), 0.0) AS progressive_net
                           FROM products p
                           LEFT JOIN categories c ON p.category_id = c.id
                           LEFT JOIN transaction_items ti ON ti.product_id = p.id
                           LEFT JOIN transactions t ON ti.transaction_id = t.id
                           WHERE p.store_id = :store_id
                           GROUP BY p.id, c.name
                           ORDER BY p.product_name ASC");
    $stmt->execute(['store_id' => $storeId]);
    $products = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode([
        "success" => true,
        "is_premium" => $isPremium,
        "free_limit" => FREE_PRODUCT_LIMIT,
        "product_count" => count($products),
        "products" => $products
    ]);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Unable to load products", "products" => []]);
}
?>
