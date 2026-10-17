<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: GET, POST");

require_once 'db.php';
require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/subscription_helper.php';
require_once __DIR__ . '/series_helper.php';

$requestedStoreId = isset($_GET['store_id']) ? intval($_GET['store_id']) : 0;
$identity = auth_require_user($conn);
auth_require_roles($identity, ['store_admin', 'manager', 'super_admin']);
$store_id = auth_resolve_store_context($conn, $identity, $requestedStoreId, true);

try {
    // 1. Total Products Count
    $stmt1 = $conn->prepare("SELECT COUNT(*) as total_products FROM products WHERE store_id = :store_id");
    $stmt1->bindParam(':store_id', $store_id);
    $stmt1->execute();
    $total_products = (int)$stmt1->fetch(PDO::FETCH_ASSOC)['total_products'];

    // 2. Low Stock Alerts (items with stock < 10)
    $stmt2 = $conn->prepare("SELECT COUNT(*) as low_stock FROM products WHERE store_id = :store_id AND stock < 10");
    $stmt2->bindParam(':store_id', $store_id);
    $stmt2->execute();
    $low_stock = (int)$stmt2->fetch(PDO::FETCH_ASSOC)['low_stock'];

    // 3. Active staff assigned to this store
    $stmt3 = $conn->prepare("SELECT COUNT(*) as total_staff FROM users WHERE store_id = :store_id AND role = 'staff' AND is_active = true");
    $stmt3->bindParam(':store_id', $store_id);
    $stmt3->execute();
    $total_staff = (int)$stmt3->fetch(PDO::FETCH_ASSOC)['total_staff'];

    // 4. Total Lifetime Sales Amount (excluding voided transactions)
    $stmt4 = $conn->prepare("SELECT COALESCE(SUM(total_amount), 0) as total_sales FROM transactions WHERE store_id = :store_id AND (status IS NULL OR status != 'voided')");
    $stmt4->bindParam(':store_id', $store_id);
    $stmt4->execute();
    $total_sales = (float)$stmt4->fetch(PDO::FETCH_ASSOC)['total_sales'];

    // 5. Today's Specific Sales & Tx Count
    $stmtToday = $conn->prepare("
        SELECT COALESCE(SUM(total_amount), 0.0) AS today_sales, COUNT(id) AS today_tx_count
        FROM transactions 
        WHERE store_id = :store_id AND (status IS NULL OR status != 'voided') AND created_at >= CURRENT_DATE
    ");
    $stmtToday->execute(['store_id' => $store_id]);
    $todayRow = $stmtToday->fetch(PDO::FETCH_ASSOC);
    $today_sales = (float)($todayRow['today_sales'] ?? 0.0);
    $today_tx_count = (int)($todayRow['today_tx_count'] ?? 0);

    // 6. Today's Sales Line Chart (Hourly Timeline of all sales for today)
    // MySQL: hour spine built in PHP from midnight to the current hour.
    $hourSeries = series_hours_range_sql(date('Y-m-d') . ' 00:00:00', date('Y-m-d'), date('G') + 1, 'h');
    $stmtHourly = $conn->prepare("
        SELECT 
            DATE_FORMAT(s.h, '%H:00') AS label,
            COALESCE(SUM(t.total_amount), 0.0) AS hourly_sales
        FROM $hourSeries s
        LEFT JOIN transactions t ON DATE_FORMAT(t.created_at, '%Y-%m-%d %H:00:00') = s.h
                                AND t.store_id = :store_id
                                AND (t.status IS NULL OR t.status != 'voided')
        GROUP BY s.h, DATE_FORMAT(s.h, '%H:00')
        ORDER BY s.h ASC
    ");
    $stmtHourly->execute(['store_id' => $store_id]);
    $today_sales_trend = $stmtHourly->fetchAll(PDO::FETCH_ASSOC);

    // 7. Today's Sold Products: Ranked from Most Purchased to Least Purchased
    $stmtTodayProducts = $conn->prepare("
        SELECT 
            p.id AS product_id,
            p.product_name,
            COALESCE(c.name, p.category, 'General') AS category_name,
            COALESCE(SUM(ti.quantity), 0) AS total_sold_qty,
            COALESCE(SUM(ti.quantity * ti.unit_price), 0.0) AS total_earned
        FROM transaction_items ti
        JOIN transactions t ON ti.transaction_id = t.id
        JOIN products p ON ti.product_id = p.id
        LEFT JOIN categories c ON p.category_id = c.id
        WHERE t.store_id = :store_id 
          AND (t.status IS NULL OR t.status != 'voided')
          AND t.created_at >= CURRENT_DATE
        GROUP BY p.id, p.product_name, c.name, p.category
        ORDER BY total_sold_qty DESC, total_earned DESC
    ");
    $stmtTodayProducts->execute(['store_id' => $store_id]);
    $today_sold_products = $stmtTodayProducts->fetchAll(PDO::FETCH_ASSOC);

    // 8. Low Stock Items (All products with stock < 10)
    $stmtLowAll = $conn->prepare("
        SELECT id, product_name, stock, price, COALESCE(cost_price, 0.0) AS cost_price
        FROM products 
        WHERE store_id = :store_id AND stock < 10 
        ORDER BY stock ASC
    ");
    $stmtLowAll->execute(['store_id' => $store_id]);
    $all_low_stock_items = $stmtLowAll->fetchAll(PDO::FETCH_ASSOC);
    $low_stock_items = array_slice($all_low_stock_items, 0, 3);

    // 9. Recent Transactions
    $stmt6 = $conn->prepare("
        SELECT id, total_amount, payment_type as payment_method, created_at as transaction_date 
        FROM transactions 
        WHERE store_id = :store_id 
        ORDER BY created_at DESC 
        LIMIT 5
    ");
    $stmt6->execute(['store_id' => $store_id]);
    $recent_transactions = $stmt6->fetchAll(PDO::FETCH_ASSOC);

    // 10. Inbound Stock Transfers Alert
    $stmt7 = $conn->prepare("
        SELECT st.id, st.status, sf.store_name AS from_store_name,
               COALESCE(SUM(sti.quantity), 0) AS total_quantity,
               DATE_FORMAT(st.created_at, '%b %d, %H:%i') AS created_at
        FROM stock_transfers st
        JOIN stores sf ON st.from_store_id = sf.id
        LEFT JOIN stock_transfer_items sti ON st.id = sti.transfer_id
        WHERE st.to_store_id = :store_id AND st.status IN ('in_transit', 'requested')
        GROUP BY st.id, st.status, sf.store_name, st.created_at
        ORDER BY CASE WHEN st.status = 'in_transit' THEN 1 ELSE 2 END, st.id DESC
        LIMIT 1
    ");
    $stmt7->execute(['store_id' => $store_id]);
    $inbound_transfer = $stmt7->fetch(PDO::FETCH_ASSOC);

    // 11. 7-Day daily sales trend for line chart (MySQL: spine built in PHP)
    $dailySeries = series_dates_sql(date('Y-m-d', strtotime('-6 days')), date('Y-m-d'), 'd');
    $stmtDaily = $conn->prepare("
        SELECT 
            s.d AS sale_date,
            DATE_FORMAT(s.d, '%a') AS label,
            COALESCE(SUM(t.total_amount), 0.0) AS daily_sales
        FROM $dailySeries s
        LEFT JOIN transactions t ON DATE(t.created_at) = s.d
                                AND t.store_id = :store_id
                                AND (t.status IS NULL OR t.status != 'voided')
        GROUP BY s.d, DATE_FORMAT(s.d, '%a')
        ORDER BY s.d ASC
    ");
    $stmtDaily->execute(['store_id' => $store_id]);
    $daily_sales_trend = $stmtDaily->fetchAll(PDO::FETCH_ASSOC);

    // 12. Current Subscription Status
    $sub = subscription_get_current($conn, $identity);

    echo json_encode([
        "success" => true,
        "server_timestamp" => date('c'),
        "store_id" => $store_id,
        "total_products" => $total_products,
        "low_stock" => $low_stock,
        "total_staff" => $total_staff,
        "total_sales" => $total_sales,
        "today_sales" => $today_sales,
        "today_tx_count" => $today_tx_count,
        "today_sales_trend" => $today_sales_trend,
        "today_sold_products" => $today_sold_products,
        "low_stock_items" => $low_stock_items,
        "all_low_stock_items" => $all_low_stock_items,
        "recent_transactions" => $recent_transactions,
        "daily_sales_trend" => $daily_sales_trend,
        "inbound_transfer" => $inbound_transfer ?: null,
        "subscription" => [
            "plan" => $sub['plan'],
            "display_plan" => $sub['display_plan'] ?? ucfirst($sub['plan']),
            "status" => $sub['status'],
            "is_premium" => $sub['is_premium'],
            "days_remaining" => $sub['days_remaining'] ?? null
        ]
    ]);

} catch (PDOException $e) {
    http_response_code(500);
    error_log('get_store_admin_dashboard caught exception: ' . $e->getMessage());
    echo json_encode(["success" => false, "message" => "Unable to load dashboard. Please try again."]);
}
?>

