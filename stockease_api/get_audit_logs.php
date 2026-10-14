<?php
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Origin: *");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin', 'manager', 'store_admin']);

try {
    $conn->exec("CREATE TABLE IF NOT EXISTS audit_logs (
        id SERIAL PRIMARY KEY,
        user_name VARCHAR(100) NOT NULL,
        role VARCHAR(50) NOT NULL,
        action VARCHAR(100) NOT NULL,
        target VARCHAR(100) NOT NULL,
        store_name VARCHAR(100) DEFAULT 'System',
        details TEXT DEFAULT '',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )");

    // Seed initial operational logs if empty
    $check = $conn->query("SELECT COUNT(*) AS total FROM audit_logs")->fetch(PDO::FETCH_ASSOC);
    if ((int)$check['total'] === 0) {
        $sampleLogs = [
            ['System Super Admin', 'super_admin', 'SYSTEM_LOGIN', 'Dashboard', 'Global System', 'Super admin session initiated successfully.'],
            ['Mark Santos', 'store_admin', 'PRODUCT_RESTOCK', 'Bottled Water', 'Ustore', 'Replenished inventory +30 units.'],
            ['Ana Cruz', 'staff', 'PROCESS_SALE', 'Receipt #1056', 'Ustore', 'Checkout completed via Cash (PHP 350.00).'],
            ['Mark Santos', 'store_admin', 'STAFF_UPDATE', 'Ana Cruz', 'Ustore', 'Cashier permissions verified.'],
            ['System Super Admin', 'super_admin', 'STORE_CONFIG', 'Ustore', 'Global System', 'Store profile verified and status set to Active.']
        ];
        $insertStmt = $conn->prepare("INSERT INTO audit_logs (user_name, role, action, target, store_name, details) VALUES (?, ?, ?, ?, ?, ?)");
        foreach ($sampleLogs as $log) {
            $insertStmt->execute($log);
        }
    }

    $search = isset($_GET['search']) ? trim($_GET['search']) : '';
    $actionFilter = isset($_GET['action_filter']) ? trim($_GET['action_filter']) : '';
    $period = isset($_GET['period']) ? trim($_GET['period']) : '';
    $startDate = isset($_GET['start_date']) ? trim($_GET['start_date']) : '';
    $endDate = isset($_GET['end_date']) ? trim($_GET['end_date']) : '';

    $sql = "SELECT id, user_name, role, action, target, store_name, details, 
                   to_char(created_at, 'YYYY-MM-DD HH24:MI:SS') as created_at 
            FROM audit_logs WHERE 1=1";
    $params = [];

    // System super admin is visible ONLY to the System Admin
    if ($identity['role'] !== 'super_admin') {
        $sql .= " AND role != 'super_admin' AND user_name NOT ILIKE '%super admin%'";
    }

    if (!empty($search)) {
        $sql .= " AND (user_name ILIKE :search OR target ILIKE :search OR details ILIKE :search OR store_name ILIKE :search)";
        $params['search'] = "%$search%";
    }

    if (!empty($actionFilter)) {
        $sql .= " AND action = :act";
        $params['act'] = $actionFilter;
    }

    if ($period === 'today') {
        $sql .= " AND created_at >= CURRENT_DATE AND created_at < CURRENT_DATE + INTERVAL '1 day'";
    } elseif ($period === 'this_week') {
        $sql .= " AND created_at >= date_trunc('week', CURRENT_DATE) AND created_at < (date_trunc('week', CURRENT_DATE) + INTERVAL '7 days')";
    } elseif ($period === 'this_month') {
        $sql .= " AND created_at >= date_trunc('month', CURRENT_DATE) AND created_at < (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month')";
    } elseif ($period === 'custom' && !empty($startDate) && !empty($endDate)) {
        $sql .= " AND created_at >= :start_date AND created_at <= :end_date";
        $params['start_date'] = $startDate . " 00:00:00";
        $params['end_date'] = $endDate . " 23:59:59";
    }

    $sql .= " ORDER BY id DESC LIMIT 150";

    $stmt = $conn->prepare($sql);
    $stmt->execute($params);
    $logs = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode(["success" => true, "logs" => $logs]);

} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Unable to load audit logs", "error" => $e->getMessage()]);
}
?>
