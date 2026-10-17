<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: GET, POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/subscription_helper.php';

$identity = auth_require_user($conn);

$rawBody = file_get_contents('php://input');
$jsonBody = json_decode($rawBody, true);
if (!is_array($jsonBody)) {
    $jsonBody = [];
}

$action = $_GET['action'] ?? $_POST['action'] ?? ($jsonBody['action'] ?? 'fetch');

// -------------------------------------------------------------
// 1. FETCH SUBSCRIPTION & PENDING REQUESTS
// -------------------------------------------------------------
if ($action === 'fetch') {
    auth_require_roles($identity, ['super_admin', 'store_admin']);
    $currentSub = subscription_get_current($conn, $identity);

    // If store_admin, check if they have a pending or recently rejected subscription application
    $pendingRequest = null;
    $lastRequest = null;
    if ($identity['role'] === 'store_admin') {
        $prStmt = $conn->prepare("
            SELECT id, plan, billing_period, price, status, payment_reference, notes, rejection_reason,
                   created_at as created_at
            FROM subscription_requests
            WHERE user_id = :uid AND status = 'pending'
            ORDER BY id DESC LIMIT 1
        ");
        $prStmt->execute(['uid' => $identity['user_id']]);
        $pendingRequest = $prStmt->fetch(PDO::FETCH_ASSOC);

        $lastStmt = $conn->prepare("
            SELECT id, plan, billing_period, price, status, rejection_reason,
                   reviewed_at as reviewed_at
            FROM subscription_requests
            WHERE user_id = :uid
            ORDER BY id DESC LIMIT 1
        ");
        $lastStmt->execute(['uid' => $identity['user_id']]);
        $lastRequest = $lastStmt->fetch(PDO::FETCH_ASSOC);
    }

    // If super_admin, fetch all pending applications from store admins awaiting approval
    $pendingRequests = [];
    if ($identity['role'] === 'super_admin') {
        $prAllStmt = $conn->query("
            SELECT sr.id, sr.user_id, u.full_name as user_name, u.username,
                   sr.store_id, COALESCE(s.store_name, 'Unassigned') as store_name,
                   sr.plan, sr.billing_period, sr.price, sr.status,
                   sr.payment_reference, sr.notes,
                   sr.created_at as created_at
            FROM subscription_requests sr
            JOIN users u ON sr.user_id = u.id
            LEFT JOIN stores s ON sr.store_id = s.id
            WHERE sr.status = 'pending'
            ORDER BY sr.id DESC
        ");
        $pendingRequests = $prAllStmt->fetchAll(PDO::FETCH_ASSOC);
    }

    echo json_encode([
        'success' => true,
        'subscription' => $currentSub,
        'pricing' => SUBSCRIPTION_PRICING,
        'features' => PREMIUM_FEATURES,
        'pending_request' => $pendingRequest,
        'last_request' => $lastRequest,
        'pending_requests' => $pendingRequests,
        'pending_requests_count' => count($pendingRequests)
    ]);
    exit;
}

// -------------------------------------------------------------
// 2. STORE ADMIN APPLIES / REQUESTS SUBSCRIPTION
// -------------------------------------------------------------
if ($action === 'apply' || $action === 'request') {
    auth_require_roles($identity, ['store_admin', 'super_admin']);

    $input = !empty($jsonBody) ? $jsonBody : $_POST;

    $billingPeriod = strtolower(trim((string)($input['billing_period'] ?? 'monthly')));
    if (!in_array($billingPeriod, ['monthly', 'yearly'], true)) {
        auth_json_error(400, "Billing period must be 'monthly' or 'yearly'");
    }

    $pricing = SUBSCRIPTION_PRICING[$billingPeriod] ?? null;
    $price = $pricing ? $pricing['price'] : 699.00;
    $paymentRef = trim((string)($input['payment_reference'] ?? ''));
    $notes = trim((string)($input['notes'] ?? ''));
    $storeId = $identity['store_id'] ?? null;

    // Check if there is already an active pending application
    $checkStmt = $conn->prepare("SELECT id FROM subscription_requests WHERE user_id = :uid AND status = 'pending' LIMIT 1");
    $checkStmt->execute(['uid' => $identity['user_id']]);
    $existingId = $checkStmt->fetchColumn();

    if ($existingId) {
        $upStmt = $conn->prepare("
            UPDATE subscription_requests
            SET billing_period = :period, price = :price, payment_reference = :ref, notes = :notes, created_at = CURRENT_TIMESTAMP
            WHERE id = :id
        ");
        $upStmt->execute([
            'period' => $billingPeriod,
            'price' => $price,
            'ref' => $paymentRef,
            'notes' => $notes,
            'id' => $existingId
        ]);
        $requestId = (int)$existingId;
    } else {
        $insStmt = $conn->prepare("
            INSERT INTO subscription_requests (user_id, store_id, plan, billing_period, price, status, payment_reference, notes)
            VALUES (:uid, :sid, 'premium', :period, :price, 'pending', :ref, :notes)
        ");
        $insStmt->execute([
            'uid' => $identity['user_id'],
            'sid' => $storeId,
            'period' => $billingPeriod,
            'price' => $price,
            'ref' => $paymentRef,
            'notes' => $notes
        ]);
        $requestId = (int)$conn->lastInsertId();
    }

    // Audit log
    $auditStmt = $conn->prepare("
        INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
        VALUES (?, ?, ?, ?, ?, ?)
    ");
    $auditStmt->execute([
        $identity['full_name'],
        $identity['role'],
        'SUBSCRIPTION_REQUESTED',
        "Request #$requestId",
        "Store #" . ($storeId ?? 'N/A'),
        "Store Admin {$identity['full_name']} applied for StockEase Premium ($billingPeriod, ₱$price). Awaiting Super Admin approval."
    ]);

    echo json_encode([
        'success' => true,
        'message' => "Application for StockEase Premium (" . ucfirst($billingPeriod) . ") submitted successfully. Super Admin has been notified and must approve it before features unlock.",
        'request_id' => $requestId,
        'billing_period' => $billingPeriod,
        'price' => $price,
        'status' => 'pending'
    ]);
    exit;
}

// -------------------------------------------------------------
// 3. SUPER ADMIN APPROVES SUBSCRIPTION REQUEST
// -------------------------------------------------------------
if ($action === 'approve_request') {
    auth_require_roles($identity, ['super_admin']);

    $input = !empty($jsonBody) ? $jsonBody : $_POST;

    $requestId = (int)($input['request_id'] ?? 0);
    if ($requestId <= 0) {
        auth_json_error(400, 'Invalid subscription request ID');
    }

    $conn->beginTransaction();

    $stmt = $conn->prepare("
        SELECT sr.*, u.full_name as user_name, s.store_name
        FROM subscription_requests sr
        JOIN users u ON sr.user_id = u.id
        LEFT JOIN stores s ON sr.store_id = s.id
        WHERE sr.id = :id FOR UPDATE OF sr
    ");
    $stmt->execute(['id' => $requestId]);
    $req = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$req) {
        $conn->rollBack();
        auth_json_error(404, 'Subscription request not found');
    }

    if ($req['status'] !== 'pending') {
        $conn->rollBack();
        auth_json_error(400, "Request is already {$req['status']}. Only pending requests can be approved.");
    }

    $period = strtolower($req['billing_period']);
    $now = new DateTime('now');
    $startsAt = $now->format('Y-m-d H:i:s');

    if ($period === 'yearly') {
        $renewDate = clone $now;
        $renewDate->modify('+1 year');
        $expiresAt = $renewDate->format('Y-m-d H:i:s');
    } else {
        // Monthly
        $renewDate = clone $now;
        $renewDate->modify('+1 month');
        $expiresAt = $renewDate->format('Y-m-d H:i:s');
    }

    // 1. Mark request as approved
    $upReq = $conn->prepare("
        UPDATE subscription_requests
        SET status = 'approved', reviewed_by = :reviewer, reviewed_at = CURRENT_TIMESTAMP
        WHERE id = :id
    ");
    $upReq->execute([
        'reviewer' => $identity['user_id'],
        'id' => $requestId
    ]);

    // 2. Activate Premium in subscriptions table
    $ownerUserId = subscription_get_owner_user_id($conn, $identity);
    $subStmt = $conn->prepare("
        INSERT INTO subscriptions (owner_user_id, plan, billing_period, status, starts_at, expires_at, updated_at)
        VALUES (:owner_id, 'premium', :period, 'active', :starts_at, :expires_at, CURRENT_TIMESTAMP)
        ON DUPLICATE KEY UPDATE
            plan = 'premium',
            billing_period = VALUES(billing_period),
            status = 'active',
            starts_at = VALUES(starts_at),
            expires_at = VALUES(expires_at),
            updated_at = CURRENT_TIMESTAMP
    ");
    $subStmt->execute([
        'owner_id' => $ownerUserId,
        'period' => $period,
        'starts_at' => $startsAt,
        'expires_at' => $expiresAt
    ]);

    // 3. Audit log
    $auditStmt = $conn->prepare("
        INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
        VALUES (?, ?, ?, ?, ?, ?)
    ");
    $auditStmt->execute([
        $identity['full_name'],
        $identity['role'],
        'SUBSCRIPTION_APPROVED',
        "Request #$requestId",
        $req['store_name'] ?? 'Global',
        "Super Admin {$identity['full_name']} APPROVED Premium ($period) for Store Admin {$req['user_name']} ({$req['store_name']}). Valid until $expiresAt."
    ]);

    $conn->commit();

    echo json_encode([
        'success' => true,
        'message' => "Subscription request #$requestId approved. Premium features unlocked for {$req['user_name']} until " . date('M d, Y', strtotime($expiresAt)) . ".",
        'request_id' => $requestId,
        'status' => 'approved',
        'expires_at' => $expiresAt
    ]);
    exit;
}

// -------------------------------------------------------------
// 4. SUPER ADMIN REJECTS SUBSCRIPTION REQUEST
// -------------------------------------------------------------
if ($action === 'reject_request') {
    auth_require_roles($identity, ['super_admin']);

    $input = !empty($jsonBody) ? $jsonBody : $_POST;

    $requestId = (int)($input['request_id'] ?? 0);
    $reason = trim((string)($input['reason'] ?? 'Declined by Super Admin'));

    if ($requestId <= 0) {
        auth_json_error(400, 'Invalid subscription request ID');
    }

    $upReq = $conn->prepare("
        UPDATE subscription_requests
        SET status = 'rejected', rejection_reason = :reason, reviewed_by = :reviewer, reviewed_at = CURRENT_TIMESTAMP
        WHERE id = :id AND status = 'pending'
    ");
    $upReq->execute([
        'reason' => $reason,
        'reviewer' => $identity['user_id'],
        'id' => $requestId
    ]);

    if ($upReq->rowCount() === 0) {
        auth_json_error(404, 'Pending subscription request not found');
    }

    // Audit log
    $auditStmt = $conn->prepare("
        INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
        VALUES (?, ?, ?, ?, ?, ?)
    ");
    $auditStmt->execute([
        $identity['full_name'],
        $identity['role'],
        'SUBSCRIPTION_REJECTED',
        "Request #$requestId",
        'Global System',
        "Super Admin rejected subscription request #$requestId. Reason: $reason"
    ]);

    echo json_encode([
        'success' => true,
        'message' => "Subscription request #$requestId has been declined."
    ]);
    exit;
}

// -------------------------------------------------------------
// 5. SIMULATE / DIRECT OVERRIDE (FOR SANDBOX TESTING)
// -------------------------------------------------------------
if ($action === 'simulate_checkout' || $action === 'update_plan') {
    $input = !empty($jsonBody) ? $jsonBody : $_POST;

    // If a Store Admin initiates checkout or plan update, they cannot bypass Super Admin approval!
    // We automatically route their request to the subscription application queue ('apply')
    if ($identity['role'] === 'store_admin') {

        $targetPlan = strtolower(trim((string)($input['plan'] ?? 'premium')));
        if ($targetPlan === 'free') {
            auth_json_error(403, 'Store administrators cannot cancel or downgrade global store subscriptions.');
        }

        $billingPeriod = strtolower(trim((string)($input['billing_period'] ?? 'monthly')));
        if (!in_array($billingPeriod, ['monthly', 'yearly'], true)) {
            $billingPeriod = 'monthly';
        }

        $pricing = SUBSCRIPTION_PRICING[$billingPeriod] ?? null;
        $price = $pricing ? $pricing['price'] : 699.00;
        $paymentRef = trim((string)($input['payment_reference'] ?? ''));
        $notes = trim((string)($input['notes'] ?? ''));
        $storeId = $identity['store_id'] ?? null;

        // Check if there is already an active pending application
        $checkStmt = $conn->prepare("SELECT id FROM subscription_requests WHERE user_id = :uid AND status = 'pending' LIMIT 1");
        $checkStmt->execute(['uid' => $identity['user_id']]);
        $existingId = $checkStmt->fetchColumn();

        if ($existingId) {
            $upStmt = $conn->prepare("
                UPDATE subscription_requests
                SET billing_period = :period, price = :price, payment_reference = :ref, notes = :notes, created_at = CURRENT_TIMESTAMP
                WHERE id = :id
            ");
            $upStmt->execute([
                'period' => $billingPeriod,
                'price' => $price,
                'ref' => $paymentRef,
                'notes' => $notes,
                'id' => $existingId
            ]);
            $requestId = (int)$existingId;
        } else {
            $insStmt = $conn->prepare("
                INSERT INTO subscription_requests (user_id, store_id, plan, billing_period, price, status, payment_reference, notes)
                VALUES (:uid, :sid, 'premium', :period, :price, 'pending', :ref, :notes)
            ");
            $insStmt->execute([
                'uid' => $identity['user_id'],
                'sid' => $storeId,
                'period' => $billingPeriod,
                'price' => $price,
                'ref' => $paymentRef,
                'notes' => $notes
            ]);
            $requestId = (int)$conn->lastInsertId();
        }

        // Audit log
        $auditStmt = $conn->prepare("
            INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
            VALUES (?, ?, ?, ?, ?, ?)
        ");
        $auditStmt->execute([
            $identity['full_name'],
            $identity['role'],
            'SUBSCRIPTION_REQUESTED',
            "Request #$requestId",
            "Store #" . ($storeId ?? 'N/A'),
            "Store Admin {$identity['full_name']} applied for StockEase Premium ($billingPeriod, ₱$price). Awaiting Super Admin approval."
        ]);

        echo json_encode([
            'success' => true,
            'message' => "Application for StockEase Premium (" . ucfirst($billingPeriod) . ") submitted successfully. Super Admin has been notified and must approve it before features unlock.",
            'request_id' => $requestId,
            'billing_period' => $billingPeriod,
            'price' => $price,
            'status' => 'pending'
        ]);
        exit;
    }

    // Direct sandbox simulations are restricted to super_admin only
    auth_require_roles($identity, ['super_admin']);

    $input = json_decode(file_get_contents('php://input'), true);
    if (!is_array($input)) {
        $input = $_POST;
    }

    $targetPlan = strtolower(trim((string)($input['plan'] ?? 'free')));
    $targetPeriod = isset($input['billing_period']) ? strtolower(trim((string)$input['billing_period'])) : null;
    $targetStatus = strtolower(trim((string)($input['status'] ?? 'active')));

    if (!in_array($targetPlan, ['free', 'premium'], true)) {
        auth_json_error(400, 'Invalid subscription plan');
    }

    if ($targetPlan === 'premium') {
        if (!in_array($targetPeriod, ['monthly', 'yearly'], true)) {
            auth_json_error(400, 'Billing period must be monthly or yearly for Premium plans');
        }
        if (!in_array($targetStatus, ['active', 'trial', 'expired', 'cancelled'], true)) {
            $targetStatus = 'active';
        }
    } else {
        $targetPeriod = null;
        $targetStatus = 'active';
    }

    $ownerUserId = subscription_get_owner_user_id($conn, $identity);

    $now = new DateTime('now');
    $startsAt = $now->format('Y-m-d H:i:s');
    $expiresAt = null;
    $trialEndsAt = null;

    if ($targetPlan === 'premium') {
        if ($targetStatus === 'trial') {
            $trialDate = clone $now;
            $trialDate->modify('+14 days');
            $trialEndsAt = $trialDate->format('Y-m-d H:i:s');
            $expiresAt = $trialEndsAt;
        } elseif ($targetPeriod === 'yearly') {
            $renewDate = clone $now;
            $renewDate->modify('+1 year');
            $expiresAt = $renewDate->format('Y-m-d H:i:s');
        } else {
            // Monthly
            $renewDate = clone $now;
            $renewDate->modify('+1 month');
            $expiresAt = $renewDate->format('Y-m-d H:i:s');
        }

        if ($targetStatus === 'expired') {
            $pastDate = clone $now;
            $pastDate->modify('-1 day');
            $expiresAt = $pastDate->format('Y-m-d H:i:s');
        }
    }

    try {
        $conn->beginTransaction();

        $stmt = $conn->prepare("
            INSERT INTO subscriptions (owner_user_id, plan, billing_period, status, starts_at, expires_at, trial_ends_at, updated_at)
            VALUES (:owner_id, :plan, :period, :status, :starts_at, :expires_at, :trial_ends_at, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE
                plan = VALUES(plan),
                billing_period = VALUES(billing_period),
                status = VALUES(status),
                starts_at = VALUES(starts_at),
                expires_at = VALUES(expires_at),
                trial_ends_at = VALUES(trial_ends_at),
                updated_at = CURRENT_TIMESTAMP
        ");

        $stmt->execute([
            'owner_id' => $ownerUserId,
            'plan' => $targetPlan,
            'period' => $targetPeriod,
            'status' => $targetStatus,
            'starts_at' => $startsAt,
            'expires_at' => $expiresAt,
            'trial_ends_at' => $trialEndsAt
        ]);

        $auditDetails = sprintf(
            "Subscription updated to %s (%s, %s). Expires: %s",
            strtoupper($targetPlan),
            $targetPeriod ?? 'N/A',
            strtoupper($targetStatus),
            $expiresAt ?? 'N/A'
        );

        $auditStmt = $conn->prepare("
            INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
            VALUES (?, ?, ?, ?, ?, ?)
        ");
        $auditStmt->execute([
            $identity['full_name'] ?? 'Super Admin',
            $identity['role'] ?? 'super_admin',
            'SUBSCRIPTION_UPDATE',
            'Business Plan',
            'Global System',
            $auditDetails
        ]);

        $conn->commit();

        $updatedSub = subscription_get_current($conn, $identity);
        echo json_encode([
            'success' => true,
            'message' => 'Subscription updated successfully',
            'subscription' => $updatedSub
        ]);
        exit;
    } catch (Exception $e) {
        if ($conn->inTransaction()) {
            $conn->rollBack();
        }
        http_response_code(500);
        echo json_encode([
            'success' => false,
            'message' => 'Failed to update subscription',
            'error' => 'Unexpected server error. Details were written to the server log.'
        ]);
        exit;
    }
}

auth_json_error(400, 'Invalid subscription action');
