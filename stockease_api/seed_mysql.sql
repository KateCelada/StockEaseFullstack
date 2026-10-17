-- ============================================================================
-- StockEase - seed data for the MySQL (XAMPP) database
-- Converted from the PostgreSQL `stockease_manual` dump (pg_dump, 15 Oct 2026).
-- Timestamps: fractional seconds and +08 offsets stripped (DATETIME).
-- booleans: t/f -> 1/0,  '\N' -> NULL
--
-- Import with:
--   C:\xampp\mysql\bin\mysql.exe -u root stockease < seed_mysql.sql
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

USE stockease;

DELETE FROM audit_logs;
DELETE FROM subscription_requests;
DELETE FROM subscriptions;
DELETE FROM transfer_events;
DELETE FROM stock_transfer_items;
DELETE FROM stock_transfers;
DELETE FROM transaction_items;
DELETE FROM transactions;
DELETE FROM staff_shifts;
DELETE FROM products;
DELETE FROM categories;
DELETE FROM stores;
DELETE FROM businesses;
DELETE FROM users;

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
INSERT INTO users
  (id, full_name, username, password, email, role, store_id, is_active, created_at,
   must_change_password, phone, address, occupation, sex, owner_id, parent_id)
VALUES
  (1, 'System Super Admin', 'superadmin', '$2y$10$Q0KM/t998N9k3FD345bxIugMC6NSB0k0SrdapLC8OQFnnFQ.Zm3Oq',
      'superadmin@stockease.local', 'super_admin', NULL, 1, '2026-09-25 22:01:25',
      0, NULL, NULL, NULL, NULL, NULL, NULL),
  (2, 'Kate Celada', 'katecelada', '$2y$10$rV/BnWp/BZZlU5.uMvn0huh6QkCCdMQH/c4XQ.6kTMIQAHUnhH2gi',
      'katecelada@gmail.com', 'store_owner', 1, 1, '2026-10-15 14:07:36',
      0, NULL, NULL, NULL, NULL, NULL, NULL),
  (3, 'Laurence Enopia', 'laurence', '$2y$10$kv8aZ2ZloV0.EHWLHVdwAOEXJrfb5/NgOnQOSS7jRkW.w6VXkJzBO',
      'laurence@stockease.local', 'manager', 1, 1, '2026-10-15 14:09:16',
      0, NULL, NULL, NULL, NULL, 2, NULL),
  (4, 'John Jucal', 'john', '$2y$10$UY5P7wpvrQrtoUVwTkasDunCm2nlo/.Td.OIWEQusG1yVyMWCLmVq',
      'john@stockease.local', 'store_admin', 1, 1, '2026-10-15 14:11:11',
      0, NULL, NULL, NULL, NULL, 2, NULL),
  (6, 'Xander Sepra', 'xander', '$2y$10$VyQ9KXAeqj2XmnCV.lt8F.8EiaYsZnCEIOU8y8hIUM4uov4bGYOE2',
      NULL, 'inventory_checker', 1, 1, '2026-10-15 14:12:40',
      0, NULL, NULL, NULL, NULL, NULL, NULL),
  (5, 'Neil Piecco', 'neil', '$2y$10$KP1cWc0rck9n8oOrUKKNc.6hgC5Gir4wbLtPr917/x1S1jCxPGODK',
      NULL, 'staff', 1, 1, '2026-10-15 14:12:20',
      0, NULL, NULL, NULL, NULL, NULL, NULL);

-- ---------------------------------------------------------------------------
-- businesses
-- ---------------------------------------------------------------------------
INSERT INTO businesses (id, business_name, owner_id, created_at)
VALUES (1, 'Osave', 2, '2026-10-15 14:07:36');

-- ---------------------------------------------------------------------------
-- stores
-- ---------------------------------------------------------------------------
INSERT INTO stores (id, store_name, location, status, created_at, owner_id, description, manager_id, business_id)
VALUES (1, 'Osave-Colon Branch', 'Colon, Cebu', 'Active', '2026-10-15 14:10:33', 2, '', 3, 1);

-- ---------------------------------------------------------------------------
-- categories
-- ---------------------------------------------------------------------------
INSERT INTO categories (id, store_id, name) VALUES
  (1, 1, 'Beverage'),
  (2, 1, 'Snacks');

-- ---------------------------------------------------------------------------
-- products
-- ---------------------------------------------------------------------------
INSERT INTO products
  (id, store_id, product_name, category, category_id, price, stock, created_at, cost_price, min_stock)
VALUES
  (1, 1, 'Coke Zero 2L', 'Beverage', 1, 90.00, 20, '2026-10-15 14:13:57', 85.00, 20),
  (2, 1, 'Skyflakes',    'Snacks',   2, 12.00, 60, '2026-10-15 14:28:27', 10.00, 20);

-- ---------------------------------------------------------------------------
-- transactions / items
-- ---------------------------------------------------------------------------
INSERT INTO transactions (id, store_id, user_id, total_amount, payment_type, created_at, status, source)
VALUES (1, 1, 5, 2700.00, 'Cash', '2026-10-15 14:15:22', 'completed', 'pos');

INSERT INTO transaction_items (id, transaction_id, product_id, quantity, unit_price, cost_price)
VALUES (1, 1, 1, 30, 90.00, 85.00);

-- ---------------------------------------------------------------------------
-- staff_shifts
-- ---------------------------------------------------------------------------
INSERT INTO staff_shifts (id, user_id, store_id, status, started_at, ended_at, created_at) VALUES
  (20, 6, 1, 'ended', '2026-10-15 14:12:59', '2026-10-15 14:14:24', '2026-10-15 14:12:59'),
  (21, 5, 1, 'ended', '2026-10-15 14:14:40', '2026-10-15 14:18:04', '2026-10-15 14:14:40'),
  (22, 6, 1, 'ended', '2026-10-15 14:27:44', '2026-10-15 14:28:36', '2026-10-15 14:27:44'),
  (23, 5, 1, 'ended', '2026-10-15 14:28:44', '2026-10-15 14:34:01', '2026-10-15 14:28:44');

-- ---------------------------------------------------------------------------
-- subscriptions
-- ---------------------------------------------------------------------------
INSERT INTO subscriptions
  (id, owner_user_id, plan, billing_period, status, starts_at, expires_at, trial_ends_at, created_at, updated_at)
VALUES
  (1, 1, 'premium', 'monthly', 'active', '2026-10-15 14:22:52', '2026-11-15 14:22:52', NULL,
   '2026-10-15 14:22:52', '2026-10-15 14:22:52');

-- ---------------------------------------------------------------------------
-- subscription_requests
-- ---------------------------------------------------------------------------
INSERT INTO subscription_requests
  (id, user_id, store_id, plan, billing_period, price, status, payment_reference, notes,
   rejection_reason, reviewed_by, created_at, reviewed_at)
VALUES
  (1, 2, 1, 'premium', 'monthly', 699.00, 'approved', '', '', '', 1,
   '2026-10-15 14:22:38', '2026-10-15 14:22:52');

-- ---------------------------------------------------------------------------
-- audit_logs
-- ---------------------------------------------------------------------------
INSERT INTO audit_logs
  (id, user_name, role, action, target, store_name, details, created_at)
VALUES
  (14, 'System Super Admin', 'super_admin', 'SYSTEM_RESET', 'System Database', 'Global System',
       'All demo accounts, stores, products, and transactions cleared. System initialized.', '2026-10-15 11:30:04'),
  (2, 'Kate Celada', 'store_owner', 'PASSWORD_CHANGED', 'User #2 (katecelada)', 'System',
       'User successfully changed their account password.', '2026-10-15 14:08:38'),
  (3, 'Laurence Enopia', 'manager', 'PASSWORD_CHANGED', 'User #3 (laurence)', 'System',
       'User successfully changed their account password.', '2026-10-15 14:09:54'),
  (4, 'John Jucal', 'store_admin', 'PASSWORD_CHANGED', 'User #4 (john)', 'System',
       'User successfully changed their account password.', '2026-10-15 14:11:39'),
  (5, 'neil', 'store_admin', 'STAFF_CREATED', 'Neil Piecco (Cashier)', 'Osave-Colon Branch',
       'Staff created with initial temporary password.', '2026-10-15 14:12:20'),
  (6, 'xander', 'store_admin', 'STAFF_CREATED', 'Xander Sepra (Inventory Staff)', 'Osave-Colon Branch',
       'Staff created with initial temporary password.', '2026-10-15 14:12:41'),
  (7, 'Xander Sepra', 'inventory_checker', 'PASSWORD_CHANGED', 'User #6 (xander)', 'System',
       'User successfully changed their account password.', '2026-10-15 14:13:11'),
  (8, 'Neil Piecco', 'staff', 'PASSWORD_CHANGED', 'User #5 (neil)', 'System',
       'User successfully changed their account password.', '2026-10-15 14:14:51'),
  (9, 'Kate Celada', 'store_owner', 'SUBSCRIPTION_REQUESTED', 'Request #1', 'Store #1',
       'Store Admin Kate Celada applied for StockEase Premium (monthly, P699). Awaiting Super Admin approval.', '2026-10-15 14:22:38'),
  (10, 'System Super Admin', 'super_admin', 'SUBSCRIPTION_APPROVED', 'Request #1', 'Osave-Colon Branch',
       'Super Admin System Super Admin APPROVED Premium (monthly) for Store Admin Kate Celada (Osave-Colon Branch). Valid until 2026-11-15 06:22:52.', '2026-10-15 14:22:52');

SET FOREIGN_KEY_CHECKS = 1;
