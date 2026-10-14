<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: GET, POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/subscription_helper.php';

$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin', 'store_admin', 'manager']);

// Server-side Premium Enforcement
subscription_require_premium($conn, $identity);

$rawBody = file_get_contents('php://input');
$jsonBody = json_decode($rawBody, true);
if (!is_array($jsonBody)) {
    $jsonBody = [];
}

$action = $_GET['action'] ?? $_POST['action'] ?? ($jsonBody['action'] ?? 'list');

/**
 * Helper to ensure a product exists in destination store when receiving a transfer.
 */
function get_or_create_destination_product(PDO $conn, int $toStoreId, int $sourceProductId): int
{
    // Fetch source product details
    $srcStmt = $conn->prepare("SELECT product_name, category, category_id, price FROM products WHERE id = :id");
    $srcStmt->execute(['id' => $sourceProductId]);
    $src = $srcStmt->fetch(PDO::FETCH_ASSOC);

    if (!$src) {
        throw new Exception("Source product record #$sourceProductId does not exist.");
    }

    // Check if destination store already has a product with identical name
    $destStmt = $conn->prepare("SELECT id FROM products WHERE store_id = :store_id AND LOWER(TRIM(product_name)) = LOWER(TRIM(:name)) LIMIT 1");
    $destStmt->execute(['store_id' => $toStoreId, 'name' => $src['product_name']]);
    $destProductId = $destStmt->fetchColumn();

    if ($destProductId) {
        return (int)$destProductId;
    }

    // Create corresponding product entry in destination store with 0 initial stock
    $createStmt = $conn->prepare("
        INSERT INTO products (store_id, product_name, category, category_id, price, stock)
        VALUES (:store_id, :name, :category, :category_id, :price, 0)
        RETURNING id
    ");
    $createStmt->execute([
        'store_id' => $toStoreId,
        'name' => $src['product_name'],
        'category' => $src['category'] ?? 'General',
        'category_id' => $src['category_id'] ?? null,
        'price' => $src['price'] ?? 0.00
    ]);

    return (int)$createStmt->fetchColumn();
}

try {
    if ($action === 'list') {
        $filterStatus = $_GET['status'] ?? '';
        $filterStore = isset($_GET['store_id']) ? (int)$_GET['store_id'] : 0;

        $where = ["1=1"];
        $params = [];

        // Store Admin isolation: can only inspect transfers involving their store
        if ($identity['role'] === 'store_admin') {
            $userStoreId = (int)($identity['store_id'] ?? 0);
            $where[] = "(st.from_store_id = :user_store OR st.to_store_id = :user_store)";
            $params['user_store'] = $userStoreId;
        } elseif ($identity['role'] === 'manager') {
            $mgrId = (int)$identity['user_id'];
            $where[] = "(st.from_store_id IN (SELECT id FROM stores WHERE owner_id = :mgr_id) OR st.to_store_id IN (SELECT id FROM stores WHERE owner_id = :mgr_id))";
            $params['mgr_id'] = $mgrId;
        } elseif ($filterStore > 0) {
            $where[] = "(st.from_store_id = :filter_store OR st.to_store_id = :filter_store)";
            $params['filter_store'] = $filterStore;
        }

        if (!empty($filterStatus)) {
            $where[] = "st.status = :status";
            $params['status'] = strtolower($filterStatus);
        }

        $whereClause = implode(" AND ", $where);

        $sql = "
            SELECT st.id, st.from_store_id, sf.store_name AS from_store_name,
                   st.to_store_id, st_dest.store_name AS to_store_name,
                   st.requested_by, u.full_name AS requested_by_name,
                   st.status, st.notes,
                   to_char(st.created_at, 'YYYY-MM-DD HH24:MI:SS') AS created_at,
                   to_char(st.dispatched_at, 'YYYY-MM-DD HH24:MI:SS') AS dispatched_at,
                   to_char(st.received_at, 'YYYY-MM-DD HH24:MI:SS') AS received_at,
                   to_char(st.completed_at, 'YYYY-MM-DD HH24:MI:SS') AS completed_at,
                   COALESCE(COUNT(sti.id), 0) AS total_items,
                   COALESCE(SUM(sti.quantity), 0) AS total_quantity
            FROM stock_transfers st
            JOIN stores sf ON st.from_store_id = sf.id
            JOIN stores st_dest ON st.to_store_id = st_dest.id
            JOIN users u ON st.requested_by = u.id
            LEFT JOIN stock_transfer_items sti ON st.id = sti.transfer_id
            WHERE $whereClause
            GROUP BY st.id, sf.store_name, st_dest.store_name, u.full_name
            ORDER BY st.id DESC
            LIMIT 100
        ";

        $stmt = $conn->prepare($sql);
        $stmt->execute($params);
        $transfers = $stmt->fetchAll(PDO::FETCH_ASSOC);

        echo json_encode([
            'success' => true,
            'transfers' => $transfers
        ]);
        exit;
    }

    if ($action === 'detail') {
        $transferId = (int)($_GET['id'] ?? 0);
        if ($transferId <= 0) {
            auth_json_error(400, 'Invalid transfer ID');
        }

        $stmt = $conn->prepare("
            SELECT st.id, st.from_store_id, sf.store_name AS from_store_name,
                   st.to_store_id, st_dest.store_name AS to_store_name,
                   st.requested_by, u.full_name AS requested_by_name,
                   st.status, st.notes,
                   to_char(st.created_at, 'YYYY-MM-DD HH24:MI:SS') AS created_at,
                   to_char(st.dispatched_at, 'YYYY-MM-DD HH24:MI:SS') AS dispatched_at,
                   to_char(st.received_at, 'YYYY-MM-DD HH24:MI:SS') AS received_at,
                   to_char(st.completed_at, 'YYYY-MM-DD HH24:MI:SS') AS completed_at
            FROM stock_transfers st
            JOIN stores sf ON st.from_store_id = sf.id
            JOIN stores st_dest ON st.to_store_id = st_dest.id
            JOIN users u ON st.requested_by = u.id
            WHERE st.id = :id
            LIMIT 1
        ");
        $stmt->execute(['id' => $transferId]);
        $transfer = $stmt->fetch(PDO::FETCH_ASSOC);

        if (!$transfer) {
            auth_json_error(404, 'Transfer not found');
        }

        // Scope check for store admin
        if ($identity['role'] === 'store_admin') {
            $userStore = (int)$identity['store_id'];
            if ((int)$transfer['from_store_id'] !== $userStore && (int)$transfer['to_store_id'] !== $userStore) {
                auth_json_error(403, 'Unauthorized access to transfer record');
            }
        }

        $itemsStmt = $conn->prepare("
            SELECT sti.id, sti.product_id, p.product_name, sti.quantity, p.stock AS current_source_stock
            FROM stock_transfer_items sti
            JOIN products p ON sti.product_id = p.id
            WHERE sti.transfer_id = :id
        ");
        $itemsStmt->execute(['id' => $transferId]);
        $items = $itemsStmt->fetchAll(PDO::FETCH_ASSOC);

        $transfer['items'] = $items;
        echo json_encode(['success' => true, 'transfer' => $transfer]);
        exit;
    }

    if ($action === 'create') {
        $input = !empty($jsonBody) ? $jsonBody : $_POST;

        $fromStoreId = (int)($input['from_store_id'] ?? 0);
        $toStoreId = (int)($input['to_store_id'] ?? 0);
        $productId = (int)($input['product_id'] ?? 0);
        $quantity = (int)($input['quantity'] ?? 0);
        $notes = trim((string)($input['notes'] ?? ''));

        if ($fromStoreId <= 0 || $toStoreId <= 0 || $productId <= 0 || $quantity <= 0) {
            auth_json_error(400, 'From Store, To Store, Product, and positive Quantity are required.');
        }

        if ($fromStoreId === $toStoreId) {
            auth_json_error(400, 'Source and destination store must be different.');
        }

        // Store Admin role authorization: Must be assigned to either source or destination store
        if ($identity['role'] === 'store_admin') {
            $assignedStore = (int)($identity['store_id'] ?? 0);
            if ($assignedStore !== $fromStoreId && $assignedStore !== $toStoreId) {
                auth_json_error(403, 'Store Admins can only create transfers involving their assigned store.');
            }
        }

        $conn->beginTransaction();

        // Validate stores exist
        if (!auth_store_exists($conn, $fromStoreId) || !auth_store_exists($conn, $toStoreId)) {
            $conn->rollBack();
            auth_json_error(400, 'One or both specified stores do not exist.');
        }

        // Enforce sibling branch ownership
        $ownersStmt = $conn->prepare("SELECT id, owner_id FROM stores WHERE id IN (:from, :to)");
        $ownersStmt->execute(['from' => $fromStoreId, 'to' => $toStoreId]);
        $storeOwnerRows = $ownersStmt->fetchAll(PDO::FETCH_KEY_PAIR);

        $fromOwner = $storeOwnerRows[$fromStoreId] ?? null;
        $toOwner = $storeOwnerRows[$toStoreId] ?? null;

        if ($fromOwner !== $toOwner || !$fromOwner) {
            $conn->rollBack();
            auth_json_error(403, 'Stock transfers are strictly permitted only between branches belonging to the same business owner.');
        }

        // Lock and validate product in source store
        $prodStmt = $conn->prepare("
            SELECT id, product_name, stock FROM products
            WHERE id = :p_id AND store_id = :s_id
            FOR UPDATE
        ");
        $prodStmt->execute(['p_id' => $productId, 's_id' => $fromStoreId]);
        $sourceProduct = $prodStmt->fetch(PDO::FETCH_ASSOC);

        if (!$sourceProduct) {
            $conn->rollBack();
            auth_json_error(400, 'Selected product does not belong to the source store.');
        }

        if ((int)$sourceProduct['stock'] < $quantity) {
            $conn->rollBack();
            auth_json_error(400, "Insufficient stock. Source store only has {$sourceProduct['stock']} units available.");
        }

        // Insert stock_transfers header
        $insertStmt = $conn->prepare("
            INSERT INTO stock_transfers (from_store_id, to_store_id, requested_by, status, notes)
            VALUES (:from_id, :to_id, :req_by, 'requested', :notes)
            RETURNING id
        ");
        $insertStmt->execute([
            'from_id' => $fromStoreId,
            'to_id' => $toStoreId,
            'req_by' => $identity['user_id'],
            'notes' => $notes
        ]);
        $transferId = (int)$insertStmt->fetchColumn();

        // Insert stock_transfer_items
        $itemStmt = $conn->prepare("
            INSERT INTO stock_transfer_items (transfer_id, product_id, quantity)
            VALUES (:t_id, :p_id, :qty)
        ");
        $itemStmt->execute([
            't_id' => $transferId,
            'p_id' => $productId,
            'qty' => $quantity
        ]);

        // Audit log entry
        $auditStmt = $conn->prepare("
            INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
            VALUES (?, ?, ?, ?, ?, ?)
        ");
        $auditStmt->execute([
            $identity['full_name'],
            $identity['role'],
            'STOCK_TRANSFER_REQUESTED',
            "Transfer #$transferId",
            "Store #$fromStoreId -> Store #$toStoreId",
            "Requested transfer of $quantity unit(s) of '{$sourceProduct['product_name']}'."
        ]);

        $conn->commit();

        echo json_encode([
            'success' => true,
            'message' => 'Stock transfer request created successfully.',
            'transfer_id' => $transferId
        ]);
        exit;
    }

    if ($action === 'quick_transfer') {
        // Direct Immediate Transfer (e.g. "throw 20 T-shirts in car and drive to Store B")
        // Decrements source store and increments destination store atomically in a single instant step
        $input = !empty($jsonBody) ? $jsonBody : $_POST;

        $fromStoreId = (int)($input['from_store_id'] ?? 0);
        $toStoreId = (int)($input['to_store_id'] ?? 0);
        $productId = (int)($input['product_id'] ?? 0);
        $quantity = (int)($input['quantity'] ?? 0);
        $notes = trim((string)($input['notes'] ?? ''));

        if ($fromStoreId <= 0 || $toStoreId <= 0 || $productId <= 0 || $quantity <= 0) {
            auth_json_error(400, 'Source Store, Destination Store, Product, and positive Quantity are required.');
        }

        if ($fromStoreId === $toStoreId) {
            auth_json_error(400, 'Source and destination store must be different.');
        }

        $conn->beginTransaction();

        // Validate stores exist
        if (!auth_store_exists($conn, $fromStoreId) || !auth_store_exists($conn, $toStoreId)) {
            $conn->rollBack();
            auth_json_error(400, 'One or both specified stores do not exist.');
        }

        // Enforce sibling branch ownership
        $ownersStmt = $conn->prepare("SELECT id, owner_id FROM stores WHERE id IN (:from, :to)");
        $ownersStmt->execute(['from' => $fromStoreId, 'to' => $toStoreId]);
        $storeOwnerRows = $ownersStmt->fetchAll(PDO::FETCH_KEY_PAIR);

        $fromOwner = $storeOwnerRows[$fromStoreId] ?? null;
        $toOwner = $storeOwnerRows[$toStoreId] ?? null;

        if ($fromOwner !== $toOwner || !$fromOwner) {
            $conn->rollBack();
            auth_json_error(403, 'Quick move is strictly permitted only between branches belonging to the same business owner.');
        }

        // Lock and validate product in source store
        $prodStmt = $conn->prepare("
            SELECT id, product_name, category, category_id, price, stock FROM products
            WHERE id = :p_id AND store_id = :s_id
            FOR UPDATE
        ");
        $prodStmt->execute(['p_id' => $productId, 's_id' => $fromStoreId]);
        $sourceProduct = $prodStmt->fetch(PDO::FETCH_ASSOC);

        if (!$sourceProduct) {
            $conn->rollBack();
            auth_json_error(400, 'Selected product does not belong to the source store.');
        }

        if ((int)$sourceProduct['stock'] < $quantity) {
            $conn->rollBack();
            auth_json_error(400, "Insufficient stock in source store. Has {$sourceProduct['stock']} unit(s) available, but {$quantity} requested.");
        }

        // 1. Deduct stock from source store
        $deductStmt = $conn->prepare("UPDATE products SET stock = stock - :qty WHERE id = :id");
        $deductStmt->execute(['qty' => $quantity, 'id' => $productId]);

        // 2. Resolve or create product in destination store
        $destProductId = get_or_create_destination_product($conn, $toStoreId, $productId);

        // 3. Add stock to destination store
        $addStmt = $conn->prepare("UPDATE products SET stock = stock + :qty WHERE id = :id");
        $addStmt->execute(['qty' => $quantity, 'id' => $destProductId]);

        // 4. Insert completed transfer record
        $insertStmt = $conn->prepare("
            INSERT INTO stock_transfers (from_store_id, to_store_id, requested_by, status, notes, dispatched_at, received_at, completed_at)
            VALUES (:from_id, :to_id, :req_by, 'completed', :notes, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            RETURNING id
        ");
        $insertStmt->execute([
            'from_id' => $fromStoreId,
            'to_id' => $toStoreId,
            'req_by' => $identity['user_id'],
            'notes' => $notes ?: 'Direct quick transfer (rebalance)'
        ]);
        $transferId = (int)$insertStmt->fetchColumn();

        // 5. Insert transfer item
        $itemStmt = $conn->prepare("
            INSERT INTO stock_transfer_items (transfer_id, product_id, quantity)
            VALUES (:t_id, :p_id, :qty)
        ");
        $itemStmt->execute([
            't_id' => $transferId,
            'p_id' => $productId,
            'qty' => $quantity
        ]);

        // 6. Fetch store names for audit & response
        $sfStmt = $conn->prepare("SELECT store_name FROM stores WHERE id = :id");
        $sfStmt->execute(['id' => $fromStoreId]);
        $fromStoreName = $sfStmt->fetchColumn() ?: "Store #$fromStoreId";

        $stStmt = $conn->prepare("SELECT store_name FROM stores WHERE id = :id");
        $stStmt->execute(['id' => $toStoreId]);
        $toStoreName = $stStmt->fetchColumn() ?: "Store #$toStoreId";

        // 7. Audit log entry
        $auditStmt = $conn->prepare("
            INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
            VALUES (?, ?, ?, ?, ?, ?)
        ");
        $auditStmt->execute([
            $identity['full_name'],
            $identity['role'],
            'QUICK_STOCK_TRANSFER',
            "Transfer #$transferId",
            "$fromStoreName -> $toStoreName",
            "Directly moved $quantity unit(s) of '{$sourceProduct['product_name']}' from $fromStoreName to $toStoreName."
        ]);

        $conn->commit();

        echo json_encode([
            'success' => true,
            'message' => "Quick Transfer complete: Directly moved {$quantity} unit(s) of '{$sourceProduct['product_name']}' from {$fromStoreName} to {$toStoreName}.",
            'transfer_id' => $transferId,
            'source_remaining_stock' => (int)$sourceProduct['stock'] - $quantity,
            'product_name' => $sourceProduct['product_name'],
            'from_store_name' => $fromStoreName,
            'to_store_name' => $toStoreName
        ]);
        exit;
    }

    if ($action === 'manager_approve') {
        $input = !empty($jsonBody) ? $jsonBody : $_POST;
        $transferId = (int)($input['transfer_id'] ?? 0);
        $selectedSourceStoreId = (int)($input['from_store_id'] ?? 0);

        if ($transferId <= 0) {
            auth_json_error(400, 'Invalid transfer ID');
        }

        $conn->beginTransaction();

        $stStmt = $conn->prepare("SELECT * FROM stock_transfers WHERE id = :id FOR UPDATE");
        $stStmt->execute(['id' => $transferId]);
        $transfer = $stStmt->fetch(PDO::FETCH_ASSOC);

        if (!$transfer) {
            $conn->rollBack();
            auth_json_error(404, 'Transfer request not found');
        }

        if ($transfer['status'] !== 'requested') {
            $conn->rollBack();
            auth_json_error(400, "Only transfers in 'requested' status can be approved. Current: {$transfer['status']}.");
        }

        if ($identity['role'] !== 'super_admin' && $identity['role'] !== 'manager') {
            $conn->rollBack();
            auth_json_error(403, 'Manager or Super Admin approval is required for stock transfers.');
        }

        $fromStoreId = $selectedSourceStoreId > 0 ? $selectedSourceStoreId : (int)$transfer['from_store_id'];

        $itemsStmt = $conn->prepare("SELECT * FROM stock_transfer_items WHERE transfer_id = :id");
        $itemsStmt->execute(['id' => $transferId]);
        $items = $itemsStmt->fetchAll(PDO::FETCH_ASSOC);

        foreach ($items as $item) {
            $pStmt = $conn->prepare("SELECT id, stock, product_name FROM products WHERE (id = :p_id OR LOWER(product_name) = (SELECT LOWER(product_name) FROM products WHERE id = :p_id)) AND store_id = :s_id FOR UPDATE");
            $pStmt->execute(['p_id' => $item['product_id'], 's_id' => $fromStoreId]);
            $p = $pStmt->fetch(PDO::FETCH_ASSOC);

            if (!$p || (int)$p['stock'] < (int)$item['quantity']) {
                $conn->rollBack();
                $avail = $p ? $p['stock'] : 0;
                auth_json_error(400, "Donor store does not have sufficient stock (Available: $avail, Needed: {$item['quantity']}).");
            }

            $decStmt = $conn->prepare("UPDATE products SET stock = stock - :qty WHERE id = :p_id");
            $decStmt->execute(['qty' => $item['quantity'], 'p_id' => $p['id']]);
        }

        $upStmt = $conn->prepare("
            UPDATE stock_transfers 
            SET status = 'in_transit', from_store_id = :from_id, dispatched_at = CURRENT_TIMESTAMP, approved_by = :app_by 
            WHERE id = :id
        ");
        $upStmt->execute([
            'from_id' => $fromStoreId,
            'app_by' => $identity['user_id'],
            'id' => $transferId
        ]);

        $auditStmt = $conn->prepare("
            INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
            VALUES (?, ?, ?, ?, ?, ?)
        ");
        $auditStmt->execute([
            $identity['full_name'],
            $identity['role'],
            'STOCK_TRANSFER_MANAGER_APPROVED',
            "Transfer #$transferId",
            "Approved donor Store #$fromStoreId -> Store #{$transfer['to_store_id']}",
            "Manager {$identity['full_name']} approved transfer #$transferId. Donor inventory deducted and dispatched."
        ]);

        $conn->commit();
        echo json_encode(['success' => true, 'message' => "Transfer #$transferId approved by Manager and dispatched into transit."]);
        exit;
    }

    if ($action === 'dispatch') {
        // Sets status to 'in_transit' and decrements source inventory atomically
        $input = !empty($jsonBody) ? $jsonBody : $_POST;

        $transferId = (int)($input['transfer_id'] ?? 0);
        if ($transferId <= 0) {
            auth_json_error(400, 'Invalid transfer ID');
        }

        $conn->beginTransaction();

        $stStmt = $conn->prepare("SELECT * FROM stock_transfers WHERE id = :id FOR UPDATE");
        $stStmt->execute(['id' => $transferId]);
        $transfer = $stStmt->fetch(PDO::FETCH_ASSOC);

        if (!$transfer) {
            $conn->rollBack();
            auth_json_error(404, 'Transfer not found');
        }

        if ($transfer['status'] !== 'requested') {
            $conn->rollBack();
            auth_json_error(400, "Only transfers in 'requested' status can be dispatched. Current: {$transfer['status']}.");
        }

        // Scope check: Super admin or store admin of source store
        if ($identity['role'] === 'store_admin' && (int)$identity['store_id'] !== (int)$transfer['from_store_id']) {
            $conn->rollBack();
            auth_json_error(403, 'Only the sending store admin or super admin can dispatch this transfer.');
        }

        // Fetch transfer items
        $itemsStmt = $conn->prepare("SELECT * FROM stock_transfer_items WHERE transfer_id = :id");
        $itemsStmt->execute(['id' => $transferId]);
        $items = $itemsStmt->fetchAll(PDO::FETCH_ASSOC);

        // Lock and decrement source products
        foreach ($items as $item) {
            $pStmt = $conn->prepare("SELECT stock, product_name FROM products WHERE id = :p_id AND store_id = :s_id FOR UPDATE");
            $pStmt->execute(['p_id' => $item['product_id'], 's_id' => $transfer['from_store_id']]);
            $p = $pStmt->fetch(PDO::FETCH_ASSOC);

            if (!$p || (int)$p['stock'] < (int)$item['quantity']) {
                $conn->rollBack();
                auth_json_error(400, "Insufficient stock to dispatch '{$p['product_name']}'. Current stock: {$p['stock']}, required: {$item['quantity']}.");
            }

            $decStmt = $conn->prepare("UPDATE products SET stock = stock - :qty WHERE id = :p_id");
            $decStmt->execute(['qty' => $item['quantity'], 'p_id' => $item['product_id']]);
        }

        // Update transfer to 'in_transit'
        $upStmt = $conn->prepare("
            UPDATE stock_transfers 
            SET status = 'in_transit', dispatched_at = CURRENT_TIMESTAMP 
            WHERE id = :id
        ");
        $upStmt->execute(['id' => $transferId]);

        // Audit log
        $auditStmt = $conn->prepare("
            INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
            VALUES (?, ?, ?, ?, ?, ?)
        ");
        $auditStmt->execute([
            $identity['full_name'],
            $identity['role'],
            'STOCK_TRANSFER_DISPATCHED',
            "Transfer #$transferId",
            "Store #{$transfer['from_store_id']}",
            "Dispatched transfer #$transferId. Source inventory deducted."
        ]);

        $conn->commit();
        echo json_encode(['success' => true, 'message' => 'Transfer dispatched into transit. Source inventory updated.']);
        exit;
    }

    if ($action === 'complete') {
        // Destination confirms receipt; destination inventory increases atomically
        $input = !empty($jsonBody) ? $jsonBody : $_POST;

        $transferId = (int)($input['transfer_id'] ?? 0);
        if ($transferId <= 0) {
            auth_json_error(400, 'Invalid transfer ID');
        }

        $conn->beginTransaction();

        $stStmt = $conn->prepare("SELECT * FROM stock_transfers WHERE id = :id FOR UPDATE");
        $stStmt->execute(['id' => $transferId]);
        $transfer = $stStmt->fetch(PDO::FETCH_ASSOC);

        if (!$transfer) {
            $conn->rollBack();
            auth_json_error(404, 'Transfer not found');
        }

        if ($transfer['status'] !== 'in_transit') {
            $conn->rollBack();
            auth_json_error(400, "Only transfers currently 'in_transit' can be marked completed. Current: {$transfer['status']}.");
        }

        // Authorization check: Destination store admin or super admin
        if ($identity['role'] === 'store_admin' && (int)$identity['store_id'] !== (int)$transfer['to_store_id']) {
            $conn->rollBack();
            auth_json_error(403, 'Only the receiving store admin or super admin can confirm receipt.');
        }

        $itemsStmt = $conn->prepare("SELECT * FROM stock_transfer_items WHERE transfer_id = :id");
        $itemsStmt->execute(['id' => $transferId]);
        $items = $itemsStmt->fetchAll(PDO::FETCH_ASSOC);

        foreach ($items as $item) {
            $destProdId = get_or_create_destination_product($conn, (int)$transfer['to_store_id'], (int)$item['product_id']);

            $incStmt = $conn->prepare("UPDATE products SET stock = stock + :qty WHERE id = :id");
            $incStmt->execute(['qty' => $item['quantity'], 'id' => $destProdId]);
        }

        $upStmt = $conn->prepare("
            UPDATE stock_transfers
            SET status = 'completed', received_at = CURRENT_TIMESTAMP, completed_at = CURRENT_TIMESTAMP
            WHERE id = :id
        ");
        $upStmt->execute(['id' => $transferId]);

        // Audit log
        $auditStmt = $conn->prepare("
            INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
            VALUES (?, ?, ?, ?, ?, ?)
        ");
        $auditStmt->execute([
            $identity['full_name'],
            $identity['role'],
            'STOCK_TRANSFER_COMPLETED',
            "Transfer #$transferId",
            "Store #{$transfer['to_store_id']}",
            "Transfer #$transferId marked completed. Destination inventory replenished."
        ]);

        $conn->commit();
        echo json_encode(['success' => true, 'message' => 'Transfer confirmed and received. Destination inventory updated.']);
        exit;
    }

    if ($action === 'cancel') {
        $input = !empty($jsonBody) ? $jsonBody : $_POST;

        $transferId = (int)($input['transfer_id'] ?? 0);
        if ($transferId <= 0) {
            auth_json_error(400, 'Invalid transfer ID');
        }

        $conn->beginTransaction();

        $stStmt = $conn->prepare("SELECT * FROM stock_transfers WHERE id = :id FOR UPDATE");
        $stStmt->execute(['id' => $transferId]);
        $transfer = $stStmt->fetch(PDO::FETCH_ASSOC);

        if (!$transfer) {
            $conn->rollBack();
            auth_json_error(404, 'Transfer not found');
        }

        if (in_array($transfer['status'], ['completed', 'cancelled'], true)) {
            $conn->rollBack();
            auth_json_error(400, "Cannot cancel a transfer that is already {$transfer['status']}.");
        }

        // Scope check
        if ($identity['role'] === 'store_admin') {
            $assignedStore = (int)$identity['store_id'];
            if ($assignedStore !== (int)$transfer['from_store_id'] && $assignedStore !== (int)$transfer['to_store_id']) {
                $conn->rollBack();
                auth_json_error(403, 'Unauthorized to cancel this transfer.');
            }
        }

        // If transfer was in_transit, we must refund the deducted stock back to the source store
        if ($transfer['status'] === 'in_transit') {
            $itemsStmt = $conn->prepare("SELECT * FROM stock_transfer_items WHERE transfer_id = :id");
            $itemsStmt->execute(['id' => $transferId]);
            $items = $itemsStmt->fetchAll(PDO::FETCH_ASSOC);

            foreach ($items as $item) {
                $refundStmt = $conn->prepare("UPDATE products SET stock = stock + :qty WHERE id = :p_id");
                $refundStmt->execute(['qty' => $item['quantity'], 'p_id' => $item['product_id']]);
            }
        }

        $upStmt = $conn->prepare("UPDATE stock_transfers SET status = 'cancelled' WHERE id = :id");
        $upStmt->execute(['id' => $transferId]);

        // Audit log
        $auditStmt = $conn->prepare("
            INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
            VALUES (?, ?, ?, ?, ?, ?)
        ");
        $auditStmt->execute([
            $identity['full_name'],
            $identity['role'],
            'STOCK_TRANSFER_CANCELLED',
            "Transfer #$transferId",
            "Store #{$transfer['from_store_id']}",
            "Transfer #$transferId cancelled. " . ($transfer['status'] === 'in_transit' ? "Inventory refunded to source." : "")
        ]);

        $conn->commit();
        echo json_encode(['success' => true, 'message' => 'Transfer cancelled successfully.']);
        exit;
    }

    auth_json_error(400, 'Invalid transfer action');

} catch (Exception $e) {
    if ($conn->inTransaction()) {
        $conn->rollBack();
    }
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'message' => 'An error occurred during transfer processing',
        'error' => $e->getMessage()
    ]);
    exit;
}
