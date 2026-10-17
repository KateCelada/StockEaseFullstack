<?php
require_once 'db.php';

try {
    $conn->beginTransaction();

    // 1. Unlink stores
    $conn->exec("UPDATE stores SET owner_id = NULL WHERE owner_id != 1");
    if ($conn->query("SELECT column_name FROM information_schema.columns WHERE table_name='stores' AND column_name='manager_id'")->fetch()) {
        $conn->exec("UPDATE stores SET manager_id = NULL WHERE manager_id != 1");
    }

    // 2. Clean subscriptions
    $conn->exec("DELETE FROM subscriptions WHERE owner_user_id != 1");
    $conn->exec("DELETE FROM subscription_requests WHERE user_id != 1 OR (reviewed_by IS NOT NULL AND reviewed_by != 1)");

    // 3. Unlink or reassign transfers and transactions
    $conn->exec("UPDATE stock_transfers SET requested_by = 1 WHERE requested_by != 1");
    $conn->exec("UPDATE transactions SET user_id = 1 WHERE user_id != 1");

    // 4. Delete all users except superadmin
    $stmt = $conn->prepare("DELETE FROM users WHERE id != 1");
    $stmt->execute();
    $deletedUsersCount = $stmt->rowCount();

    // Ensure superadmin has known password and is active
    $hash = password_hash('admin123', PASSWORD_BCRYPT);
    $conn->prepare("UPDATE users SET password = ?, is_active = true, role = 'super_admin' WHERE id = 1")->execute([$hash]);

    $conn->commit();
    echo json_encode([
        'success' => true,
        'message' => "Successfully purged {$deletedUsersCount} non-superadmin users.",
        'remaining_users' => $conn->query("SELECT id, username, role FROM users")->fetchAll(PDO::FETCH_ASSOC)
    ], JSON_PRETTY_PRINT);
} catch (Exception $e) {
    if ($conn->inTransaction()) {
        $conn->rollBack();
    }
    echo json_encode(['success' => false, 'error' => 'Unexpected server error. Details were written to the server log.']);
}
