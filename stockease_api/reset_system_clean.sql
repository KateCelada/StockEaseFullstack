-- Remove all existing transactions, items, transfers, products, categories, stores, subscriptions, and accounts except super admin (user 1)

-- 1. Unlink foreign keys
UPDATE users SET store_id = NULL;

-- 2. Delete transactions and items
DELETE FROM transaction_items;
DELETE FROM transactions;

-- 3. Delete stock transfers
DELETE FROM stock_transfer_items;
DELETE FROM stock_transfers;

-- 4. Delete subscriptions
DELETE FROM subscription_requests;
DELETE FROM subscriptions;

-- 5. Delete products and categories
DELETE FROM products;
DELETE FROM categories;

-- 6. Delete stores
DELETE FROM stores;

-- 7. Delete all users except System Super Admin (id = 1)
DELETE FROM users WHERE id != 1;

-- 8. Clean audit logs and leave a single fresh system initialization log
DELETE FROM audit_logs;
INSERT INTO audit_logs (user_name, role, action, target, store_name, details, created_at)
VALUES ('System Super Admin', 'super_admin', 'SYSTEM_RESET', 'System Database', 'Global System', 'All demo accounts, stores, products, and transactions cleared. System initialized.', CURRENT_TIMESTAMP);

-- 9. Ensure Super Admin settings
UPDATE users 
SET role = 'super_admin', 
    store_id = NULL, 
    must_change_password = FALSE,
    full_name = 'System Super Admin',
    email = 'superadmin@stockease.local'
WHERE id = 1;

-- 10. Reset sequences
ALTER SEQUENCE stores_id_seq RESTART WITH 1;
ALTER SEQUENCE products_id_seq RESTART WITH 1;
ALTER SEQUENCE transactions_id_seq RESTART WITH 1;
ALTER SEQUENCE transaction_items_id_seq RESTART WITH 1;
ALTER SEQUENCE stock_transfers_id_seq RESTART WITH 1;
ALTER SEQUENCE stock_transfer_items_id_seq RESTART WITH 1;
ALTER SEQUENCE categories_id_seq RESTART WITH 1;
ALTER SEQUENCE subscriptions_id_seq RESTART WITH 1;
ALTER SEQUENCE subscription_requests_id_seq RESTART WITH 1;
ALTER SEQUENCE audit_logs_id_seq RESTART WITH 2;
ALTER SEQUENCE users_id_seq RESTART WITH 2;
