<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/subscription_helper.php';

$identity = auth_require_user($conn);
auth_require_roles($identity, ['super_admin', 'store_owner', 'manager', 'store_admin']);

// Server-side Premium Enforcement
subscription_require_premium($conn, $identity);

$range = $_GET['range'] ?? '30'; // '7', '30', 'this_month'
$storeFilter = isset($_GET['store_id']) ? (int)$_GET['store_id'] : 0;

if ($identity['role'] === 'store_admin') {
    $storeFilter = (int)$identity['store_id'];
} elseif ($storeFilter <= 0 && ($identity['role'] === 'manager' || $identity['role'] === 'store_owner')) {
    $storeFilter = (int)($identity['store_id'] ?? 0);
}

// Compute start date based on range
$now = new DateTime();
$daysCount = 30;

if ($range === '7') {
    $daysCount = 7;
    $startDate = (clone $now)->modify('-7 days')->format('Y-m-d 00:00:00');
} elseif ($range === 'this_month') {
    $firstOfMonth = new DateTime($now->format('Y-m-01 00:00:00'));
    $daysCount = max(1, (int)$now->diff($firstOfMonth)->format('%a') + 1);
    $startDate = $firstOfMonth->format('Y-m-d 00:00:00');
} else {
    $daysCount = 30;
    $startDate = (clone $now)->modify('-30 days')->format('Y-m-d 00:00:00');
}

try {
    // 1. Fetch stores (Super Admin gets all stores; Store Admin only gets sibling stores of same owner)
    if ($identity['role'] === 'store_admin') {
        $assignedStoreId = (int)($identity['store_id'] ?? 0);
        $ownerStmt = $conn->prepare("SELECT owner_id FROM stores WHERE id = :store_id");
        $ownerStmt->execute(['store_id' => $assignedStoreId]);
        $ownerId = (int)$ownerStmt->fetchColumn();

        if ($ownerId > 0) {
            $storesStmt = $conn->prepare("SELECT id, store_name, location, owner_id FROM stores WHERE owner_id = :owner_id ORDER BY id ASC");
            $storesStmt->execute(['owner_id' => $ownerId]);
        } else {
            $storesStmt = $conn->prepare("SELECT id, store_name, location, owner_id FROM stores WHERE id = :store_id ORDER BY id ASC");
            $storesStmt->execute(['store_id' => $assignedStoreId]);
        }
    } else {
        $storesStmt = $conn->query("SELECT id, store_name, location, owner_id FROM stores ORDER BY id ASC");
    }
    $allStores = $storesStmt->fetchAll(PDO::FETCH_ASSOC);
    $storeNames = [];
    $storeOwners = [];
    foreach ($allStores as $s) {
        $sid = (int)$s['id'];
        $storeNames[$sid] = $s['store_name'];
        $storeOwners[$sid] = (int)($s['owner_id'] ?? 0);
    }

    // 2. Fetch sales summary per store within period
    $storeSalesStmt = $conn->prepare("
        SELECT t.store_id,
               COALESCE(COUNT(t.id), 0) AS transaction_count,
               COALESCE(SUM(t.total_amount), 0.0) AS total_sales,
               COALESCE(AVG(t.total_amount), 0.0) AS avg_transaction_value
        FROM transactions t
        WHERE t.created_at >= :start_date
        GROUP BY t.store_id
    ");
    $storeSalesStmt->execute(['start_date' => $startDate]);
    $storeSalesRows = $storeSalesStmt->fetchAll(PDO::FETCH_ASSOC);

    $storeMetrics = [];
    foreach ($allStores as $s) {
        $sid = (int)$s['id'];
        $storeMetrics[$sid] = [
            'store_id' => $sid,
            'store_name' => $s['store_name'],
            'location' => $s['location'],
            'transactions' => 0,
            'sales' => 0.0,
            'avg_order' => 0.0,
            'units_sold' => 0,
            'total_stock' => 0
        ];
    }

    foreach ($storeSalesRows as $row) {
        $sid = (int)$row['store_id'];
        if (isset($storeMetrics[$sid])) {
            $storeMetrics[$sid]['transactions'] = (int)$row['transaction_count'];
            $storeMetrics[$sid]['sales'] = (float)$row['total_sales'];
            $storeMetrics[$sid]['avg_order'] = (float)$row['avg_transaction_value'];
        }
    }

    // 3. Units sold per product and store within period
    $unitsStmt = $conn->prepare("
        SELECT p.store_id, p.id AS product_id, p.product_name, p.category, p.stock, p.price,
               COALESCE(SUM(ti.quantity), 0) AS units_sold
        FROM products p
        LEFT JOIN transaction_items ti ON ti.product_id = p.id
        LEFT JOIN transactions t ON ti.transaction_id = t.id AND t.created_at >= :start_date
        GROUP BY p.store_id, p.id, p.product_name, p.category, p.stock, p.price
        ORDER BY p.store_id, p.product_name
    ");
    $unitsStmt->execute(['start_date' => $startDate]);
    $productStats = $unitsStmt->fetchAll(PDO::FETCH_ASSOC);

    $fastMoving = [];
    $slowMoving = [];
    $stockoutRisks = [];
    $productCatalogByStore = []; // [clean_name => [store_id => data]]

    $totalPeriodSales = 0.0;
    $totalPeriodTransactions = 0;
    $totalPeriodUnits = 0;

    foreach ($productStats as $p) {
        $sid = (int)$p['store_id'];
        $pid = (int)$p['product_id'];
        $name = $p['product_name'];
        $stock = (int)$p['stock'];
        $sold = (int)$p['units_sold'];
        $price = (float)$p['price'];

        if (isset($storeMetrics[$sid])) {
            $storeMetrics[$sid]['units_sold'] += $sold;
            $storeMetrics[$sid]['total_stock'] += $stock;
        }

        if ($storeFilter === 0 || $storeFilter === $sid) {
            $totalPeriodUnits += $sold;
        }

        $dailyVelocity = round($sold / $daysCount, 2);
        $daysCoverage = $dailyVelocity > 0 ? round($stock / $dailyVelocity, 1) : ($stock > 0 ? 999.0 : 0.0);

        $prodData = [
            'product_id' => $pid,
            'store_id' => $sid,
            'store_name' => $storeNames[$sid] ?? "Store #$sid",
            'product_name' => $name,
            'category' => $p['category'] ?? 'General',
            'stock' => $stock,
            'price' => $price,
            'units_sold' => $sold,
            'velocity_per_day' => $dailyVelocity,
            'days_of_stock' => $daysCoverage
        ];

        $cleanKey = strtolower(trim($name));
        $productCatalogByStore[$cleanKey][$sid] = $prodData;

        // Filtering for insights lists if store filter matches
        if ($storeFilter === 0 || $storeFilter === $sid) {
            // Fast Moving: >= 3 units sold and velocity >= 0.2 units/day
            if ($sold >= 3 && $dailyVelocity >= 0.1) {
                $fastMoving[] = $prodData;
            }

            // Slow Moving: stock >= 15 and sold <= 1 in period
            if ($stock >= 15 && $sold <= 1) {
                $slowMoving[] = array_merge($prodData, [
                    'insight' => 'Moving slowly relative to on-hand inventory.',
                    'recommendation' => 'Reduce replenishment order size.'
                ]);
            }

            // Stockout Risk: coverage <= 7 days with active velocity OR stock == 0 with past sales
            if (($daysCoverage <= 7.0 && $dailyVelocity > 0) || ($stock === 0 && $sold > 0)) {
                $stockoutRisks[] = array_merge($prodData, [
                    'risk_level' => $stock <= 3 ? 'Critical' : 'Moderate',
                    'coverage_label' => $stock === 0 ? 'Stocked out' : "$daysCoverage days remaining"
                ]);
            }
        }
    }

    // Sort Fast Moving by sold DESC
    usort($fastMoving, fn($a, $b) => $b['units_sold'] <=> $a['units_sold']);
    $fastMoving = array_slice($fastMoving, 0, 10);

    // Sort Stockout Risks by days_of_stock ASC
    usort($stockoutRisks, fn($a, $b) => $a['days_of_stock'] <=> $b['days_of_stock']);
    $stockoutRisks = array_slice($stockoutRisks, 0, 10);

    // 4. Calculate Explainable Transfer Recommendations
    // Compare identical product across different stores
    $transferRecommendations = [];

    foreach ($productCatalogByStore as $cleanName => $storeEntries) {
        if (count($storeEntries) < 2) {
            continue; // Need at least two branches stocking this product to transfer
        }

        // Identify branch with low coverage / stockout risk (Destination)
        // and branch with high coverage / surplus stock (Source)
        foreach ($storeEntries as $destStoreId => $dest) {
            // Condition for destination: days coverage <= 7 days or stock <= 5
            if ($dest['days_of_stock'] > 7.0 && $dest['stock'] > 5) {
                continue;
            }

            foreach ($storeEntries as $srcStoreId => $src) {
                if ($srcStoreId === $destStoreId) continue;

                // Sibling Branch Isolation: Only recommend transfers between branches of the SAME Store Owner!
                $srcOwner = $storeOwners[$srcStoreId] ?? 0;
                $destOwner = $storeOwners[$destStoreId] ?? 0;
                if ($srcOwner <= 0 || $destOwner <= 0 || $srcOwner !== $destOwner) {
                    continue; // Do not transfer stock between different owners / competing businesses
                }

                // Condition for source: must have at least 5 units on hand
                if ($src['stock'] < 5) {
                    continue;
                }

                // If store filter is active, only show recommendations relevant to this store
                if ($storeFilter > 0 && $storeFilter !== $destStoreId && $storeFilter !== $srcStoreId) {
                    continue;
                }

                // Calculate balanced transfer quantity
                $destNeed = max(2, (int)ceil($dest['velocity_per_day'] * 10) - $dest['stock']);
                $srcSurplus = max(0, $src['stock'] - (int)ceil($src['velocity_per_day'] * 7));

                $transferQty = max(2, min($destNeed, $srcSurplus, (int)floor($src['stock'] / 2)));
                if ($transferQty > $src['stock'] - 1) {
                    $transferQty = max(1, (int)floor($src['stock'] / 2));
                }

                if ($transferQty >= 1 && $src['stock'] >= 3) {
                    $isFasterSeller = ($dest['units_sold'] > $src['units_sold']) || ($dest['velocity_per_day'] > $src['velocity_per_day']);

                    if ($isFasterSeller) {
                        $ratioText = $src['units_sold'] > 0 
                            ? round($dest['units_sold'] / max(1, $src['units_sold']), 1) . 'x' 
                            : 'significantly';
                        $headline = "{$dest['store_name']} sells more {$src['product_name']} than {$src['store_name']} — Recommended to move {$transferQty} units here";
                        $reason = "{$dest['store_name']} sells {$ratioText} more '{$src['product_name']}' than {$src['store_name']} ({$dest['units_sold']} sold vs {$src['units_sold']} sold). {$dest['store_name']} has only {$dest['stock']} units left, while {$src['store_name']} has {$src['stock']} units in stock. It is recommended to move {$transferQty} units from {$src['store_name']} to {$dest['store_name']} to fulfill customer demand.";
                    } elseif ($dest['stock'] === 0) {
                        $headline = "{$dest['store_name']} has completely run out of {$src['product_name']} — Move {$transferQty} units from {$src['store_name']}";
                        $reason = "{$dest['store_name']} has completely run out of '{$src['product_name']}' (0 stock), while {$src['store_name']} has {$src['stock']} units on hand. It is recommended to move {$transferQty} units from {$src['store_name']} to {$dest['store_name']} immediately.";
                    } else {
                        $headline = "Stock Imbalance: {$dest['store_name']} is low on {$src['product_name']} — Move {$transferQty} units from {$src['store_name']}";
                        $reason = "{$dest['store_name']} has only {$dest['stock']} units of '{$src['product_name']}' remaining ({$dest['days_of_stock']} days runway), while {$src['store_name']} has a surplus of {$src['stock']} units. Recommended transfer: {$transferQty} units from {$src['store_name']} to {$dest['store_name']}.";
                    }

                    $transferRecommendations[] = [
                        'product_name' => $src['product_name'],
                        'headline' => $headline,
                        'source_product_id' => $src['product_id'],
                        'source_store_id' => $srcStoreId,
                        'source_store_name' => $src['store_name'],
                        'source_current_stock' => $src['stock'],
                        'destination_store_id' => $destStoreId,
                        'destination_store_name' => $dest['store_name'],
                        'destination_current_stock' => $dest['stock'],
                        'recommended_quantity' => $transferQty,
                        'reason' => $reason,
                        'quick_command' => [
                            'action' => 'quick_transfer',
                            'from_store_id' => $srcStoreId,
                            'to_store_id' => $destStoreId,
                            'product_id' => $src['product_id'],
                            'quantity' => $transferQty,
                            'notes' => "Quick move: Rebalance {$src['product_name']} based on sales velocity recommendation"
                        ]
                    ];
                }
            }
        }
    }

    // Limit recommendations to top 5
    $transferRecommendations = array_slice($transferRecommendations, 0, 5);

    // Compute overview metrics
    foreach ($storeMetrics as $sm) {
        if ($storeFilter === 0 || $storeFilter === $sm['store_id']) {
            $totalPeriodSales += $sm['sales'];
            $totalPeriodTransactions += $sm['transactions'];
        }
    }

    $avgTransaction = $totalPeriodTransactions > 0 ? round($totalPeriodSales / $totalPeriodTransactions, 2) : 0.0;

    $isSuperAdmin = ($identity['role'] === 'super_admin');

    echo json_encode([
        'success' => true,
        'period_days' => $daysCount,
        'overview' => [
            'total_sales' => $totalPeriodSales,
            'total_transactions' => $totalPeriodTransactions,
            'average_transaction_value' => $avgTransaction,
            'total_units_sold' => $totalPeriodUnits,
            'stores_analyzed' => $isSuperAdmin ? count($allStores) : 1
        ],
        'store_comparison' => $isSuperAdmin ? array_values($storeMetrics) : [],
        'fast_moving' => $fastMoving,
        'slow_moving' => $slowMoving,
        'stockout_risks' => $stockoutRisks,
        'transfer_recommendations' => $transferRecommendations
    ]);
    exit;

} catch (Exception $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'message' => 'Unable to calculate inventory insights',
        'error' => 'Unexpected server error. Details were written to the server log.'
    ]);
    exit;
}
