<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: GET, POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/subscription_helper.php';

$identity = auth_require_user($conn);
// Store admins and inventory staff request; managers approve; inventory staff may also view
auth_require_roles($identity, ['super_admin', 'store_admin', 'manager', 'inventory_checker']);

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
    $srcStmt = $conn->prepare("SELECT product_name, category, category_id, price, COALESCE(cost_price, 0.00) AS cost_price, COALESCE(min_stock, 10) AS min_stock FROM products WHERE id = :id");
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
        INSERT INTO products (store_id, product_name, category, category_id, price, cost_price, stock, min_stock)
        VALUES (:store_id, :name, :category, :category_id, :price, :cost_price, 0, :min_stock)
    ");
    $createStmt->execute([
        'store_id' => $toStoreId,
        'name' => $src['product_name'],
        'category' => $src['category'] ?? 'General',
        'category_id' => $src['category_id'] ?? null,
        'price' => $src['price'] ?? 0.00,
        'cost_price' => $src['cost_price'] ?? 0.00,
        'min_stock' => $src['min_stock'] ?? 10
    ]);

    return (int)$conn->lastInsertId();
}

/**
 * Records one step of a transfer's life cycle in transfer_events so the
 * requesting store admin can watch the request travel through approval,
 * dispatch and arrival. Logging must never break the transfer itself.
 *
 * Events: requested | approved | dispatched | received | declined | cancelled | completed
 */
function transfer_log_event(PDO $conn, int $transferId, string $event, ?array $identity, string $note = ''): void
{
    try {
        $stmt = $conn->prepare("
            INSERT INTO transfer_events (transfer_id, event, actor_id, actor_name, note)
            VALUES (:transfer_id, :event, :actor_id, :actor_name, :note)
        ");
        $stmt->execute([
            'transfer_id' => $transferId,
            'event' => $event,
            'actor_id' => $identity['user_id'] ?? null,
            'actor_name' => (string)($identity['full_name'] ?? ''),
            'note' => $note
        ]);
    } catch (Throwable $e) {
        // Non-fatal: the transfer status itself is the source of truth.
    }
}

try {
    if ($action === 'list') {
        $filterStatus = $_GET['status'] ?? '';
        $filterStore = isset($_GET['store_id']) ? (int)$_GET['store_id'] : 0;

        $where = ["1=1"];
        $params = [];

        // Branch-level isolation: store admins and inventory staff only see transfers involving their store
        if ($identity['role'] === 'store_admin' || $identity['role'] === 'inventory_checker') {
            $userStoreId = (int)($identity['store_id'] ?? 0);
            $where[] = "(st.from_store_id = :user_store OR st.to_store_id = :user_store)";
            $params['user_store'] = $userStoreId;
        } elseif ($identity['role'] === 'manager') {
            $mgrId = (int)$identity['user_id'];
            $mgrAssignedStore = (int)($identity['store_id'] ?? 0);
            // Managers see transfers for branches they own, manage, or are assigned to
            $where[] = "(st.from_store_id IN (SELECT id FROM stores WHERE owner_id = :mgr_id OR manager_id = :mgr_id OR id = :mgr_store)
                         OR st.to_store_id IN (SELECT id FROM stores WHERE owner_id = :mgr_id OR manager_id = :mgr_id OR id = :mgr_store))";
            $params['mgr_id'] = $mgrId;
            $params['mgr_store'] = $mgrAssignedStore;
        } elseif ($identity['role'] === 'store_owner') {
            $ownerId = (int)$identity['user_id'];
            $where[] = "(st.from_store_id IN (SELECT id FROM stores WHERE owner_id = :own_id)
                         OR st.to_store_id IN (SELECT id FROM stores WHERE owner_id = :own_id))";
            $params['own_id'] = $ownerId;
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
                   st.created_at AS created_at,
                   st.dispatched_at AS dispatched_at,
                   st.received_at AS received_at,
                   st.completed_at AS completed_at,
                   st.declined_at AS declined_at,
                   COALESCE(st.decline_reason, '') AS decline_reason,
                   COALESCE(COUNT(sti.id), 0) AS total_items,
                   COALESCE(SUM(sti.quantity), 0) AS total_quantity,
                   COALESCE(SUM(sti.base_price * sti.quantity), 0.00) AS total_base_value,
                   COALESCE(SUM(sti.selling_price * sti.quantity), 0.00) AS total_selling_value
            FROM stock_transfers st
            LEFT JOIN stores sf ON st.from_store_id = sf.id
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
                   st.created_at AS created_at,
                   st.dispatched_at AS dispatched_at,
                   st.received_at AS received_at,
                   st.completed_at AS completed_at,
                   st.declined_at AS declined_at,
                   COALESCE(st.decline_reason, '') AS decline_reason
            FROM stock_transfers st
            LEFT JOIN stores sf ON st.from_store_id = sf.id
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

        // Scope check for branch-level roles
        if ($identity['role'] === 'store_admin' || $identity['role'] === 'inventory_checker') {
            $userStore = (int)$identity['store_id'];
            $fromStore = $transfer['from_store_id'] !== null ? (int)$transfer['from_store_id'] : 0;
            if ($fromStore !== $userStore && (int)$transfer['to_store_id'] !== $userStore) {
                auth_json_error(403, 'Unauthorized access to transfer record');
            }
        }

        $itemsStmt = $conn->prepare("
            SELECT sti.id, sti.product_id, p.product_name,
                   COALESCE(c.name, p.category, 'General') AS category,
                   sti.quantity,
                   COALESCE(sti.base_price, 0.00) AS base_price,
                   COALESCE(sti.selling_price, 0.00) AS selling_price,
                   p.stock AS current_source_stock
            FROM stock_transfer_items sti
            JOIN products p ON sti.product_id = p.id
            LEFT JOIN categories c ON p.category_id = c.id
            WHERE sti.transfer_id = :id
        ");
        $itemsStmt->execute(['id' => $transferId]);
        $items = $itemsStmt->fetchAll(PDO::FETCH_ASSOC);

        $transfer['items'] = $items;

        $eventsStmt = $conn->prepare("
            SELECT id, event, actor_id, actor_name, note, created_at
            FROM transfer_events
            WHERE transfer_id = :id
            ORDER BY id ASC
        ");
        $eventsStmt->execute(['id' => $transferId]);
        $transfer['events'] = $eventsStmt->fetchAll(PDO::FETCH_ASSOC);

        echo json_encode(['success' => true, 'transfer' => $transfer]);
        exit;
    }

    if ($action === 'timeline') {
        // Full life cycle of one transfer: requested -> approved -> dispatched
        // -> received, with the person and timestamp of every step.
        $transferId = (int)($_GET['id'] ?? $_GET['transfer_id'] ?? 0);
        if ($transferId <= 0) {
            auth_json_error(400, 'Invalid transfer ID');
        }

        $stmt = $conn->prepare("
            SELECT st.id, st.status, st.from_store_id, sf.store_name AS from_store_name,
                   st.to_store_id, dt.store_name AS to_store_name,
                   st.requested_by, u.full_name AS requested_by_name,
                   st.created_at, st.dispatched_at, st.received_at, st.completed_at,
                   st.declined_at, st.decline_reason,
                   COALESCE(st.notes, '') AS notes
            FROM stock_transfers st
            LEFT JOIN stores sf ON st.from_store_id = sf.id
            JOIN stores dt ON st.to_store_id = dt.id
            JOIN users u ON st.requested_by = u.id
            WHERE st.id = :id
            LIMIT 1
        ");
        $stmt->execute(['id' => $transferId]);
        $transfer = $stmt->fetch(PDO::FETCH_ASSOC);

        if (!$transfer) {
            auth_json_error(404, 'Transfer not found');
        }

        // Same branch-level scope check as the detail action
        if ($identity['role'] === 'store_admin' || $identity['role'] === 'inventory_checker') {
            $userStore = (int)($identity['store_id'] ?? 0);
            $fromStore = $transfer['from_store_id'] !== null ? (int)$transfer['from_store_id'] : 0;
            if ($fromStore !== $userStore && (int)$transfer['to_store_id'] !== $userStore) {
                auth_json_error(403, 'Unauthorized access to transfer record');
            }
        }

        $eventsStmt = $conn->prepare("
            SELECT id, event, actor_id, actor_name, note, created_at
            FROM transfer_events
            WHERE transfer_id = :id
            ORDER BY id ASC
        ");
        $eventsStmt->execute(['id' => $transferId]);
        $events = $eventsStmt->fetchAll(PDO::FETCH_ASSOC);

        echo json_encode([
            'success' => true,
            'transfer' => $transfer,
            'events' => $events
        ]);
        exit;
    }

    if ($action === 'activity') {
        // "Monitoring" feed for the signed-in account: the newest transfer
        // events the caller is allowed to see (their requests, their branch,
        // or - for managers - their portfolio).
        $limit = (int)($_GET['limit'] ?? 20);
        if ($limit < 1 || $limit > 50) {
            $limit = 20;
        }

        $where = ["1=1"];
        $params = [];

        $role = $identity['role'];
        if ($role === 'store_admin' || $role === 'inventory_checker') {
            $where[] = "(st.from_store_id = :user_store OR st.to_store_id = :user_store OR st.requested_by = :req_by)";
            $params['user_store'] = (int)($identity['store_id'] ?? 0);
            $params['req_by'] = (int)$identity['user_id'];
        } elseif ($role === 'manager') {
            $where[] = "(st.requested_by = :req_by
                         OR st.from_store_id IN (SELECT id FROM stores WHERE owner_id = :mgr_id OR manager_id = :mgr_id OR id = :mgr_store)
                         OR st.to_store_id IN (SELECT id FROM stores WHERE owner_id = :mgr_id OR manager_id = :mgr_id OR id = :mgr_store))";
            $params['req_by'] = (int)$identity['user_id'];
            $params['mgr_id'] = (int)$identity['user_id'];
            $params['mgr_store'] = (int)($identity['store_id'] ?? 0);
        } elseif ($role === 'store_owner') {
            $where[] = "(st.requested_by = :req_by OR st.from_store_id IN (SELECT id FROM stores WHERE owner_id = :own_id) OR st.to_store_id IN (SELECT id FROM stores WHERE owner_id = :own_id))";
            $params['req_by'] = (int)$identity['user_id'];
            $params['own_id'] = (int)$identity['user_id'];
        }

        $whereClause = implode(' AND ', $where);

        $stmt = $conn->prepare("
            SELECT te.id, te.transfer_id, te.event, te.actor_id, te.actor_name, te.note, te.created_at,
                   st.status AS transfer_status,
                   st.from_store_id, sf.store_name AS from_store_name,
                   st.to_store_id, dt.store_name AS to_store_name,
                   u.full_name AS requested_by_name
            FROM transfer_events te
            JOIN stock_transfers st ON st.id = te.transfer_id
            LEFT JOIN stores sf ON st.from_store_id = sf.id
            JOIN stores dt ON st.to_store_id = dt.id
            JOIN users u ON st.requested_by = u.id
            WHERE $whereClause
            ORDER BY te.created_at DESC, te.id DESC
            LIMIT $limit
        ");
        $stmt->execute($params);
        $events = $stmt->fetchAll(PDO::FETCH_ASSOC);

        echo json_encode([
            'success' => true,
            'events' => $events,
            'count' => count($events)
        ]);
        exit;
    }

    if ($action === 'donor_options') {
        // Branches a Manager may choose from when approving: the siblings of the
        // requesting branch (same owner or same business), excluding the requester.
        $transferId = (int)($_GET['id'] ?? 0);
        if ($transferId <= 0) {
            auth_json_error(400, 'Invalid transfer ID');
        }

        if ($identity['role'] !== 'super_admin' && $identity['role'] !== 'manager' && $identity['role'] !== 'store_owner') {
            auth_json_error(403, 'Only a Manager can choose which branch transfers the stock.');
        }

        $destStmt = $conn->prepare("
            SELECT st.id, st.status AS transfer_status, s.id AS dest_id, s.store_name, s.owner_id, s.business_id
            FROM stock_transfers st
            JOIN stores s ON s.id = st.to_store_id
            WHERE st.id = :id
        ");
        $destStmt->execute(['id' => $transferId]);
        $dest = $destStmt->fetch(PDO::FETCH_ASSOC);

        if (!$dest) {
            auth_json_error(404, 'Transfer request not found');
        }

        $conds = ["id <> :dest_id", "LOWER(COALESCE(status, 'active')) = 'active'"];
        $donorParams = ['dest_id' => (int)$dest['dest_id']];

        if ($dest['owner_id'] !== null) {
            $conds[] = "owner_id = :owner_id";
            $donorParams['owner_id'] = (int)$dest['owner_id'];
        }
        if ($dest['business_id'] !== null) {
            $conds[] = "business_id = :business_id";
            $donorParams['business_id'] = (int)$dest['business_id'];
        }

        if (count($conds) === 2) {
            // Neither an owner nor a business links this branch: no valid donor exists
            echo json_encode([
                'success' => true,
                'transfer_id' => $transferId,
                'destination_store' => ['id' => (int)$dest['dest_id'], 'store_name' => $dest['store_name']],
                'donors' => []
            ]);
            exit;
        }

        $donorStmt = $conn->prepare("
            SELECT id, store_name
            FROM stores
            WHERE " . implode(' AND ', $conds) . "
            ORDER BY store_name ASC
        ");
        $donorStmt->execute($donorParams);
        $donors = $donorStmt->fetchAll(PDO::FETCH_ASSOC);

        echo json_encode([
            'success' => true,
            'transfer_id' => $transferId,
            'destination_store' => ['id' => (int)$dest['dest_id'], 'store_name' => $dest['store_name']],
            'donors' => $donors
        ]);
        exit;
    }

    if ($action === 'create') {
        $input = !empty($jsonBody) ? $jsonBody : $_POST;

        $fromStoreId = (int)($input['from_store_id'] ?? 0);
        $toStoreId = (int)($input['to_store_id'] ?? 0);
        $notes = trim((string)($input['notes'] ?? ''));

        // Multi-select request: [{product_id, quantity}, ...]
        $items = [];
        if (isset($input['items']) && is_array($input['items'])) {
            foreach ($input['items'] as $rawItem) {
                if (!is_array($rawItem)) {
                    continue;
                }
                $rawProductId = (int)($rawItem['product_id'] ?? 0);
                $rawQuantity = (int)($rawItem['quantity'] ?? 0);
                if ($rawProductId > 0 && $rawQuantity > 0) {
                    $items[$rawProductId] = ($items[$rawProductId] ?? 0) + $rawQuantity;
                }
            }
        }

        // Backwards compatible: a single product_id/quantity payload
        if (empty($items)) {
            $legacyProductId = (int)($input['product_id'] ?? 0);
            $legacyQuantity = (int)($input['quantity'] ?? 0);
            if ($legacyProductId > 0 && $legacyQuantity > 0) {
                $items[$legacyProductId] = $legacyQuantity;
            }
        }

        // Store Admins and Inventory staff request stock FOR their own branch; the manager picks the donor on approval.
        if ($identity['role'] === 'store_admin' || $identity['role'] === 'inventory_checker') {
            $assignedStore = (int)($identity['store_id'] ?? 0);
            if ($assignedStore <= 0) {
                auth_json_error(403, 'Your account is not assigned to a branch.');
            }
            if ($toStoreId > 0 && $toStoreId !== $assignedStore) {
                auth_json_error(403, 'Store Admins can only request transfers for their own branch.');
            }
            $toStoreId = $assignedStore;
        }

        if ($toStoreId <= 0 || empty($items)) {
            auth_json_error(400, 'To Store and at least one item with a positive quantity are required.');
        }

        if ($fromStoreId > 0 && $fromStoreId === $toStoreId) {
            auth_json_error(400, 'Source and destination store must be different.');
        }

        $itemLines = [];

        $conn->beginTransaction();

        // Validate stores exist
        if (!auth_store_exists($conn, $toStoreId) || ($fromStoreId > 0 && !auth_store_exists($conn, $fromStoreId))) {
            $conn->rollBack();
            auth_json_error(400, 'One or both specified stores do not exist.');
        }

        if ($fromStoreId > 0) {
            // Enforce sibling branch ownership when the donor is already known
            $metaStmt = $conn->prepare("SELECT owner_id, business_id FROM stores WHERE id = :id");
            $metaStmt->execute(['id' => $fromStoreId]);
            $fromMeta = $metaStmt->fetch(PDO::FETCH_ASSOC);
            $metaStmt->execute(['id' => $toStoreId]);
            $toMeta = $metaStmt->fetch(PDO::FETCH_ASSOC);

            $sameOwner = $fromMeta && $toMeta && $fromMeta['owner_id'] !== null && $fromMeta['owner_id'] == $toMeta['owner_id'];
            $sameBusiness = $fromMeta && $toMeta && $fromMeta['business_id'] !== null && $fromMeta['business_id'] == $toMeta['business_id'];

            if (!$sameOwner && !$sameBusiness) {
                $conn->rollBack();
                auth_json_error(403, 'Stock transfers are strictly permitted only between branches belonging to the same business.');
            }
        }

        // Validate every requested line and snapshot its base/selling price
        foreach ($items as $productId => $quantity) {
            $priceSelect = "SELECT id, product_name, stock,
                                   COALESCE(cost_price, 0.00) AS base_price,
                                   COALESCE(price, 0.00) AS selling_price
                            FROM products
                            WHERE id = :p_id AND store_id = :s_id";

            if ($fromStoreId > 0) {
                // Lock and validate the product inside the known source store
                $prodStmt = $conn->prepare($priceSelect . " FOR UPDATE");
                $prodStmt->execute(['p_id' => $productId, 's_id' => $fromStoreId]);
                $product = $prodStmt->fetch(PDO::FETCH_ASSOC);

                if (!$product) {
                    $conn->rollBack();
                    auth_json_error(400, 'Selected product does not belong to the source store.');
                }

                if ((int)$product['stock'] < $quantity) {
                    $conn->rollBack();
                    auth_json_error(400, "Insufficient stock for '{$product['product_name']}'. Source store only has {$product['stock']} units available.");
                }
            } else {
                // Pending-donor request: the product is selected from the requester's (destination) branch
                $prodStmt = $conn->prepare($priceSelect);
                $prodStmt->execute(['p_id' => $productId, 's_id' => $toStoreId]);
                $product = $prodStmt->fetch(PDO::FETCH_ASSOC);

                if (!$product) {
                    $conn->rollBack();
                    auth_json_error(400, 'Selected product does not belong to your branch.');
                }
            }

            $itemLines[$productId] = [
                'product_id' => $productId,
                'quantity' => $quantity,
                'product_name' => $product['product_name'],
                'base_price' => (float)$product['base_price'],
                'selling_price' => (float)$product['selling_price'],
            ];
        }

        // Insert stock_transfers header ('requested' = awaiting manager approval)
        $insertStmt = $conn->prepare("
            INSERT INTO stock_transfers (from_store_id, to_store_id, requested_by, status, notes)
            VALUES (:from_id, :to_id, :req_by, 'requested', :notes)
        ");
        $insertStmt->execute([
            'from_id' => $fromStoreId > 0 ? $fromStoreId : null,
            'to_id' => $toStoreId,
            'req_by' => $identity['user_id'],
            'notes' => $notes
        ]);
        $transferId = (int)$conn->lastInsertId();

        // Insert stock_transfer_items with the price snapshot for each line
        $itemStmt = $conn->prepare("
            INSERT INTO stock_transfer_items (transfer_id, product_id, quantity, base_price, selling_price)
            VALUES (:t_id, :p_id, :qty, :base_price, :selling_price)
        ");
        $totalQuantity = 0;
        $summaryParts = [];
        foreach ($itemLines as $line) {
            $itemStmt->execute([
                't_id' => $transferId,
                'p_id' => $line['product_id'],
                'qty' => $line['quantity'],
                'base_price' => $line['base_price'],
                'selling_price' => $line['selling_price'],
            ]);
            $totalQuantity += $line['quantity'];
            $summaryParts[] = $line['quantity'] . 'x ' . $line['product_name'];
        }

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
            "Store #" . ($fromStoreId > 0 ? $fromStoreId . ' -> ' : '(pending donor) -> ') . "Store #$toStoreId",
            "Requested " . count($itemLines) . " item line(s), $totalQuantity unit(s): " . implode(', ', $summaryParts) . '.' .
                ($fromStoreId > 0 ? '' : ' Awaiting manager approval and donor branch selection.')
        ]);

        transfer_log_event($conn, $transferId, 'requested', $identity,
            "Requested $totalQuantity unit(s): " . implode(', ', $summaryParts) . '.');

        $conn->commit();

        echo json_encode([
            'success' => true,
            'message' => $fromStoreId > 0
                ? 'Stock transfer request created successfully.'
                : 'Stock transfer request submitted. The manager will approve it and choose the donor branch.',
            'transfer_id' => $transferId,
            'item_count' => count($itemLines),
            'total_quantity' => $totalQuantity
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
        ");
        $insertStmt->execute([
            'from_id' => $fromStoreId,
            'to_id' => $toStoreId,
            'req_by' => $identity['user_id'],
            'notes' => $notes ?: 'Direct quick transfer (rebalance)'
        ]);
        $transferId = (int)$conn->lastInsertId();

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

        transfer_log_event($conn, $transferId, 'requested', $identity,
            "Direct quick transfer of $quantity unit(s) of '{$sourceProduct['product_name']}'.");
        transfer_log_event($conn, $transferId, 'received', $identity,
            "Instant transfer: stock left $fromStoreName and arrived at $toStoreName.");

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
        if ($selectedSourceStoreId <= 0) {
            auth_json_error(400, 'You must choose which branch will transfer stock (donor branch) before approving.');
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

        $fromStoreId = $selectedSourceStoreId;
        $toStoreId = (int)$transfer['to_store_id'];

        if ($fromStoreId === $toStoreId) {
            $conn->rollBack();
            auth_json_error(400, 'The donor branch must be different from the requesting branch.');
        }

        // The donor must belong to the same business (or at least the same owner) as the requester's branch
        $metaStmt = $conn->prepare("SELECT owner_id, business_id FROM stores WHERE id = :id");
        $metaStmt->execute(['id' => $fromStoreId]);
        $donorMeta = $metaStmt->fetch(PDO::FETCH_ASSOC);
        $metaStmt->execute(['id' => $toStoreId]);
        $destMeta = $metaStmt->fetch(PDO::FETCH_ASSOC);

        if (!$donorMeta || !$destMeta) {
            $conn->rollBack();
            auth_json_error(400, 'One of the specified branches no longer exists.');
        }

        $sameOwner = $donorMeta['owner_id'] !== null && $donorMeta['owner_id'] == $destMeta['owner_id'];
        $sameBusiness = $donorMeta['business_id'] !== null && $donorMeta['business_id'] == $destMeta['business_id'];

        if (!$sameOwner && !$sameBusiness) {
            $conn->rollBack();
            auth_json_error(403, 'You can only transfer stock between branches of the same business.');
        }

        // Managers may only approve requests within their own branch portfolio
        if ($identity['role'] === 'manager') {
            $mgrCheck = $conn->prepare("SELECT id FROM stores WHERE id = :sid AND (owner_id = :uid OR manager_id = :uid OR id = :assigned)");
            $mgrCheck->execute([
                'sid' => $toStoreId,
                'uid' => (int)$identity['user_id'],
                'assigned' => (int)($identity['store_id'] ?? 0)
            ]);
            if (!$mgrCheck->fetchColumn()) {
                $conn->rollBack();
                auth_json_error(403, 'You can only approve transfer requests for your own branches.');
            }
        }

        $itemsStmt = $conn->prepare("SELECT * FROM stock_transfer_items WHERE transfer_id = :id");
        $itemsStmt->execute(['id' => $transferId]);
        $items = $itemsStmt->fetchAll(PDO::FETCH_ASSOC);

        foreach ($items as $item) {
            // Resolve the requested product inside the donor branch (matched by id or identical name)
            $pStmt = $conn->prepare("SELECT id, stock, product_name, COALESCE(cost_price, 0.00) AS base_price, COALESCE(price, 0.00) AS selling_price FROM products WHERE (id = :p_id OR LOWER(product_name) = (SELECT LOWER(product_name) FROM products WHERE id = :p_id)) AND store_id = :s_id FOR UPDATE");
            $pStmt->execute(['p_id' => $item['product_id'], 's_id' => $fromStoreId]);
            $p = $pStmt->fetch(PDO::FETCH_ASSOC);

            if (!$p || (int)$p['stock'] < (int)$item['quantity']) {
                $conn->rollBack();
                $avail = $p ? $p['stock'] : 0;
                auth_json_error(400, "The chosen donor branch does not have sufficient stock (Available: $avail, Needed: {$item['quantity']}).");
            }

            // Point the transfer item at the donor's product so receipt/cancel reconcile the right stock,
            // and refresh the price snapshot with the donor branch's actual base/selling prices
            $rebindStmt = $conn->prepare("UPDATE stock_transfer_items SET product_id = :donor_pid, base_price = :base_price, selling_price = :selling_price WHERE id = :item_id");
            $rebindStmt->execute([
                'donor_pid' => $p['id'],
                'base_price' => (float)$p['base_price'],
                'selling_price' => (float)$p['selling_price'],
                'item_id' => $item['id']
            ]);

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

        $donorNameStmt = $conn->prepare("SELECT store_name FROM stores WHERE id = :id");
        $donorNameStmt->execute(['id' => $fromStoreId]);
        $donorName = $donorNameStmt->fetchColumn() ?: "Store #$fromStoreId";

        $auditStmt = $conn->prepare("
            INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
            VALUES (?, ?, ?, ?, ?, ?)
        ");
        $auditStmt->execute([
            $identity['full_name'],
            $identity['role'],
            'STOCK_TRANSFER_MANAGER_APPROVED',
            "Transfer #$transferId",
            "$donorName -> Store #$toStoreId",
            "Manager {$identity['full_name']} approved transfer #$transferId and chose $donorName as the donor branch. Donor inventory deducted and dispatched."
        ]);

        transfer_log_event($conn, $transferId, 'approved', $identity,
            "Approved by {$identity['full_name']}; donor branch: $donorName.");
        transfer_log_event($conn, $transferId, 'dispatched', $identity,
            "Stock left $donorName and is now in transit to Store #$toStoreId.");

        $conn->commit();
        echo json_encode(['success' => true, 'message' => "Transfer #$transferId approved. $donorName was chosen as the donor branch and the stock is now in transit."]);
        exit;
    }

    if ($action === 'manager_decline') {
        $input = !empty($jsonBody) ? $jsonBody : $_POST;
        $transferId = (int)($input['transfer_id'] ?? 0);
        $reason = trim((string)($input['reason'] ?? ''));

        if ($transferId <= 0) {
            auth_json_error(400, 'Invalid transfer ID');
        }

        if ($identity['role'] !== 'super_admin' && $identity['role'] !== 'manager') {
            auth_json_error(403, 'Only a Manager or Super Admin can decline transfer requests.');
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
            auth_json_error(400, "Only transfers in 'requested' status can be declined. Current: {$transfer['status']}.");
        }

        $upStmt = $conn->prepare("
            UPDATE stock_transfers
            SET status = 'declined', declined_at = CURRENT_TIMESTAMP, declined_by = :uid, decline_reason = :reason
            WHERE id = :id
        ");
        $upStmt->execute([
            'uid' => (int)$identity['user_id'],
            'reason' => $reason !== '' ? $reason : 'No reason provided.',
            'id' => $transferId
        ]);

        $auditStmt = $conn->prepare("
            INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
            VALUES (?, ?, ?, ?, ?, ?)
        ");
        $auditStmt->execute([
            $identity['full_name'],
            $identity['role'],
            'STOCK_TRANSFER_DECLINED',
            "Transfer #$transferId",
            "Store #{$transfer['to_store_id']}",
            "Transfer #$transferId declined. Reason: " . ($reason !== '' ? $reason : 'No reason provided.')
        ]);

        transfer_log_event($conn, $transferId, 'declined', $identity,
            "Declined by {$identity['full_name']}. Reason: " . ($reason !== '' ? $reason : 'No reason provided.'));

        $conn->commit();
        echo json_encode(['success' => true, 'message' => "Transfer #$transferId declined."]);
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

        transfer_log_event($conn, $transferId, 'dispatched', $identity,
            "Dispatched by {$identity['full_name']}; source inventory deducted.");

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

        transfer_log_event($conn, $transferId, 'received', $identity,
            "Stock arrived and was added to inventory at Store #{$transfer['to_store_id']}.");

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
        if ($identity['role'] === 'store_admin' || $identity['role'] === 'inventory_checker') {
            $assignedStore = (int)$identity['store_id'];
            $fromStore = $transfer['from_store_id'] !== null ? (int)$transfer['from_store_id'] : 0;
            if ($assignedStore !== $fromStore && $assignedStore !== (int)$transfer['to_store_id']) {
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
            "Store #" . ($transfer['from_store_id'] !== null ? $transfer['from_store_id'] : $transfer['to_store_id']),
            "Transfer #$transferId cancelled. " . ($transfer['status'] === 'in_transit' ? "Inventory refunded to source." : "")
        ]);

        transfer_log_event($conn, $transferId, 'cancelled', $identity,
            "Cancelled by {$identity['full_name']}." .
            ($transfer['status'] === 'in_transit' ? " In-transit stock was refunded to the source branch." : ""));

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
        'error' => 'Unexpected server error. Details were written to the server log.'
    ]);
    exit;
}
