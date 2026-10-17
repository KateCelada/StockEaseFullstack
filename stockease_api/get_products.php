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

// --- Table controls -------------------------------------------------------
$search = isset($_GET['search']) ? trim((string)$_GET['search']) : '';
$categoryName = isset($_GET['category']) ? trim((string)$_GET['category']) : '';
$categoryId = isset($_GET['category_id']) ? (int)$_GET['category_id'] : 0;
$lowStockOnly = isset($_GET['low_stock']) && in_array(strtolower((string)$_GET['low_stock']), ['1', 'true', 'yes'], true);
$paginate = isset($_GET['page']) && $_GET['page'] !== '';
$page = $paginate ? max(1, (int)$_GET['page']) : 1;
$perPage = isset($_GET['per_page']) ? (int)$_GET['per_page'] : 10;
$perPage = max(1, min(100, $perPage));

$lowStockExpr = "p.stock <= COALESCE(p.min_stock, 10)";

$select = "SELECT p.id,
                  p.store_id,
                  p.product_name,
                  COALESCE(c.name, p.category, 'General') AS category,
                  p.category_id,
                  p.price,
                  COALESCE(p.cost_price, 0.00) AS cost_price,
                  p.stock,
                  COALESCE(p.min_stock, 10) AS min_stock,
                  CASE WHEN $lowStockExpr THEN TRUE ELSE FALSE END AS is_low_stock,
                  p.created_at,
                  COALESCE(SUM(CASE WHEN (t.status IS NULL OR t.status != 'voided') THEN ti.quantity ELSE 0 END), 0) AS total_sold_units,
                  COALESCE(SUM(CASE WHEN (t.status IS NULL OR t.status != 'voided') THEN ti.quantity * ti.unit_price ELSE 0 END), 0.0) AS progressive_gross,
                  COALESCE(SUM(CASE WHEN (t.status IS NULL OR t.status != 'voided') THEN ti.quantity * (ti.unit_price - COALESCE(ti.cost_price, p.cost_price, 0.0)) ELSE 0 END), 0.0) AS progressive_net";

$from = "FROM products p
         LEFT JOIN categories c ON p.category_id = c.id
         LEFT JOIN transaction_items ti ON ti.product_id = p.id
         LEFT JOIN transactions t ON ti.transaction_id = t.id
         WHERE p.store_id = :store_id";

$params = ['store_id' => $storeId];

if ($search !== '') {
    $from .= " AND (p.product_name ILIKE :search OR COALESCE(c.name, p.category, 'General') ILIKE :search)";
    $params['search'] = "%$search%";
}
if ($categoryId > 0) {
    $from .= " AND p.category_id = :category_id";
    $params['category_id'] = $categoryId;
} elseif ($categoryName !== '' && strtolower($categoryName) !== 'all categories') {
    $from .= " AND LOWER(COALESCE(c.name, p.category, 'General')) = LOWER(:category_name)";
    $params['category_name'] = $categoryName;
}
if ($lowStockOnly) {
    $from .= " AND $lowStockExpr";
}

try {
    $total = 0;
    $totalPages = 0;

    if ($paginate) {
        $countStmt = $conn->prepare("SELECT COUNT(DISTINCT p.id) $from");
        $countStmt->execute($params);
        $total = (int)$countStmt->fetchColumn();
        $totalPages = max(1, (int)ceil($total / $perPage));
        if ($page > $totalPages) {
            $page = $totalPages;
        }
    }

    $listSql = "$select $from
                GROUP BY p.id, c.name
                ORDER BY p.product_name ASC";

    if ($paginate) {
        $listSql .= " LIMIT :limit OFFSET :offset";
    }

    $stmt = $conn->prepare($listSql);
    foreach ($params as $key => $value) {
        $stmt->bindValue($key, $value);
    }
    if ($paginate) {
        $stmt->bindValue('limit', $perPage, PDO::PARAM_INT);
        $stmt->bindValue('offset', ($page - 1) * $perPage, PDO::PARAM_INT);
    }
    $stmt->execute();
    $products = $stmt->fetchAll(PDO::FETCH_ASSOC);

    $payload = [
        "success" => true,
        "is_premium" => $isPremium,
        "free_limit" => FREE_PRODUCT_LIMIT,
        "product_count" => count($products),
        "products" => $products
    ];

    if ($paginate) {
        $payload["pagination"] = [
            "page" => $page,
            "per_page" => $perPage,
            "total" => $total,
            "total_pages" => $totalPages
        ];
    }

    echo json_encode($payload);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Unable to load products", "products" => []]);
}
?>
