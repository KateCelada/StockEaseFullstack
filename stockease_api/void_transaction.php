<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);
// Store admin or staff (with permission) can void a transaction
auth_require_roles($identity, ['super_admin', 'store_admin', 'staff']);

$rawBody = file_get_contents('php://input');
$data = json_decode($rawBody, true);
if (!is_array($data)) {
    $data = $_POST;
}

$transactionId = isset($data['transaction_id']) ? (int)$data['transaction_id'] : 0;
$reason = trim($data['reason'] ?? 'Customer refund / transaction voided');

if ($transactionId <= 0) {
    echo json_encode(["success" => false, "message" => "A valid transaction_id is required"]);
    exit;
}

try {
    $conn->beginTransaction();

    // Fetch the transaction
    $txnStmt = $conn->prepare("SELECT id, store_id, total_amount, status FROM transactions WHERE id = :id FOR UPDATE");
    $txnStmt->execute(['id' => $transactionId]);
    $txn = $txnStmt->fetch(PDO::FETCH_ASSOC);

    if (!$txn) {
        throw new RuntimeException("Transaction not found");
    }

    $txnStoreId = (int)$txn['store_id'];

    // Verify role / store isolation
    if ($identity['role'] !== 'super_admin' && (int)$identity['store_id'] !== $txnStoreId) {
        throw new RuntimeException("You do not have permission to void a transaction from another store");
    }

    if (isset($txn['status']) && strtolower($txn['status']) === 'voided') {
        throw new RuntimeException("This transaction has already been voided");
    }

    // Fetch transaction items to restore stock
    $itemsStmt = $conn->prepare("SELECT product_id, quantity FROM transaction_items WHERE transaction_id = :txn_id");
    $itemsStmt->execute(['txn_id' => $transactionId]);
    $items = $itemsStmt->fetchAll(PDO::FETCH_ASSOC);

    $restockStmt = $conn->prepare("UPDATE products SET stock = stock + :qty WHERE id = :product_id AND store_id = :store_id");
    foreach ($items as $item) {
        $pId = (int)$item['product_id'];
        $qty = (int)$item['quantity'];
        if ($pId > 0 && $qty > 0) {
            $restockStmt->execute([
                'qty' => $qty,
                'product_id' => $pId,
                'store_id' => $txnStoreId
            ]);
        }
    }

    // Update transaction status to voided
    $updateStmt = $conn->prepare("UPDATE transactions SET status = 'voided' WHERE id = :id");
    $updateStmt->execute(['id' => $transactionId]);

    // Record in audit log if audit_logs table exists
    try {
        $auditStmt = $conn->prepare("
            INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
            VALUES (?, ?, ?, ?, ?, ?)
        ");
        $auditStmt->execute([
            $identity['full_name'] ?? 'User',
            $identity['role'],
            'VOID_TRANSACTION',
            "Transaction #{$transactionId}",
            $identity['store_name'] ?? 'Store',
            "Voided transaction #{$transactionId} (Total: ₱{$txn['total_amount']}). Reason: {$reason}"
        ]);
    } catch (Throwable $e) {
        // Audit log fail should not block voiding
    }

    $conn->commit();

    echo json_encode([
        "success" => true,
        "message" => "Transaction voided successfully and product stock restored",
        "transaction_id" => $transactionId,
        "status" => "voided"
    ]);
} catch (RuntimeException $e) {
    if ($conn->inTransaction()) {
        $conn->rollBack();
    }
    echo json_encode(["success" => false, "message" => $e->getMessage()]);
} catch (PDOException $e) {
    if ($conn->inTransaction()) {
        $conn->rollBack();
    }
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Database error occurred while voiding transaction"]);
} catch (Throwable $e) {
    if ($conn->inTransaction()) {
        $conn->rollBack();
    }
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Unexpected error occurred"]);
}
?>
