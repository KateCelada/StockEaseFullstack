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

    // 4. Sales breakdown that feeds the line chart.
    // A single day is bucketed by hour so "Today" shows real intra-day progress,
    // longer ranges are bucketed by day (no 7-day cap) so "This Week" / "This Month"
    // actually draw the whole selected period.
    $isSingleDay = !empty($startDate) && !empty($endDate) && $startDate === $endDate;
    if ($isSingleDay) {
        $dailyStmt = $conn->prepare("
            SELECT 
                DATE_FORMAT(t.created_at, '%H:00') AS sale_date,
                COALESCE(SUM(t.total_amount), 0.0) AS daily_sales,
                COUNT(t.id) AS transaction_count
            FROM transactions t
            WHERE $whereSql
            GROUP BY DATE_FORMAT(t.created_at, '%H:00')
            ORDER BY sale_date DESC
        ");
    } else {
        $dailyStmt = $conn->prepare("
            SELECT 
                DATE(t.created_at) AS sale_date,
                COALESCE(SUM(t.total_amount), 0.0) AS daily_sales,
                COUNT(t.id) AS transaction_count
            FROM transactions t
            WHERE $whereSql
            GROUP BY DATE(t.created_at)
            ORDER BY sale_date DESC
            LIMIT 92
        ");
    }
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
                SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) AS completed_transfers,
                SUM(CASE WHEN status = 'in_transit' THEN 1 ELSE 0 END) AS in_transit_transfers
            FROM stock_transfers
            WHERE from_store_id = :sid OR to_store_id = :sid
        ");
        $transStmt->execute(['sid' => $storeId]);
        $transferMetrics = $transStmt->fetch(PDO::FETCH_ASSOC);
    }

    // 5. "Other Sales" - sales recorded outside the POS (source = 'manual'),
    //    plus voided/refunded transactions that the main summary excludes.
    $rangeSql = "";
    if ($startDate && $endDate) {
        $rangeSql = " AND t.created_at >= :start_date AND t.created_at <= :end_date";
    } elseif ($startDate) {
        $rangeSql = " AND t.created_at >= :start_date";
    }

    $otherStmt = $conn->prepare("
        SELECT
            COALESCE(SUM(CASE WHEN t.source = 'manual' AND (t.status IS NULL OR t.status = 'completed') THEN t.total_amount END), 0.0) AS manual_sales_total,
            COALESCE(SUM(CASE WHEN t.source = 'manual' AND (t.status IS NULL OR t.status = 'completed') THEN 1 ELSE 0 END), 0) AS manual_sales_count,
            COALESCE(SUM(CASE WHEN LOWER(t.status) = 'voided' THEN t.total_amount END), 0.0) AS voided_total,
            COALESCE(SUM(CASE WHEN LOWER(t.status) = 'voided' THEN 1 ELSE 0 END), 0) AS voided_count
        FROM transactions t
        WHERE t.store_id = :store_id$rangeSql
    ");
    $otherStmt->execute($params);
    $otherRow = $otherStmt->fetch(PDO::FETCH_ASSOC);

    // Recent other-sales entries (manual sales and voided refunds) with item lines
    $otherListStmt = $conn->prepare("
        SELECT t.id, t.total_amount, t.payment_type, t.status, t.source, t.created_at,
               COALESCE(u.full_name, 'Unknown user') AS recorded_by,
               GROUP_CONCAT(CONCAT(ti.quantity, 'x ', COALESCE(p.product_name, 'Item')) SEPARATOR '\n') AS items_summary
        FROM transactions t
        LEFT JOIN users u ON t.user_id = u.id
        LEFT JOIN transaction_items ti ON ti.transaction_id = t.id
        LEFT JOIN products p ON ti.product_id = p.id
        WHERE t.store_id = :store_id
          AND (t.source = 'manual' OR LOWER(t.status) = 'voided')$rangeSql
        GROUP BY t.id, t.total_amount, t.payment_type, t.status, t.source, t.created_at, u.full_name
        ORDER BY t.created_at DESC
        LIMIT 50
    ");
    $otherListStmt->execute($params);
    $otherEntries = $otherListStmt->fetchAll(PDO::FETCH_ASSOC);

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
        "other_sales" => [
            "manual_sales_total" => round((float)($otherRow['manual_sales_total'] ?? 0.0), 2),
            "manual_sales_count" => (int)($otherRow['manual_sales_count'] ?? 0),
            "voided_total" => round((float)($otherRow['voided_total'] ?? 0.0), 2),
            "voided_count" => (int)($otherRow['voided_count'] ?? 0),
            "entries" => $otherEntries
        ],
        "payment_methods" => $paymentMethods,
        "top_products" => $topProducts,
        "daily_sales" => $dailySales
    ]);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode([
        "success" => false,
        "message" => "Unable to load store reports. Please try again."
    ]);
}
?>

