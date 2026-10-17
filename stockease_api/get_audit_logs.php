<?php
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Origin: *");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin', 'manager', 'store_admin']);

try {
    $conn->exec("CREATE TABLE IF NOT EXISTS audit_logs (
        id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        user_name VARCHAR(100) NOT NULL,
        role VARCHAR(50) NOT NULL,
        action VARCHAR(100) NOT NULL,
        target VARCHAR(100) NOT NULL,
        store_name VARCHAR(100) DEFAULT 'System',
        details TEXT NULL,
        created_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

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

    // Pagination: opt in with ?page=1 (legacy callers without page keep the old 150 row cap)
    $paginate = isset($_GET['page']) && $_GET['page'] !== '';
    $page = $paginate ? max(1, (int)$_GET['page']) : 1;
    $perPage = isset($_GET['per_page']) ? (int)$_GET['per_page'] : 25;
    $perPage = max(1, min(200, $perPage));

    $where = ["1=1"];
    $params = [];

    // System super admin is visible ONLY to the System Admin
    if ($identity['role'] !== 'super_admin') {
        $where[] = "role != 'super_admin' AND user_name NOT LIKE '%super admin%'";
    }

    if (!empty($search)) {
        $where[] = "(user_name LIKE :search OR target LIKE :search OR details LIKE :search OR store_name LIKE :search)";
        $params['search'] = "%$search%";
    }

    if (!empty($actionFilter)) {
        $where[] = "action = :act";
        $params['act'] = $actionFilter;
    }

    if ($period === 'today') {
        $where[] = "created_at >= CURDATE() AND created_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)";
    } elseif ($period === 'this_week') {
        // PostgreSQL date_trunc('week', ...) starts on Monday - WEEKDAY() is Monday-based too
        $where[] = "created_at >= DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY)
                    AND created_at < DATE_ADD(DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY), INTERVAL 7 DAY)";
    } elseif ($period === 'this_month') {
        $where[] = "created_at >= DATE_FORMAT(CURDATE(), '%Y-%m-01')
                    AND created_at < DATE_ADD(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL 1 MONTH)";
    } elseif ($period === 'custom' && !empty($startDate) && !empty($endDate)) {
        $where[] = "created_at >= :start_date AND created_at <= :end_date";
        $params['start_date'] = $startDate . " 00:00:00";
        $params['end_date'] = $endDate . " 23:59:59";
    }

    $whereClause = implode(" AND ", $where);

    $countStmt = $conn->prepare("SELECT COUNT(*) FROM audit_logs WHERE $whereClause");
    $countStmt->execute($params);
    $total = (int)$countStmt->fetchColumn();

    $sql = "SELECT id, user_name, role, action, target, store_name, details, 
                   created_at 
            FROM audit_logs 
            WHERE $whereClause 
            ORDER BY id DESC";

    if ($paginate) {
        $totalPages = max(1, (int)ceil($total / $perPage));
        if ($page > $totalPages) {
            $page = $totalPages;
        }
        $sql .= " LIMIT :limit OFFSET :offset";
    } else {
        $totalPages = 1;
        $sql .= " LIMIT 150";
    }

    $stmt = $conn->prepare($sql);
    foreach ($params as $key => $value) {
        $stmt->bindValue($key, $value);
    }
    if ($paginate) {
        $stmt->bindValue('limit', $perPage, PDO::PARAM_INT);
        $stmt->bindValue('offset', ($page - 1) * $perPage, PDO::PARAM_INT);
    }
    $stmt->execute();
    $logs = $stmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode([
        "success" => true,
        "logs" => $logs,
        "pagination" => [
            "page" => $page,
            "per_page" => $perPage,
            "total" => $total,
            "total_pages" => $totalPages
        ]
    ]);

} catch (PDOException $e) {
    http_response_code(500);
    error_log('get_audit_logs caught exception: ' . $e->getMessage());
    echo json_encode(["success" => false, "message" => "Unable to load audit logs", "error" => "Unexpected server error. Details were written to the server log."]);
}
?>

