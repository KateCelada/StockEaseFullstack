<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: GET, POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/shift_helper.php';

$identity = auth_require_user($conn);
// Shifts belong to the staff tier (cashiers and inventory staff)
auth_require_roles($identity, ['staff']);

$rawBody = file_get_contents('php://input');
$jsonBody = json_decode($rawBody, true);
if (!is_array($jsonBody)) {
    $jsonBody = [];
}

$action = $_GET['action'] ?? $_POST['action'] ?? ($jsonBody['action'] ?? 'current');

/**
 * Fetch the currently active shift for the logged-in staff member.
 */
function get_active_shift(PDO $conn, int $userId): ?array
{
    $stmt = $conn->prepare("
        SELECT ss.id, ss.user_id, ss.store_id, ss.status,
               to_char(ss.started_at, 'YYYY-MM-DD HH24:MI:SS') AS started_at,
               to_char(ss.ended_at, 'YYYY-MM-DD HH24:MI:SS') AS ended_at
        FROM staff_shifts ss
        WHERE ss.user_id = :uid AND ss.status = 'active'
        ORDER BY ss.id DESC
        LIMIT 1
    ");
    $stmt->execute(['uid' => $userId]);
    $shift = $stmt->fetch(PDO::FETCH_ASSOC);
    return $shift ?: null;
}

try {
    if ($action === 'current') {
        // A shift left open from a previous day is closed automatically
        shift_close_stale_shifts($conn, (int)$identity['user_id']);
        $shift = get_active_shift($conn, (int)$identity['user_id']);
        echo json_encode(['success' => true, 'active' => $shift !== null, 'shift' => $shift]);
        exit();
    }

    if ($action === 'start') {
        // Shifts normally start automatically on the first login of the day
        // (login.php); this action stays available as an idempotent fallback.
        shift_close_stale_shifts($conn, (int)$identity['user_id']);
        $existing = get_active_shift($conn, (int)$identity['user_id']);
        if ($existing) {
            echo json_encode([
                'success' => true,
                'message' => 'Shift already active.',
                'shift' => $existing
            ]);
            exit();
        }

        $storeId = (int)($identity['store_id'] ?? 0);
        if ($storeId <= 0) {
            auth_json_error(400, 'Your account is not assigned to a branch. Cannot start a shift.');
        }

        $stmt = $conn->prepare("
            INSERT INTO staff_shifts (user_id, store_id, status, started_at)
            VALUES (:uid, :sid, 'active', CURRENT_TIMESTAMP)
            RETURNING id, user_id, store_id, status,
                      to_char(started_at, 'YYYY-MM-DD HH24:MI:SS') AS started_at
        ");
        $stmt->execute(['uid' => (int)$identity['user_id'], 'sid' => $storeId]);
        $shift = $stmt->fetch(PDO::FETCH_ASSOC);

        echo json_encode([
            'success' => true,
            'message' => 'Shift started. You can now process transactions.',
            'shift' => $shift
        ]);
        exit();
    }

    if ($action === 'end') {
        $input = !empty($jsonBody) ? $jsonBody : $_POST;
        $shiftId = isset($input['shift_id']) ? (int)$input['shift_id'] : 0;

        $shift = get_active_shift($conn, (int)$identity['user_id']);
        if (!$shift || ($shiftId > 0 && (int)$shift['id'] !== $shiftId)) {
            auth_json_error(400, 'You have no active shift to end.');
        }

        $stmt = $conn->prepare("
            UPDATE staff_shifts
            SET status = 'ended', ended_at = CURRENT_TIMESTAMP
            WHERE id = :id AND user_id = :uid AND status = 'active'
            RETURNING id, to_char(started_at, 'YYYY-MM-DD HH24:MI:SS') AS started_at,
                      to_char(ended_at, 'YYYY-MM-DD HH24:MI:SS') AS ended_at
        ");
        $stmt->execute(['id' => (int)$shift['id'], 'uid' => (int)$identity['user_id']]);
        $ended = $stmt->fetch(PDO::FETCH_ASSOC);

        echo json_encode([
            'success' => true,
            'message' => 'Shift ended.',
            'shift' => $ended
        ]);
        exit();
    }

    if ($action === 'summary') {
        $shift = get_active_shift($conn, (int)$identity['user_id']);
        if (!$shift) {
            // Fall back to the most recent ended shift today
            $recentStmt = $conn->prepare("
                SELECT ss.id, ss.store_id, ss.status,
                       to_char(ss.started_at, 'YYYY-MM-DD HH24:MI:SS') AS started_at,
                       to_char(ss.ended_at, 'YYYY-MM-DD HH24:MI:SS') AS ended_at
                FROM staff_shifts ss
                WHERE ss.user_id = :uid AND ss.status = 'ended' AND ss.ended_at::date = CURRENT_DATE
                ORDER BY ss.id DESC
                LIMIT 1
            ");
            $recentStmt->execute(['uid' => (int)$identity['user_id']]);
            $shift = $recentStmt->fetch(PDO::FETCH_ASSOC);
        }

        if (!$shift) {
            echo json_encode(['success' => true, 'active' => false, 'shift' => null, 'summary' => null]);
            exit();
        }

        $summaryStmt = $conn->prepare("
            SELECT COUNT(*) AS transaction_count,
                   COALESCE(SUM(t.total), 0) AS total_sales
            FROM transactions t
            WHERE t.user_id = :uid
              AND t.store_id = :sid
              AND t.status <> 'voided'
              AND t.created_at >= :started_at
              AND (:ended_at IS NULL OR t.created_at <= :ended_at)
        ");
        $summaryStmt->execute([
            'uid' => (int)$identity['user_id'],
            'sid' => (int)$shift['store_id'],
            'started_at' => $shift['started_at'],
            'ended_at' => $shift['ended_at']
        ]);
        $summary = $summaryStmt->fetch(PDO::FETCH_ASSOC);

        echo json_encode([
            'success' => true,
            'active' => $shift['status'] === 'active',
            'shift' => $shift,
            'summary' => $summary
        ]);
        exit();
    }

    auth_json_error(400, 'Invalid shift action');
} catch (PDOException $e) {
    if ($conn->inTransaction()) {
        $conn->rollBack();
    }
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'message' => 'An error occurred while processing the shift',
        'error' => $e->getMessage()
    ]);
    exit();
}
