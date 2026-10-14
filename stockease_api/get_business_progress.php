<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: GET, POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin', 'manager', 'store_admin', 'staff']);

$period = $_GET['period'] ?? 'this_week';
$startDate = $_GET['start_date'] ?? null;
$endDate = $_GET['end_date'] ?? null;
$requestedStoreId = isset($_GET['store_id']) ? (int)$_GET['store_id'] : 0;
$requestedOwnerId = isset($_GET['owner_id']) ? (int)$_GET['owner_id'] : 0;

$whereClauses = ["(t.status IS NULL OR t.status != 'voided')"];
$params = [];

// Determine Scope based on user role and request
if ($identity['role'] === 'super_admin') {
    if ($requestedStoreId > 0) {
        $whereClauses[] = "t.store_id = :store_id";
        $params['store_id'] = $requestedStoreId;
    } elseif ($requestedOwnerId > 0) {
        $whereClauses[] = "t.store_id IN (SELECT id FROM stores WHERE owner_id = :owner_id)";
        $params['owner_id'] = $requestedOwnerId;
    }
} elseif ($identity['role'] === 'manager') {
    $managerId = (int)$identity['user_id'];
    if ($requestedStoreId > 0) {
        // Verify manager owns this store or is assigned
        $stCheck = $conn->prepare("SELECT id FROM stores WHERE id = :sid AND (owner_id = :uid OR id = :assigned)");
        $stCheck->execute(['sid' => $requestedStoreId, 'uid' => $managerId, 'assigned' => (int)($identity['store_id'] ?? 0)]);
        if (!$stCheck->fetchColumn()) {
            auth_json_error(403, "Access to requested store branch denied");
        }
        $whereClauses[] = "t.store_id = :store_id";
        $params['store_id'] = $requestedStoreId;
    } else {
        // All branches owned by this manager
        $whereClauses[] = "t.store_id IN (SELECT id FROM stores WHERE owner_id = :mgr_id OR id = :assigned)";
        $params['mgr_id'] = $managerId;
        $params['assigned'] = (int)($identity['store_id'] ?? 0);
    }
} else {
    // store_admin or staff: strictly their assigned store
    $assignedStoreId = auth_resolve_store_context($conn, $identity, $requestedStoreId, false);
    $whereClauses[] = "t.store_id = :store_id";
    $params['store_id'] = $assignedStoreId;
}

// Compute Date Window
$now = new DateTime();
$dateCondition = "";
$chartSeriesSql = "";

switch ($period) {
    case 'today':
        $dateCondition = "t.created_at >= CURRENT_DATE AND t.created_at < CURRENT_DATE + INTERVAL '1 day'";
        $chartSeriesSql = "
            SELECT 
                TO_CHAR(h, 'HH24:00') AS label,
                COALESCE(SUM(t.total_amount), 0.0) AS sales,
                COUNT(t.id) AS tx_count
            FROM generate_series(
                date_trunc('day', CURRENT_TIMESTAMP),
                date_trunc('hour', CURRENT_TIMESTAMP),
                '1 hour'::interval
            ) h
            LEFT JOIN transactions t ON date_trunc('hour', t.created_at) = h
                                    AND " . implode(" AND ", $whereClauses) . "
            GROUP BY h
            ORDER BY h ASC
        ";
        break;

    case 'this_month':
        $dateCondition = "t.created_at >= date_trunc('month', CURRENT_DATE) AND t.created_at < (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month')";
        $chartSeriesSql = "
            SELECT 
                TO_CHAR(d::date, 'Mon DD') AS label,
                COALESCE(SUM(t.total_amount), 0.0) AS sales,
                COUNT(t.id) AS tx_count
            FROM generate_series(
                date_trunc('month', CURRENT_DATE),
                LEAST(CURRENT_DATE, (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month' - INTERVAL '1 day')::date),
                '1 day'::interval
            ) d
            LEFT JOIN transactions t ON TO_CHAR(t.created_at, 'YYYY-MM-DD') = TO_CHAR(d::date, 'YYYY-MM-DD')
                                    AND " . implode(" AND ", $whereClauses) . "
            GROUP BY d::date
            ORDER BY d::date ASC
        ";
        break;

    case 'custom':
        if (!empty($startDate) && !empty($endDate)) {
            $dateCondition = "t.created_at >= :start_date AND t.created_at <= :end_date";
            $params['start_date'] = $startDate . " 00:00:00";
            $params['end_date'] = $endDate . " 23:59:59";
            
            $chartSeriesSql = "
                SELECT 
                    TO_CHAR(d::date, 'Mon DD') AS label,
                    COALESCE(SUM(t.total_amount), 0.0) AS sales,
                    COUNT(t.id) AS tx_count
                FROM generate_series(
                    :series_start::date,
                    :series_end::date,
                    '1 day'::interval
                ) d
                LEFT JOIN transactions t ON TO_CHAR(t.created_at, 'YYYY-MM-DD') = TO_CHAR(d::date, 'YYYY-MM-DD')
                                        AND " . implode(" AND ", $whereClauses) . "
                GROUP BY d::date
                ORDER BY d::date ASC
            ";
        } else {
            // Fallback to this_week if custom dates invalid
            $period = 'this_week';
        }
        break;

    case 'this_week':
    default:
        $period = 'this_week';
        $dateCondition = "t.created_at >= date_trunc('week', CURRENT_DATE) AND t.created_at < (date_trunc('week', CURRENT_DATE) + INTERVAL '7 days')";
        $chartSeriesSql = "
            SELECT 
                TO_CHAR(d::date, 'Dy') AS label,
                COALESCE(SUM(t.total_amount), 0.0) AS sales,
                COUNT(t.id) AS tx_count
            FROM generate_series(
                date_trunc('week', CURRENT_DATE),
                date_trunc('week', CURRENT_DATE) + INTERVAL '6 days',
                '1 day'::interval
            ) d
            LEFT JOIN transactions t ON TO_CHAR(t.created_at, 'YYYY-MM-DD') = TO_CHAR(d::date, 'YYYY-MM-DD')
                                    AND " . implode(" AND ", $whereClauses) . "
            GROUP BY d::date
            ORDER BY d::date ASC
        ";
        break;
}

if (!empty($dateCondition)) {
    $whereClauses[] = $dateCondition;
}

$finalWhereSql = implode(" AND ", $whereClauses);

try {
    // 1. Overall Summary Metrics
    $summaryStmt = $conn->prepare("
        SELECT 
            COALESCE(SUM(t.total_amount), 0.0) AS total_sales,
            COUNT(t.id) AS transaction_count,
            COALESCE(AVG(t.total_amount), 0.0) AS avg_transaction
        FROM transactions t
        WHERE $finalWhereSql
    ");
    $summaryStmt->execute($params);
    $sumRow = $summaryStmt->fetch(PDO::FETCH_ASSOC);

    $totalSales = (float)($sumRow['total_sales'] ?? 0.0);
    $txCount = (int)($sumRow['transaction_count'] ?? 0);
    $avgTx = (float)($sumRow['avg_transaction'] ?? 0.0);

    // COGS, Net Profit, and Total Products Sold
    $cogsStmt = $conn->prepare("
        SELECT 
            COALESCE(SUM(ti.quantity), 0) AS products_sold,
            COALESCE(SUM(ti.quantity * COALESCE(ti.cost_price, p.cost_price, 0.0)), 0.0) AS total_cogs
        FROM transaction_items ti
        JOIN transactions t ON ti.transaction_id = t.id
        LEFT JOIN products p ON ti.product_id = p.id
        WHERE $finalWhereSql
    ");
    $cogsStmt->execute($params);
    $cogsRow = $cogsStmt->fetch(PDO::FETCH_ASSOC);

    $productsSold = (int)($cogsRow['products_sold'] ?? 0);
    $totalCogs = (float)($cogsRow['total_cogs'] ?? 0.0);
    $netProfit = $totalSales - $totalCogs;
    $marginPct = $totalSales > 0 ? round(($netProfit / $totalSales) * 100, 1) : 0.0;

    // 2. Line Chart Progression
    $chartStmt = $conn->prepare($chartSeriesSql);
    $chartParams = $params;
    if ($period === 'custom' && !empty($startDate) && !empty($endDate)) {
        $chartParams['series_start'] = $startDate;
        $chartParams['series_end'] = $endDate;
    }
    // Remove custom date range filters from chart series where-clauses if duplicate
    $chartStmt->execute($chartParams);
    $lineChart = $chartStmt->fetchAll(PDO::FETCH_ASSOC);

    // 3. Ranked Products: ALL products sold in this period, from MOST purchased to LEAST purchased
    $prodRankStmt = $conn->prepare("
        SELECT 
            p.id AS product_id,
            COALESCE(p.product_name, 'Unknown Item') AS product_name,
            COALESCE(c.name, p.category, 'General') AS category_name,
            COALESCE(p.price, 0.0) AS price,
            COALESCE(p.cost_price, 0.0) AS cost_price,
            COALESCE(SUM(ti.quantity), 0) AS total_sold_qty,
            COALESCE(SUM(ti.quantity * ti.unit_price), 0.0) AS total_revenue,
            COALESCE(SUM(ti.quantity * (ti.unit_price - COALESCE(ti.cost_price, p.cost_price, 0.0))), 0.0) AS total_net_profit
        FROM transaction_items ti
        JOIN transactions t ON ti.transaction_id = t.id
        JOIN products p ON ti.product_id = p.id
        LEFT JOIN categories c ON p.category_id = c.id
        WHERE $finalWhereSql
        GROUP BY p.id, p.product_name, c.name, p.category, p.price, p.cost_price
        ORDER BY total_sold_qty DESC, total_revenue DESC
    ");
    $prodRankStmt->execute($params);
    $rankedProducts = $prodRankStmt->fetchAll(PDO::FETCH_ASSOC);

    // 4. Payment Method Breakdown (Cash, GCash, Bank Transfer, Card)
    $payStmt = $conn->prepare("
        SELECT 
            COALESCE(t.payment_type, 'Cash') AS payment_method,
            COALESCE(SUM(t.total_amount), 0.0) AS total_amount,
            COUNT(t.id) AS tx_count
        FROM transactions t
        WHERE $finalWhereSql
        GROUP BY t.payment_type
        ORDER BY total_amount DESC
    ");
    $payStmt->execute($params);
    $paymentMethods = $payStmt->fetchAll(PDO::FETCH_ASSOC);

    // 5. Branch Comparison Cards (if viewing multiple branches)
    $branchList = [];
    if ($identity['role'] === 'super_admin' || $identity['role'] === 'manager') {
        $branchWhere = [];
        $branchParams = [];
        if ($identity['role'] === 'super_admin' && $requestedOwnerId > 0) {
            $branchWhere[] = "s.owner_id = :owner_id";
            $branchParams['owner_id'] = $requestedOwnerId;
        } elseif ($identity['role'] === 'manager') {
            $branchWhere[] = "(s.owner_id = :mgr_id OR s.id = :assigned)";
            $branchParams['mgr_id'] = (int)$identity['user_id'];
            $branchParams['assigned'] = (int)($identity['store_id'] ?? 0);
        }
        $branchWhereSql = !empty($branchWhere) ? "WHERE " . implode(" AND ", $branchWhere) : "";

        $branchesStmt = $conn->prepare("
            SELECT 
                s.id AS store_id,
                s.store_name,
                s.location,
                s.status,
                s.owner_id,
                COALESCE(COUNT(DISTINCT p.id), 0) AS product_count,
                COALESCE(SUM(CASE WHEN $finalWhereSql THEN t.total_amount ELSE 0 END), 0.0) AS total_sales,
                COALESCE(COUNT(DISTINCT CASE WHEN $finalWhereSql THEN t.id ELSE NULL END), 0) AS transaction_count
            FROM stores s
            LEFT JOIN products p ON s.id = p.store_id
            LEFT JOIN transactions t ON s.id = t.store_id
            $branchWhereSql
            GROUP BY s.id, s.store_name, s.location, s.status, s.owner_id
            ORDER BY s.id ASC
        ");
        $branchesStmt->execute(array_merge($params, $branchParams));
        $branchList = $branchesStmt->fetchAll(PDO::FETCH_ASSOC);

        $grandBranchSales = 0.0;
        foreach ($branchList as $b) {
            $grandBranchSales += (float)$b['total_sales'];
        }
        for ($i = 0; $i < count($branchList); $i++) {
            $bSales = (float)$branchList[$i]['total_sales'];
            $branchList[$i]['contribution_pct'] = $grandBranchSales > 0 ? round(($bSales / $grandBranchSales) * 100, 1) : 0.0;
        }
    }

    echo json_encode([
        "success" => true,
        "server_timestamp" => date('c'),
        "period" => $period,
        "date_range" => [
            "start_date" => $startDate,
            "end_date" => $endDate
        ],
        "summary" => [
            "total_sales" => round($totalSales, 2),
            "total_cogs" => round($totalCogs, 2),
            "net_profit" => round($netProfit, 2),
            "profit_margin_pct" => $marginPct,
            "transaction_count" => $txCount,
            "avg_transaction" => round($avgTx, 2),
            "products_sold" => $productsSold
        ],
        "line_chart" => $lineChart,
        "ranked_products" => $rankedProducts,
        "payment_methods" => $paymentMethods,
        "branches" => $branchList
    ]);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Database error: " . $e->getMessage()]);
}
?>
