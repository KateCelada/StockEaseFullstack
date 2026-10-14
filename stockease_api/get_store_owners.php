<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin', 'manager', 'store_admin']);

try {
    $currentUserId = (int)$identity['user_id'];
    $currentUserRole = $identity['role'];

    // 1. Fetch stores scoped to user role
    $storeQuery = "
        SELECT 
            s.id AS store_id,
            s.store_name,
            s.location,
            COALESCE(s.description, '') AS description,
            s.status,
            s.owner_id,
            s.manager_id,
            s.created_at,
            ow.full_name AS owner_name,
            ow.username AS owner_username,
            ow.email AS owner_email,
            ow.role AS owner_role,
            mgr.full_name AS manager_name,
            mgr.username AS manager_username,
            mgr.email AS manager_email
        FROM stores s
        LEFT JOIN users ow ON s.owner_id = ow.id
        LEFT JOIN users mgr ON s.manager_id = mgr.id
    ";

    $whereParams = [];
    if ($currentUserRole === 'manager') {
        $storeQuery .= " WHERE (s.owner_id = :uid OR s.manager_id = :uid OR s.id = :assigned_store)";
        $whereParams['uid'] = $currentUserId;
        $whereParams['assigned_store'] = (int)($identity['store_id'] ?? 0);
    } elseif ($currentUserRole === 'store_admin') {
        $storeQuery .= " WHERE s.id = :assigned_store";
        $whereParams['assigned_store'] = (int)($identity['store_id'] ?? 0);
    }

    $storeQuery .= " ORDER BY s.id ASC";
    $stmt = $conn->prepare($storeQuery);
    $stmt->execute($whereParams);
    $allStores = $stmt->fetchAll(PDO::FETCH_ASSOC);

    // Group stores by owner or by store portfolio
    // If owner_id is present, group under that owner. If owner_id is null, treat each independent store as its own portfolio.
    $portfolioGroups = [];

    foreach ($allStores as $s) {
        $storeId = (int)$s['store_id'];
        $groupKey = !empty($s['owner_id']) ? 'owner_' . $s['owner_id'] : 'store_' . $storeId;

        // Fetch metrics for this branch
        $metricsStmt = $conn->prepare("
            SELECT 
                COALESCE(SUM(CASE WHEN (status IS NULL OR status != 'voided') THEN total_amount ELSE 0 END), 0.0) AS total_sales,
                COALESCE(SUM(CASE WHEN (status IS NULL OR status != 'voided') AND created_at >= CURRENT_DATE THEN total_amount ELSE 0 END), 0.0) AS today_sales,
                COUNT(CASE WHEN (status IS NULL OR status != 'voided') THEN id ELSE NULL END) AS total_transactions,
                COUNT(CASE WHEN (status IS NULL OR status != 'voided') AND created_at >= CURRENT_DATE THEN id ELSE NULL END) AS today_transactions
            FROM transactions
            WHERE store_id = :store_id
        ");
        $metricsStmt->execute(['store_id' => $storeId]);
        $m = $metricsStmt->fetch(PDO::FETCH_ASSOC);

        $prodCountStmt = $conn->prepare("
            SELECT 
                COUNT(id) AS total_products,
                COUNT(CASE WHEN stock <= 5 THEN id ELSE NULL END) AS low_stock_count
            FROM products
            WHERE store_id = :store_id
        ");
        $prodCountStmt->execute(['store_id' => $storeId]);
        $pc = $prodCountStmt->fetch(PDO::FETCH_ASSOC);

        // 7-Day sales timeline for this branch
        $trendStmt = $conn->prepare("
            SELECT 
                TO_CHAR(d::date, 'Mon DD') AS label,
                TO_CHAR(d::date, 'YYYY-MM-DD') AS date,
                COALESCE(SUM(t.total_amount), 0.0) AS sales,
                COUNT(t.id) AS tx_count
            FROM generate_series(CURRENT_DATE - INTERVAL '6 days', CURRENT_DATE, '1 day'::interval) d
            LEFT JOIN transactions t ON TO_CHAR(t.created_at, 'YYYY-MM-DD') = TO_CHAR(d::date, 'YYYY-MM-DD')
                                    AND t.store_id = :store_id
                                    AND (t.status IS NULL OR t.status != 'voided')
            GROUP BY d::date
            ORDER BY d::date ASC
        ");
        $trendStmt->execute(['store_id' => $storeId]);
        $branchTrend = $trendStmt->fetchAll(PDO::FETCH_ASSOC);
        foreach ($branchTrend as &$bt) {
            $bt['sales'] = round((float)$bt['sales'], 2);
            $bt['tx_count'] = (int)$bt['tx_count'];
        }
        unset($bt);

        // Top products with quantity sold, gross earnings, net profit, gross profit, and stock on hand
        $topProdStmt = $conn->prepare("
            SELECT 
                p.id AS product_id,
                p.product_name,
                COALESCE(c.name, p.category, 'General') AS category_name,
                COALESCE(p.price, 0.0) AS price,
                COALESCE(p.cost_price, 0.0) AS cost_price,
                p.stock AS stock_on_hand,
                COALESCE(SUM(ti.quantity), 0) AS quantity_sold,
                COALESCE(SUM(ti.quantity * ti.unit_price), 0.0) AS gross_earnings,
                COALESCE(SUM(ti.quantity * (ti.unit_price - COALESCE(ti.cost_price, p.cost_price, 0.0))), 0.0) AS net_profit
            FROM products p
            LEFT JOIN categories c ON p.category_id = c.id
            LEFT JOIN transaction_items ti ON p.id = ti.product_id
            LEFT JOIN transactions t ON ti.transaction_id = t.id AND (t.status IS NULL OR t.status != 'voided')
            WHERE p.store_id = :store_id
            GROUP BY p.id, p.product_name, c.name, p.category, p.price, p.cost_price, p.stock
            ORDER BY quantity_sold DESC, gross_earnings DESC
            LIMIT 15
        ");
        $topProdStmt->execute(['store_id' => $storeId]);
        $topProducts = $topProdStmt->fetchAll(PDO::FETCH_ASSOC);
        foreach ($topProducts as &$tp) {
            $tp['price'] = round((float)$tp['price'], 2);
            $tp['cost_price'] = round((float)$tp['cost_price'], 2);
            $tp['stock_on_hand'] = (int)$tp['stock_on_hand'];
            $tp['quantity_sold'] = (int)$tp['quantity_sold'];
            $tp['gross_earnings'] = round((float)$tp['gross_earnings'], 2);
            $tp['gross_profit'] = $tp['gross_earnings'];
            $tp['net_profit'] = round((float)$tp['net_profit'], 2);
        }
        unset($tp);

        // Branch Users (Store Admins and Staff like Cashier)
        $usersSql = "
            SELECT 
                u.id,
                u.full_name,
                u.username,
                u.email,
                u.role,
                u.is_active,
                u.created_at,
                (CASE 
                    WHEN u.role = 'manager' THEN 'Store Manager'
                    WHEN u.role = 'store_admin' THEN 'Store Administrator'
                    WHEN u.role = 'inventory_checker' THEN 'Inventory Checker'
                    WHEN u.role = 'staff' THEN 'Cashier / Staff'
                    ELSE u.role 
                END) AS role_display,
                (SELECT MAX(created_at) FROM transactions WHERE user_id = u.id) AS last_transaction_at
            FROM users u
            WHERE (u.store_id = :store_id OR u.id = :owner_id OR u.id = :manager_id)
        ";
        if ($currentUserRole !== 'super_admin') {
            $usersSql .= " AND u.role != 'super_admin'";
        }
        $usersSql .= " ORDER BY (CASE WHEN u.role = 'manager' THEN 1 WHEN u.role = 'store_admin' THEN 2 WHEN u.role = 'inventory_checker' THEN 3 ELSE 4 END), u.id ASC";
        $usersStmt = $conn->prepare($usersSql);
        $usersStmt->execute([
            'store_id' => $storeId,
            'owner_id' => (int)($s['owner_id'] ?? 0),
            'manager_id' => (int)($s['manager_id'] ?? 0)
        ]);
        $branchUsers = $usersStmt->fetchAll(PDO::FETCH_ASSOC);

        $branchData = [
            'id' => $storeId,
            'store_name' => $s['store_name'],
            'location' => $s['location'],
            'description' => $s['description'],
            'status' => $s['status'],
            'owner_id' => $s['owner_id'],
            'manager_id' => $s['manager_id'],
            'owner_name' => $s['owner_name'],
            'manager_name' => $s['manager_name'],
            'total_sales' => round((float)$m['total_sales'], 2),
            'today_sales' => round((float)$m['today_sales'], 2),
            'total_transactions' => (int)$m['total_transactions'],
            'today_transactions' => (int)$m['today_transactions'],
            'total_products' => (int)$pc['total_products'],
            'low_stock_count' => (int)$pc['low_stock_count'],
            'sales_progress' => $branchTrend,
            'top_products' => $topProducts,
            'branch_users' => $branchUsers,
            'contribution_pct' => 0.0 // computed after group totals
        ];

        if (!isset($portfolioGroups[$groupKey])) {
            $portfolioGroups[$groupKey] = [
                'portfolio_key' => $groupKey,
                'store_name' => $s['store_name'],
                'primary_store_id' => $storeId,
                'description' => $s['description'],
                'owner_id' => $s['owner_id'],
                'owner_name' => $s['owner_name'] ?: 'Independent Owner',
                'owner_username' => $s['owner_username'] ?: '',
                'owner_email' => $s['owner_email'] ?: '',
                'manager_id' => $s['manager_id'],
                'manager_name' => $s['manager_name'] ?: 'Not Assigned',
                'manager_username' => $s['manager_username'] ?: '',
                'overall_sales' => 0.0,
                'overall_today_sales' => 0.0,
                'overall_transactions' => 0,
                'overall_products' => 0,
                'branches' => []
            ];
        }

        $portfolioGroups[$groupKey]['overall_sales'] += $branchData['total_sales'];
        $portfolioGroups[$groupKey]['overall_today_sales'] += $branchData['today_sales'];
        $portfolioGroups[$groupKey]['overall_transactions'] += $branchData['total_transactions'];
        $portfolioGroups[$groupKey]['overall_products'] += $branchData['total_products'];
        $portfolioGroups[$groupKey]['branches'][] = $branchData;
    }

    // Now calculate combined business progress line chart and contribution percentages
    $portfoliosList = [];
    foreach ($portfolioGroups as &$group) {
        $group['overall_sales'] = round($group['overall_sales'], 2);
        $group['overall_today_sales'] = round($group['overall_today_sales'], 2);

        // Aggregate 7-Day sales across all branches in this portfolio
        $combinedTrendMap = [];
        foreach ($group['branches'] as $b) {
            foreach ($b['sales_progress'] as $dayPoint) {
                $dKey = $dayPoint['date'];
                if (!isset($combinedTrendMap[$dKey])) {
                    $combinedTrendMap[$dKey] = [
                        'label' => $dayPoint['label'],
                        'date' => $dKey,
                        'sales' => 0.0,
                        'tx_count' => 0
                    ];
                }
                $combinedTrendMap[$dKey]['sales'] += (float)$dayPoint['sales'];
                $combinedTrendMap[$dKey]['tx_count'] += (int)$dayPoint['tx_count'];
            }
        }

        $combinedTrend = array_values($combinedTrendMap);
        usort($combinedTrend, function($a, $b) {
            return strcmp($a['date'], $b['date']);
        });
        foreach ($combinedTrend as &$ct) {
            $ct['sales'] = round($ct['sales'], 2);
        }
        unset($ct);
        $group['sales_progress'] = $combinedTrend;

        // Calculate contribution pct for each branch
        foreach ($group['branches'] as &$b) {
            if ($group['overall_sales'] > 0) {
                $b['contribution_pct'] = round(($b['total_sales'] / $group['overall_sales']) * 100, 1);
            } else {
                $b['contribution_pct'] = round(100.0 / max(1, count($group['branches'])), 1);
            }
        }
        unset($b);

        $portfoliosList[] = $group;
    }
    unset($group);

    echo json_encode([
        'success' => true,
        'count' => count($portfoliosList),
        'owners' => $portfoliosList,
        'portfolios' => $portfoliosList
    ], JSON_PRETTY_PRINT);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'message' => 'Database error: ' . $e->getMessage()]);
}
