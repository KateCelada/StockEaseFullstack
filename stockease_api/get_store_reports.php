<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/subscription_helper.php';

$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin', 'store_owner', 'manager', 'store_admin']);
$isPremium = subscription_is_premium($conn, $identity);

$requestedStoreId = isset($_GET['store_id']) ? (int)$_GET['store_id'] : 0;
$storeId = auth_resolve_store_context($conn, $identity, $requestedStoreId, true);

$startDate = $_GET['start_date'] ?? null;
$endDate = $_GET['end_date'] ?? null;

// Validate date formats if provided (YYYY-MM-DD)
$dateRegex = '/^\d{4}-\d{2}-\d{2}$/';
if ($startDate && !preg_match($dateRegex, $startDate)) {
    $startDate = null;
}
if ($endDate && !preg_match($dateRegex, $endDate)) {
    $endDate = null;
}

try {
    // Base WHERE conditions for transactions
    $whereClauses = ["t.store_id = :store_id", "(t.status IS NULL OR t.status != 'voided')"];
    $params = ['store_id' => $storeId];

    if ($startDate && $endDate) {
        $whereClauses[] = "t.created_at >= :start_date AND t.created_at <= :end_date";
        $params['start_date'] = $startDate . " 00:00:00";
        $params['end_date'] = $endDate . " 23:59:59";
    } elseif ($startDate) {
        $whereClauses[] = "t.created_at >= :start_date";
        $params['start_date'] = $startDate . " 00:00:00";
    }

    $whereSql = implode(" AND ", $whereClauses);

    // 1. Overall Summary (Sales, COGS, Net Profit)
    $summaryStmt = $conn->prepare("
        SELECT 
            COALESCE(COUNT(t.id), 0) AS transaction_count,
            COALESCE(SUM(t.total_amount), 0.0) AS total_sales,
            COALESCE(AVG(t.total_amount), 0.0) AS avg_transaction_value
        FROM transactions t
        WHERE $whereSql
    ");
    $summaryStmt->execute($params);
    $summaryRow = $summaryStmt->fetch(PDO::FETCH_ASSOC);

    $transactionCount = (int)($summaryRow['transaction_count'] ?? 0);
    $totalSales = (float)($summaryRow['total_sales'] ?? 0.0);
    $avgValue = (float)($summaryRow['avg_transaction_value'] ?? 0.0);

    // Total products sold and Total COGS (Cost of Goods Sold)
    $itemsStmt = $conn->prepare("
        SELECT 
            COALESCE(SUM(ti.quantity), 0) AS total_products_sold,
            COALESCE(SUM(ti.quantity * COALESCE(ti.cost_price, p.cost_price, 0)), 0.0) AS total_cogs
        FROM transaction_items ti
        JOIN transactions t ON ti.transaction_id = t.id
        LEFT JOIN products p ON ti.product_id = p.id
        WHERE $whereSql
    ");
    $itemsStmt->execute($params);
    $itemsRow = $itemsStmt->fetch(PDO::FETCH_ASSOC);
    $totalProductsSold = (int)($itemsRow['total_products_sold'] ?? 0);
    $totalCogs = (float)($itemsRow['total_cogs'] ?? 0.0);
    $netProfit = $totalSales - $totalCogs;
    $profitMargin = $totalSales > 0 ? round(($netProfit / $totalSales) * 100, 2) : 0.0;

    // 2. Payment Method Breakdown
    $paymentStmt = $conn->prepare("
        SELECT 
            COALESCE(t.payment_type, 'Unknown') AS payment_type,
            COALESCE(SUM(t.total_amount), 0.0) AS total_amount,
            COUNT(t.id) AS count
        FROM transactions t
        WHERE $whereSql
        GROUP BY t.payment_type
        ORDER BY total_amount DESC
    ");
    $paymentStmt->execute($params);
    $paymentMethods = $paymentStmt->fetchAll(PDO::FETCH_ASSOC);

    // 3. Top Selling Products
    $topProductsStmt = $conn->prepare("
        SELECT 
            p.id AS product_id,
            COALESCE(p.product_name, 'Unknown Product') AS product_name,
            COALESCE(SUM(ti.quantity), 0) AS total_sold,
            COALESCE(SUM(ti.quantity * p.price), 0.0) AS total_revenue
        FROM transaction_items ti
        JOIN products p ON ti.product_id = p.id
        JOIN transactions t ON ti.transaction_id = t.id
        WHERE $whereSql
        GROUP BY p.id, p.product_name
        ORDER BY total_sold DESC
        LIMIT 5
    ");
    $topProductsStmt->execute($params);
    $topProducts = $topProductsStmt->fetchAll(PDO::FETCH_ASSOC);

    // 4. Daily Sales Breakdown
    $dailyStmt = $conn->prepare("
        SELECT 
            TO_CHAR(t.created_at, 'YYYY-MM-DD') AS sale_date,
            COALESCE(SUM(t.total_amount), 0.0) AS daily_sales,
            COUNT(t.id) AS transaction_count
        FROM transactions t
        WHERE $whereSql
        GROUP BY TO_CHAR(t.created_at, 'YYYY-MM-DD')
        ORDER BY sale_date DESC
        LIMIT 7
    ");
    $dailyStmt->execute($params);
    $dailySales = $dailyStmt->fetchAll(PDO::FETCH_ASSOC);

    // Get Store Name
    $storeStmt = $conn->prepare("SELECT store_name FROM stores WHERE id = :id");
    $storeStmt->execute(['id' => $storeId]);
    $storeName = $storeStmt->fetchColumn() ?: "Store #$storeId";

    $transferMetrics = null;
    if ($isPremium) {
        $transStmt = $conn->prepare("
            SELECT 
                COUNT(*) FILTER (WHERE status = 'completed') AS completed_transfers,
                COUNT(*) FILTER (WHERE status = 'in_transit') AS in_transit_transfers
            FROM stock_transfers
            WHERE from_store_id = :sid OR to_store_id = :sid
        ");
        $transStmt->execute(['sid' => $storeId]);
        $transferMetrics = $transStmt->fetch(PDO::FETCH_ASSOC);
    }

    echo json_encode([
        "success" => true,
        "is_premium" => $isPremium,
        "store_id" => $storeId,
        "store_name" => $storeName,
        "date_range" => [
            "start_date" => $startDate,
            "end_date" => $endDate
        ],
        "summary" => [
            "total_sales" => round($totalSales, 2),
            "total_cogs" => round($totalCogs, 2),
            "net_profit" => round($netProfit, 2),
            "profit_margin_pct" => $profitMargin,
            "transaction_count" => $transactionCount,
            "average_transaction" => round($avgValue, 2),
            "products_sold" => $totalProductsSold
        ],
        "transfer_metrics" => $transferMetrics,
        "payment_methods" => $paymentMethods,
        "top_products" => $topProducts,
        "daily_sales" => $dailySales
    ]);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode([
        "success" => false,
        "message" => "Unable to load store reports: " . $e->getMessage()
    ]);
}
?>
