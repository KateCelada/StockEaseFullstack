<?php

function auth_start_session(): void
{
    if (session_status() === PHP_SESSION_ACTIVE) {
        return;
    }

    ini_set('session.use_only_cookies', '1');
    ini_set('session.use_strict_mode', '1');
    ini_set('session.cookie_httponly', '1');

    $isHttps = !empty($_SERVER['HTTPS']) && strtolower((string)$_SERVER['HTTPS']) !== 'off';
    session_name('STOCKEASESESSID');
    session_set_cookie_params([
        'lifetime' => 0,
        'path' => '/',
        'secure' => $isHttps,
        'httponly' => true,
        'samesite' => 'Lax'
    ]);
    session_start();
}

function auth_json_error(int $statusCode, string $message): void
{
    http_response_code($statusCode);
    echo json_encode(['success' => false, 'message' => $message]);
    exit;
}

function auth_is_active($value): bool
{
    return in_array($value, [true, 1, '1', 't', 'true'], true);
}

function auth_store_identity(array $user): array
{
    $identity = [
        'user_id' => (int)$user['id'],
        'role' => (string)$user['role'],
        'username' => (string)($user['username'] ?? ''),
        'store_id' => $user['store_id'] !== null ? (int)$user['store_id'] : null,
        'full_name' => (string)$user['full_name']
    ];

    $_SESSION['user_id'] = $identity['user_id'];
    $_SESSION['role'] = $identity['role'];
    $_SESSION['username'] = $identity['username'];
    $_SESSION['store_id'] = $identity['store_id'];
    $_SESSION['full_name'] = $identity['full_name'];

    return $identity;
}

function auth_clear_session(): void
{
    auth_start_session();
    $_SESSION = [];

    if (ini_get('session.use_cookies')) {
        $params = session_get_cookie_params();
        setcookie(session_name(), '', [
            'expires' => time() - 42000,
            'path' => $params['path'],
            'domain' => $params['domain'],
            'secure' => $params['secure'],
            'httponly' => $params['httponly'],
            'samesite' => $params['samesite'] ?? 'Lax'
        ]);
    }

    session_destroy();
}

function auth_require_user(PDO $conn): array
{
    auth_start_session();
    $userId = isset($_SESSION['user_id']) ? (int)$_SESSION['user_id'] : 0;
    if ($userId <= 0) {
        auth_json_error(401, 'Authentication required');
    }

    $stmt = $conn->prepare("SELECT id, role, store_id, full_name, is_active
                            FROM users
                            WHERE id = :id
                            LIMIT 1");
    $stmt->execute(['id' => $userId]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    $canonicalRoles = ['super_admin', 'store_owner', 'manager', 'store_admin', 'staff', 'inventory_checker'];
    if (!$user || !auth_is_active($user['is_active']) || !in_array($user['role'], $canonicalRoles, true)) {
        auth_clear_session();
        auth_json_error(401, 'Authentication is no longer valid');
    }

    return auth_store_identity($user);
}

function auth_require_roles(array $identity, array $allowedRoles): void
{
    // Super admins have access to all operations
    if ($identity['role'] === 'super_admin') {
        return;
    }
    // Store owners and Managers have all store_admin and manager privileges
    if (($identity['role'] === 'store_owner' || $identity['role'] === 'manager') &&
        (in_array('store_admin', $allowedRoles, true) || in_array('manager', $allowedRoles, true) || in_array('store_owner', $allowedRoles, true))) {
        return;
    }
    // Inventory Checkers have inventory and staff privileges
    if ($identity['role'] === 'inventory_checker' &&
        (in_array('staff', $allowedRoles, true) || in_array('inventory_checker', $allowedRoles, true))) {
        return;
    }
    if (!in_array($identity['role'], $allowedRoles, true)) {
        auth_json_error(403, 'You are not authorized to perform this operation');
    }
}

function auth_store_exists(PDO $conn, int $storeId): bool
{
    if ($storeId <= 0) {
        return false;
    }

    $stmt = $conn->prepare('SELECT id FROM stores WHERE id = :id');
    $stmt->execute(['id' => $storeId]);
    return (bool)$stmt->fetchColumn();
}

function auth_resolve_store_context(
    PDO $conn,
    array $identity,
    int $requestedStoreId,
    bool $allowSuperAdminSelection
): int {
    if ($identity['role'] === 'super_admin') {
        if ($requestedStoreId > 0 && auth_store_exists($conn, $requestedStoreId)) {
            return $requestedStoreId;
        }
        $firstSid = (int)$conn->query("SELECT id FROM stores ORDER BY id ASC LIMIT 1")->fetchColumn();
        return $firstSid ?: 1;
    }

    $assignedStoreId = (int)($identity['store_id'] ?? 0);

    // If requested store is different, verify if the user is the owner/manager of the store
    if ($requestedStoreId > 0) {
        $ownerStmt = $conn->prepare("SELECT id FROM stores WHERE id = :sid AND (owner_id = :uid OR manager_id = :uid)");
        $ownerStmt->execute(['sid' => $requestedStoreId, 'uid' => $identity['user_id']]);
        if ($ownerStmt->fetchColumn()) {
            return $requestedStoreId;
        }
    }

    if ($assignedStoreId > 0 && auth_store_exists($conn, $assignedStoreId)) {
        return $assignedStoreId;
    }

    if ($requestedStoreId > 0 && auth_store_exists($conn, $requestedStoreId)) {
        return $requestedStoreId;
    }

    auth_json_error(403, 'The authenticated account has no valid store assignment');
    return 0;
}

auth_start_session();

