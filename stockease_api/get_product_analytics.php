<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/series_helper.php';

$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin', 'store_admin', 'staff', 'manager']);

$productId = isset($_GET['product_id']) ? (int)$_GET['product_id'] : 0;
if ($productId <= 0) {
    http_response_code(400);
    echo json_encode(["success" => false, "message" => "product_id is required"]);
    exit;
}

try {
    // 1. Fetch Product
    $stmt = $conn->prepare("
        SELECT p.id, p.store_id, p.product_name, p.price, COALESCE(p.cost_price, 0.0) AS cost_price,
               p.stock, COALESCE(c.name, p.category, 'General') AS category_name
        FROM products p
        LEFT JOIN categories c ON p.category_id = c.id
        WHERE p.id = :product_id
    ");
    $stmt->execute(['product_id' => $productId]);
    $prod = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$prod) {
        http_response_code(404);
        echo json_encode(["success" => false, "message" => "Product not found"]);
        exit;
    }

    // Check store context if not super_admin
    $storeId = (int)$prod['store_id'];
    auth_resolve_store_context($conn, $identity, $storeId, true);

    // 2. Progressive Lifetime Earnings
    $sumStmt = $conn->prepare("
        SELECT 
            COALESCE(SUM(ti.quantity), 0) AS total_sold,
            COALESCE(SUM(ti.quantity * ti.unit_price), 0.0) AS progressive_gross,
            COALESCE(SUM(ti.quantity * (ti.unit_price - COALESCE(ti.cost_price, :cost_price, 0.0))), 0.0) AS progressive_net
        FROM transaction_items ti
        JOIN transactions t ON ti.transaction_id = t.id
        WHERE ti.product_id = :product_id
          AND (t.status IS NULL OR t.status != 'voided')
    ");
    $sumStmt->execute([
        'product_id' => $productId,
        'cost_price' => $prod['cost_price']
    ]);
    $totals = $sumStmt->fetch(PDO::FETCH_ASSOC);

    $totalSold = (int)($totals['total_sold'] ?? 0);
    $progressiveGross = (float)($totals['progressive_gross'] ?? 0.0);
    $progressiveNet = (float)($totals['progressive_net'] ?? 0.0);

    // 3. 7-Day Progressive Earnings Trajectory (Daily Gross & Net)
    // MySQL has no generate_series(), so the 7-day spine is built in PHP.
    $trendStart = date('Y-m-d', strtotime('-6 days'));
    $trendEnd = date('Y-m-d');
    $series = series_dates_sql($trendStart, $trendEnd, 'd');
    $trendStmt = $conn->prepare("
        SELECT 
            s.d AS sale_date,
            DATE_FORMAT(s.d, '%a') AS label,
            COALESCE(SUM(ti.quantity * ti.unit_price), 0.0) AS daily_gross,
            COALESCE(SUM(ti.quantity * (ti.unit_price - COALESCE(ti.cost_price, :cost_price, 0.0))), 0.0) AS daily_net
        FROM $series s
        LEFT JOIN transactions t ON DATE(t.created_at) = s.d
                                AND t.store_id = :store_id
                                AND (t.status IS NULL OR t.status != 'voided')
        LEFT JOIN transaction_items ti ON ti.transaction_id = t.id AND ti.product_id = :product_id
        GROUP BY s.d
        ORDER BY s.d ASC
    ");
    $trendStmt->execute([
        'product_id' => $productId,
        'cost_price' => $prod['cost_price'],
        'store_id' => $storeId
    ]);
    $history = $trendStmt->fetchAll(PDO::FETCH_ASSOC);

    $unitCost = (float)$prod['cost_price'];
    $unitPrice = (float)$prod['price'];
    $unitProfit = $unitPrice - $unitCost;
    $margin = $unitPrice > 0 ? round(($unitProfit / $unitPrice) * 100, 1) : 0.0;

    echo json_encode([
        "success" => true,
        "product_id" => $productId,
        "product_name" => $prod['product_name'],
        "category_name" => $prod['category_name'],
        "stock" => (int)$prod['stock'],
        "unit_cost" => $unitCost,
        "unit_price" => $unitPrice,
        "unit_profit" => $unitProfit,
        "margin_percent" => $margin,
        "total_sold" => $totalSold,
        "progressive_gross" => round($progressiveGross, 2),
        "progressive_net" => round($progressiveNet, 2),
        "earnings_history" => $history
    ]);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Database error: " . $e->getMessage()]);
}
?>
