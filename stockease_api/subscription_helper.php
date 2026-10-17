<?php
// Centralized StockEase Subscription Engine & Feature Gating

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

const SUBSCRIPTION_PRICING = [
    'monthly' => [
        'period' => 'monthly',
        'price' => 699.00,
        'currency' => 'PHP',
        'symbol' => '₱',
        'display_price' => '₱699 / month',
        'interval_months' => 1
    ],
    'yearly' => [
        'period' => 'yearly',
        'price' => 6990.00,
        'currency' => 'PHP',
        'symbol' => '₱',
        'display_price' => '₱6,990 / year',
        'savings_text' => 'Save ₱1,398 compared with monthly billing',
        'interval_months' => 12
    ]
];

const PREMIUM_FEATURES = [
    'stock_transfers' => 'Multi-Store Stock Transfers',
    'inventory_insights' => 'Smart Inventory Insights',
    'transfer_recommendations' => 'Transfer Recommendations',
    'multi_store_analytics' => 'Multi-Store Analytics & Comparison',
    'advanced_reports' => 'Advanced Extended Reporting'
];

const FREE_PRODUCT_LIMIT = 15;

/**
 * Checks if the store has exceeded the 15-product limit for free tiers.
 */
function subscription_check_product_limit(PDO $conn, int $storeId, ?array $identity = null): void
{
    if (subscription_is_premium($conn, $identity)) {
        return; // Unlimited products for Premium subscribers
    }

    $stmt = $conn->prepare("SELECT COUNT(*) FROM products WHERE store_id = :store_id");
    $stmt->execute(['store_id' => $storeId]);
    $currentCount = (int)$stmt->fetchColumn();

    if ($currentCount >= FREE_PRODUCT_LIMIT) {
        http_response_code(403);
        header('Content-Type: application/json; charset=UTF-8');
        echo json_encode([
            'success' => false,
            'code' => 'SUBSCRIPTION_LIMIT_REACHED',
            'product_count' => $currentCount,
            'limit' => FREE_PRODUCT_LIMIT,
            'message' => 'Free tier limit reached: You can only add up to ' . FREE_PRODUCT_LIMIT . ' products. Subscribe to StockEase Premium to add unlimited products!'
        ]);
        exit;
    }
}

/**
 * Resolves the business owner user id.
 * In StockEase, the super_admin account represents the Business Owner.
 */
function subscription_get_owner_user_id(PDO $conn, ?array $identity = null): int
{
    if ($identity && ($identity['role'] ?? '') === 'super_admin' && !empty($identity['user_id'])) {
        return (int)$identity['user_id'];
    }

    $stmt = $conn->query("SELECT id FROM users WHERE role = 'super_admin' ORDER BY id ASC LIMIT 1");
    $ownerId = $stmt->fetchColumn();
    return $ownerId ? (int)$ownerId : 1;
}

/**
 * Returns current business subscription details with computed active status.
 */
function subscription_get_current(PDO $conn, ?array $identity = null): array
{
    $ownerUserId = subscription_get_owner_user_id($conn, $identity);

    $stmt = $conn->prepare("
        SELECT id, owner_user_id, plan, billing_period, status,
               starts_at as starts_at,
               expires_at as expires_at,
               trial_ends_at as trial_ends_at,
               created_at as created_at,
               updated_at as updated_at,
               CASE 
                   WHEN expires_at IS NOT NULL AND expires_at < CURRENT_TIMESTAMP THEN 1 
                   ELSE 0 
               END AS is_past_expiration,
               CASE 
                   WHEN trial_ends_at IS NOT NULL AND trial_ends_at < CURRENT_TIMESTAMP THEN 1 
                   ELSE 0 
               END AS is_past_trial
        FROM subscriptions
        WHERE owner_user_id = :owner_id
        ORDER BY id DESC
        LIMIT 1
    ");
    $stmt->execute(['owner_id' => $ownerUserId]);
    $sub = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$sub) {
        // Fallback default Free subscription
        return [
            'plan' => 'free',
            'billing_period' => null,
            'status' => 'active',
            'is_premium' => false,
            'is_active' => true,
            'starts_at' => null,
            'expires_at' => null,
            'trial_ends_at' => null,
            'pricing' => SUBSCRIPTION_PRICING,
            'features' => PREMIUM_FEATURES
        ];
    }

    $plan = strtolower($sub['plan'] ?? 'free');
    $status = strtolower($sub['status'] ?? 'active');
    // MySQL returns 0/1 for the computed CASE columns - expose real booleans
    // so JSON consumers keep seeing true/false like they did on PostgreSQL.
    $sub['is_past_expiration'] = !empty($sub['is_past_expiration']);
    $sub['is_past_trial'] = !empty($sub['is_past_trial']);
    $isPastExpiration = $sub['is_past_expiration'];
    $isPastTrial = $sub['is_past_trial'];

    $isPremium = false;

    if ($plan === 'premium') {
        if ($status === 'active') {
            $isPremium = !$isPastExpiration;
        } elseif ($status === 'trial') {
            $isPremium = !$isPastTrial;
        } elseif ($status === 'cancelled') {
            // Cancelled remains active until the end of the paid billing cycle
            $isPremium = !$isPastExpiration;
        } else {
            // expired or other status
            $isPremium = false;
        }
    }

    $sub['is_premium'] = $isPremium;
    $sub['pricing'] = SUBSCRIPTION_PRICING;
    $sub['features'] = PREMIUM_FEATURES;

    return $sub;
}

/**
 * Checks whether Premium capabilities are currently active for the business.
 */
function subscription_is_premium(PDO $conn, ?array $identity = null): bool
{
    $sub = subscription_get_current($conn, $identity);
    return !empty($sub['is_premium']);
}

/**
 * Server-side gating. If account does not have active Premium, responds with HTTP 403 PREMIUM_REQUIRED.
 */
function subscription_require_premium(PDO $conn, ?array $identity = null): void
{
    if (!subscription_is_premium($conn, $identity)) {
        http_response_code(403);
        header('Content-Type: application/json; charset=UTF-8');
        echo json_encode([
            'success' => false,
            'code' => 'PREMIUM_REQUIRED',
            'message' => 'This feature requires StockEase Premium'
        ]);
        exit;
    }
}
