<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$data = json_decode(file_get_contents("php://input"), true);

$requestedStoreId = isset($data['store_id']) ? (int)$data['store_id'] : 0;
$identity = auth_require_user($conn);
auth_require_roles($identity, ['store_admin', 'manager', 'super_admin', 'inventory_checker']);
$storeId = auth_resolve_store_context($conn, $identity, $requestedStoreId, true);

$productId = isset($data['product_id']) ? (int)$data['product_id'] : 0;
$addQuantity = isset($data['quantity']) ? (int)$data['quantity'] : 0;

if ($productId <= 0 || $addQuantity <= 0) {
    http_response_code(400);
    echo json_encode([
        "success" => false,
        "message" => "Valid product ID and positive restock quantity are required"
    ]);
    exit;
}

try {
    $stmt = $conn->prepare("UPDATE products 
                           SET stock = stock + :qty 
                           WHERE id = :id AND store_id = :store_id 
                           RETURNING id, product_name, stock");
    $stmt->execute([
        'qty' => $addQuantity,
        'id' => $productId,
        'store_id' => $storeId
    ]);
    $updated = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$updated) {
        http_response_code(404);
        echo json_encode(["success" => false, "message" => "Product not found in this store"]);
        exit;
    }

    echo json_encode([
        "success" => true,
        "message" => "Stock updated successfully",
        "product" => $updated
    ]);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Database error updating stock"]);
}
?>
