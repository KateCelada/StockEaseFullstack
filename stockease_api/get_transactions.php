<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$requestedStoreId = isset($_GET['store_id']) ? (int)$_GET['store_id'] : 0;
$startDate = $_GET['start_date'] ?? null;
$endDate = $_GET['end_date'] ?? null;
$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin', 'store_admin', 'staff']);
$storeId = auth_resolve_store_context($conn, $identity, $requestedStoreId, true);

try {
    $whereClauses = ["t.store_id = :store_id"];
    $params = ['store_id' => $storeId];

    if ($identity['role'] === 'staff') {
        $whereClauses[] = "t.user_id = :user_id";
        $params['user_id'] = $identity['user_id'];
    } elseif (isset($_GET['user_id']) && (int)$_GET['user_id'] > 0) {
        $whereClauses[] = "t.user_id = :user_id";
        $params['user_id'] = (int)$_GET['user_id'];
    }

    if ($startDate && $endDate) {
        $whereClauses[] = "t.created_at >= :start_date AND t.created_at <= :end_date";
        $params['start_date'] = $startDate . " 00:00:00";
        $params['end_date'] = $endDate . " 23:59:59";
    }

    $whereSql = implode(" AND ", $whereClauses);
    $stmt = $conn->prepare("SELECT t.id,
                                  t.total_amount,
                                  t.payment_type,
                                  COALESCE(t.status, 'completed') AS status,
                                  t.created_at AS transaction_date,
                                  COALESCE(u.full_name, 'Unknown user') AS user_name,
                                  COALESCE(u.username, '') AS cashier_username,
                                  COALESCE(u.role, 'staff') AS cashier_role,
                                  STRING_AGG(ti.quantity || 'x ' || p.product_name, E'\\n') AS items_summary
                           FROM transactions t
                           JOIN users u ON t.user_id = u.id
                           LEFT JOIN transaction_items ti ON t.id = ti.transaction_id
                           LEFT JOIN products p ON ti.product_id = p.id
                           WHERE $whereSql
                           GROUP BY t.id, u.full_name, u.username, u.role, t.status
                           ORDER BY t.created_at DESC");
    $stmt->execute($params);

    echo json_encode($stmt->fetchAll(PDO::FETCH_ASSOC));
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Unable to load transactions"]);
}
?>
