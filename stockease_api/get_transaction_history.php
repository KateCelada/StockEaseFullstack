<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$requestedStoreId = isset($_GET['store_id']) ? (int)$_GET['store_id'] : 0;
$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin', 'store_admin', 'staff']);
$storeId = auth_resolve_store_context($conn, $identity, $requestedStoreId, true);

try {
    $stmt = $conn->prepare("SELECT t.id AS transaction_id,
                                  t.total_amount,
                                  t.payment_type,
                                  COALESCE(t.status, 'completed') AS status,
                                  t.created_at,
                                  COALESCE(u.full_name, 'Unknown user') AS user_name,
                                  STRING_AGG(ti.quantity || 'x ' || p.product_name, E'\\n') AS items_summary
                           FROM transactions t
                           JOIN users u ON t.user_id = u.id
                           LEFT JOIN transaction_items ti ON t.id = ti.transaction_id
                           LEFT JOIN products p ON ti.product_id = p.id
                           WHERE t.store_id = :store_id
                           GROUP BY t.id, u.full_name, t.status
                           ORDER BY t.created_at DESC");
    $stmt->execute(['store_id' => $storeId]);

    echo json_encode(["success" => true, "transactions" => $stmt->fetchAll(PDO::FETCH_ASSOC)]);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Unable to load transaction history", "transactions" => []]);
}
?>
