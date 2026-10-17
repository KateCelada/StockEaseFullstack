<?php
/**
 * Manual ("Other") sales - transactions recorded outside the POS.
 *
 * These rows carry transactions.source = 'manual' and surface in the
 * "Other Sales" section of the store report next to voided/refunded
 * transactions. Available to the web admin panel and the store-admin app.
 */
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: GET, POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);
// Recording a sale is a manager-level action; staff/cashiers stay POS-only
auth_require_roles($identity, ['super_admin', 'manager', 'store_admin']);

$action = isset($_GET['action']) ? $_GET['action'] : '';

if ($action === 'create') {
    $input = json_decode(file_get_contents('php://input'), true);
    if (!is_array($input)) {
        $input = $_POST;
    }

    $requestedStoreId = isset($input['store_id']) ? (int)$input['store_id'] : 0;
    $storeId = auth_resolve_store_context($conn, $identity, $requestedStoreId, true);

    $paymentType = trim($input['payment_type'] ?? 'Cash');
    $notes = trim($input['notes'] ?? '');
    $items = isset($input['items']) && is_array($input['items']) ? $input['items'] : [];

    if (empty($items)) {
        echo json_encode(["success" => false, "message" => "At least one item is required."]);
        exit;
    }

    try {
        $conn->beginTransaction();

        $validatedItems = [];
        $productStmt = $conn->prepare("
            SELECT id, product_name, price, COALESCE(cost_price, 0.0) AS cost_price, stock
            FROM products
            WHERE id = :pid AND store_id = :sid
            FOR UPDATE
        ");

        foreach ($items as $raw) {
            $productId = (int)($raw['product_id'] ?? 0);
            $quantity = (int)($raw['quantity'] ?? 0);

            if ($productId <= 0 || $quantity <= 0) {
                throw new RuntimeException("Each item needs a product_id and a positive quantity.");
            }

            $productStmt->execute(['pid' => $productId, 'sid' => $storeId]);
            $product = $productStmt->fetch(PDO::FETCH_ASSOC);
            if (!$product) {
                throw new RuntimeException("Product #$productId was not found in this store.");
            }
            if ((int)$product['stock'] < $quantity) {
                throw new RuntimeException("Only {$product['stock']} unit(s) of {$product['product_name']} are in stock.");
            }

            // Price may be overridden (bulk deals, barter, etc.); default = shelf price
            $unitPrice = isset($raw['unit_price']) && $raw['unit_price'] !== ''
                ? number_format((float)$raw['unit_price'], 2, '.', '')
                : number_format((float)$product['price'], 2, '.', '');

            if ((float)$unitPrice < 0) {
                throw new RuntimeException("Unit price for {$product['product_name']} cannot be negative.");
            }

            $validatedItems[] = [
                'product_id' => $productId,
                'product_name' => $product['product_name'],
                'quantity' => $quantity,
                'unit_price' => $unitPrice,
                'cost_price' => number_format((float)$product['cost_price'], 2, '.', '')
            ];
        }

        $totalCents = 0;
        foreach ($validatedItems as $item) {
            $totalCents += (int)round(((float)$item['unit_price']) * 100) * $item['quantity'];
        }
        $totalAmount = number_format($totalCents / 100, 2, '.', '');

        $txnStmt = $conn->prepare("INSERT INTO transactions
            (store_id, user_id, total_amount, payment_type, status, source)
            VALUES (:store_id, :user_id, :total_amount, :payment_type, 'completed', 'manual')");
        $txnStmt->execute([
            'store_id' => $storeId,
            'user_id' => (int)$identity['user_id'],
            'total_amount' => $totalAmount,
            'payment_type' => $paymentType !== '' ? $paymentType : 'Cash'
        ]);
        $transactionId = (int)$conn->lastInsertId();

        $itemStmt = $conn->prepare("INSERT INTO transaction_items
            (transaction_id, product_id, quantity, unit_price, cost_price)
            VALUES (:transaction_id, :product_id, :quantity, :unit_price, :cost_price)");
        $stockStmt = $conn->prepare("UPDATE products SET stock = stock - :qty WHERE id = :pid AND store_id = :sid");

        foreach ($validatedItems as $item) {
            $itemStmt->execute([
                'transaction_id' => $transactionId,
                'product_id' => $item['product_id'],
                'quantity' => $item['quantity'],
                'unit_price' => $item['unit_price'],
                'cost_price' => $item['cost_price']
            ]);
            $stockStmt->execute(['qty' => $item['quantity'], 'pid' => $item['product_id'], 'sid' => $storeId]);
        }

        try {
            $conn->prepare("INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
                            VALUES (?, ?, ?, ?, ?, ?)")
                ->execute([
                    $identity['full_name'] ?? 'User',
                    $identity['role'],
                    'MANUAL_SALE_RECORDED',
                    "Transaction #{$transactionId}",
                    $identity['store_name'] ?? "Store #{$storeId}",
                    "Recorded a manual (Other) sale worth ₱{$totalAmount}" .
                        ($notes !== '' ? " — Notes: {$notes}" : '')
                ]);
        } catch (Throwable $e) {
            // Audit log failure must not block the sale
        }

        $conn->commit();

        echo json_encode([
            "success" => true,
            "message" => "Manual sale recorded.",
            "transaction_id" => $transactionId,
            "total_amount" => $totalAmount,
            "item_count" => count($validatedItems)
        ]);
    } catch (RuntimeException $e) {
        if ($conn->inTransaction()) $conn->rollBack();
        echo json_encode(["success" => false, "message" => $e->getMessage()]);
    } catch (PDOException $e) {
        if ($conn->inTransaction()) $conn->rollBack();
        http_response_code(500);
        echo json_encode(["success" => false, "message" => "Database error while recording the manual sale."]);
    }
    exit;
}

if ($action === 'list') {
    $requestedStoreId = isset($_GET['store_id']) ? (int)$_GET['store_id'] : 0;
    $storeId = auth_resolve_store_context($conn, $identity, $requestedStoreId, true);

    try {
        $stmt = $conn->prepare("
            SELECT t.id, t.total_amount, t.payment_type, t.status, t.created_at,
                   COALESCE(u.full_name, 'Unknown user') AS recorded_by,
                   GROUP_CONCAT(CONCAT(ti.quantity, 'x ', COALESCE(p.product_name, 'Item')) SEPARATOR '\n') AS items_summary
            FROM transactions t
            LEFT JOIN users u ON t.user_id = u.id
            LEFT JOIN transaction_items ti ON ti.transaction_id = t.id
            LEFT JOIN products p ON ti.product_id = p.id
            WHERE t.store_id = :store_id AND t.source = 'manual'
            GROUP BY t.id, t.total_amount, t.payment_type, t.status, t.created_at, u.full_name
            ORDER BY t.created_at DESC
            LIMIT 100
        ");
        $stmt->execute(['store_id' => $storeId]);

        echo json_encode([
            "success" => true,
            "store_id" => $storeId,
            "manual_sales" => $stmt->fetchAll(PDO::FETCH_ASSOC)
        ]);
    } catch (PDOException $e) {
        http_response_code(500);
        echo json_encode(["success" => false, "message" => "Unable to load manual sales."]);
    }
    exit;
}

echo json_encode(["success" => false, "message" => "Invalid action parameter."]);
?>
