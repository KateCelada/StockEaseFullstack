<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);
auth_require_roles($identity, ['store_admin', 'staff']);

$data = json_decode(file_get_contents("php://input"), true);
if (!is_array($data)) {
    echo json_encode(["success" => false, "message" => "No valid JSON payload received"]);
    exit;
}

$requestedStoreId = isset($data['store_id']) ? (int)$data['store_id'] : 0;
$storeId = auth_resolve_store_context($conn, $identity, $requestedStoreId, false);
$userId = $identity['user_id'];
$paymentType = isset($data['payment_type']) && is_string($data['payment_type'])
    ? trim($data['payment_type'])
    : '';
$items = $data['items'] ?? [];

if ($paymentType === '' || !is_array($items) || empty($items)) {
    echo json_encode([
        "success" => false,
        "message" => "A valid payment_type and transaction items are required"
    ]);
    exit;
}

$requestedItems = [];
$requestedQuantities = [];

foreach ($items as $item) {
    if (!is_array($item)) {
        echo json_encode(["success" => false, "message" => "Each transaction item must be an object"]);
        exit;
    }

    $productId = isset($item['product_id']) ? (int)$item['product_id'] : 0;
    $quantity = isset($item['quantity']) ? (int)$item['quantity'] : 0;

    if ($productId <= 0 || $quantity <= 0) {
        echo json_encode([
            "success" => false,
            "message" => "Each item requires a valid product_id and positive quantity"
        ]);
        exit;
    }

    $requestedItems[] = [
        'product_id' => $productId,
        'quantity' => $quantity
    ];
    $requestedQuantities[$productId] = ($requestedQuantities[$productId] ?? 0) + $quantity;
}

// Lock products in a consistent order to reduce deadlock risk for concurrent multi-item sales.
ksort($requestedQuantities, SORT_NUMERIC);

try {
    $conn->beginTransaction();

    $userStmt = $conn->prepare("SELECT id
                                FROM users
                                WHERE id = :user_id
                                  AND store_id = :store_id
                                  AND is_active = true
                                  AND role IN ('store_admin', 'staff')");
    $userStmt->execute(['user_id' => $userId, 'store_id' => $storeId]);
    if (!$userStmt->fetch()) {
        throw new RuntimeException("The transaction user is inactive, invalid, or assigned to another store");
    }

    $productStmt = $conn->prepare("SELECT id, store_id, price, cost_price, stock
                                   FROM products
                                   WHERE id = :product_id
                                   FOR UPDATE");
    $productPrices = [];
    $productCostPrices = [];
    $calculatedTotalCents = 0;

    foreach ($requestedQuantities as $productId => $requestedQuantity) {
        $productStmt->execute(['product_id' => $productId]);
        $product = $productStmt->fetch(PDO::FETCH_ASSOC);

        if (!$product) {
            throw new RuntimeException("A requested product does not exist");
        }
        if ((int)$product['store_id'] !== $storeId) {
            throw new RuntimeException("A requested product belongs to another store");
        }
        if ((int)$product['stock'] < $requestedQuantity) {
            throw new RuntimeException("Insufficient stock for one or more requested products");
        }

        $databasePrice = (string)$product['price'];
        $unitPriceCents = (int)round(((float)$databasePrice) * 100);
        if ($unitPriceCents < 0) {
            throw new RuntimeException("A requested product has an invalid database price");
        }
        if ($unitPriceCents > 0 && $requestedQuantity > intdiv(PHP_INT_MAX, $unitPriceCents)) {
            throw new RuntimeException("The calculated transaction total is too large");
        }

        $lineTotalCents = $unitPriceCents * $requestedQuantity;
        if ($calculatedTotalCents > PHP_INT_MAX - $lineTotalCents) {
            throw new RuntimeException("The calculated transaction total is too large");
        }

        $calculatedTotalCents += $lineTotalCents;
        $productPrices[$productId] = $databasePrice;
        $productCostPrices[$productId] = isset($product['cost_price']) ? (string)$product['cost_price'] : '0.00';
    }

    $calculatedTotal = number_format($calculatedTotalCents / 100, 2, '.', '');

    $transactionStmt = $conn->prepare("INSERT INTO transactions
        (store_id, user_id, total_amount, payment_type, status)
        VALUES (:store_id, :user_id, :total_amount, :payment_type, 'completed')
        RETURNING id");
    $transactionStmt->execute([
        'store_id' => $storeId,
        'user_id' => $userId,
        'total_amount' => $calculatedTotal,
        'payment_type' => $paymentType
    ]);
    $transactionId = (int)$transactionStmt->fetchColumn();

    $itemStmt = $conn->prepare("INSERT INTO transaction_items
        (transaction_id, product_id, quantity, unit_price, cost_price)
        VALUES (:transaction_id, :product_id, :quantity, :unit_price, :cost_price)");
    foreach ($requestedItems as $item) {
        $itemStmt->execute([
            'transaction_id' => $transactionId,
            'product_id' => $item['product_id'],
            'quantity' => $item['quantity'],
            'unit_price' => $productPrices[$item['product_id']],
            'cost_price' => $productCostPrices[$item['product_id']] ?? '0.00'
        ]);
    }

    $stockStmt = $conn->prepare("UPDATE products
                                 SET stock = stock - :quantity
                                 WHERE id = :product_id
                                   AND store_id = :store_id
                                   AND stock >= :quantity");
    foreach ($requestedQuantities as $productId => $requestedQuantity) {
        $stockStmt->execute([
            'quantity' => $requestedQuantity,
            'product_id' => $productId,
            'store_id' => $storeId
        ]);
        if ($stockStmt->rowCount() !== 1) {
            throw new RuntimeException("Stock changed before the transaction could be completed");
        }
    }

    $conn->commit();
    echo json_encode([
        "success" => true,
        "message" => "Transaction completed successfully",
        "transaction_id" => $transactionId,
        "total_amount" => (float)$calculatedTotal,
        "payment_type" => $paymentType
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
    echo json_encode(["success" => false, "message" => "Unable to complete transaction"]);
} catch (Throwable $e) {
    if ($conn->inTransaction()) {
        $conn->rollBack();
    }
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Unable to complete transaction"]);
}
?>
