-- ============================================================================
-- StockEase - MySQL / MariaDB (XAMPP) schema
-- Converted 1:1 from the PostgreSQL `stockease_manual` database.
--
-- Import with:
--   C:\xampp\mysql\bin\mysql.exe -u root < schema_mysql.sql
--   C:\xampp\mysql\bin\mysql.exe -u root stockease < seed_mysql.sql
--
-- Notes vs PostgreSQL:
--   * SERIAL        -> INT AUTO_INCREMENT
--   * boolean       -> TINYINT(1) (1/0)
--   * timestamptz   -> DATETIME (no timezone storage; app uses local time)
--   * partial unique index idx_staff_shifts_active (WHERE status='active')
--     has no MySQL equivalent -> plain index; active-shift uniqueness is
--     enforced in shift_helper.php.
--   * transactions.source and transfer_events are NEW, added for panelist
--     feedback (non-POS "Other sales" report, transfer monitoring).
--   * FOREIGN KEYs are attached at the end (users <-> stores is circular).
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET SQL_MODE = '';

CREATE DATABASE IF NOT EXISTS stockease
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE stockease;

DROP TABLE IF EXISTS transfer_events;
DROP TABLE IF EXISTS transaction_items;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS staff_shifts;
DROP TABLE IF EXISTS stock_transfer_items;
DROP TABLE IF EXISTS stock_transfers;
DROP TABLE IF EXISTS subscription_requests;
DROP TABLE IF EXISTS subscriptions;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS stores;
DROP TABLE IF EXISTS businesses;
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS users;

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
  id                   INT NOT NULL AUTO_INCREMENT,
  full_name            VARCHAR(100) NOT NULL,
  username             VARCHAR(50)  NOT NULL,
  password             VARCHAR(255) NOT NULL,
  email                VARCHAR(150) NULL DEFAULT NULL,
  role                 VARCHAR(30)  NOT NULL,
  store_id             INT NULL DEFAULT NULL,
  is_active            TINYINT(1) NOT NULL DEFAULT 1,
  created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  must_change_password TINYINT(1) DEFAULT 1,
  phone                VARCHAR(30)  NULL DEFAULT NULL,
  address              VARCHAR(255) NULL DEFAULT NULL,
  occupation           VARCHAR(100) NULL DEFAULT NULL,
  sex                  VARCHAR(20)  NULL DEFAULT NULL,
  owner_id             INT NULL DEFAULT NULL,
  parent_id            INT NULL DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY users_username_key (username),
  UNIQUE KEY users_email_key (email),
  KEY idx_users_store (store_id),
  KEY idx_users_owner (owner_id),
  KEY idx_users_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- businesses
-- ---------------------------------------------------------------------------
CREATE TABLE businesses (
  id            INT NOT NULL AUTO_INCREMENT,
  business_name VARCHAR(150) NOT NULL,
  owner_id      INT NULL DEFAULT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_businesses_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- stores
-- ---------------------------------------------------------------------------
CREATE TABLE stores (
  id          INT NOT NULL AUTO_INCREMENT,
  store_name  VARCHAR(100) NOT NULL,
  location    VARCHAR(255) NULL DEFAULT NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at  DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  owner_id    INT NULL DEFAULT NULL,
  description TEXT NULL,
  manager_id  INT NULL DEFAULT NULL,
  business_id INT NULL DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_stores_owner (owner_id),
  KEY idx_stores_manager (manager_id),
  KEY idx_stores_business (business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- categories
-- ---------------------------------------------------------------------------
CREATE TABLE categories (
  id       INT NOT NULL AUTO_INCREMENT,
  store_id INT NOT NULL,
  name     VARCHAR(100) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_categories_store_name (store_id, name),
  KEY idx_categories_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- products
-- ---------------------------------------------------------------------------
CREATE TABLE products (
  id           INT NOT NULL AUTO_INCREMENT,
  store_id     INT NOT NULL,
  product_name VARCHAR(150) NOT NULL,
  category     VARCHAR(100) DEFAULT 'General',
  category_id  INT NULL DEFAULT NULL,
  price        DECIMAL(12,2) NOT NULL,
  stock        INT NOT NULL DEFAULT 0,
  created_at   DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  cost_price   DECIMAL(10,2) DEFAULT 0.00,
  min_stock    INT NOT NULL DEFAULT 10,
  PRIMARY KEY (id),
  KEY idx_products_store (store_id),
  KEY idx_products_category (category_id),
  CONSTRAINT chk_products_price CHECK (price >= 0),
  CONSTRAINT chk_products_stock CHECK (stock >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- transactions  (+ source: 'pos' | 'manual' -> "Other sales" report)
-- ---------------------------------------------------------------------------
CREATE TABLE transactions (
  id           INT NOT NULL AUTO_INCREMENT,
  store_id     INT NOT NULL,
  user_id      INT NOT NULL,
  total_amount DECIMAL(12,2) NOT NULL,
  payment_type VARCHAR(30) NOT NULL DEFAULT 'Cash',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status       VARCHAR(20) DEFAULT 'completed',
  source       VARCHAR(10) NOT NULL DEFAULT 'pos',
  PRIMARY KEY (id),
  KEY idx_transactions_store (store_id),
  KEY idx_transactions_user (user_id),
  KEY idx_transactions_created (created_at),
  KEY idx_transactions_source (source),
  CONSTRAINT chk_transactions_total CHECK (total_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- transaction_items
-- ---------------------------------------------------------------------------
CREATE TABLE transaction_items (
  id             INT NOT NULL AUTO_INCREMENT,
  transaction_id INT NOT NULL,
  product_id     INT NOT NULL,
  quantity       INT NOT NULL,
  unit_price     DECIMAL(12,2) NOT NULL,
  cost_price     DECIMAL(10,2) DEFAULT 0.00,
  PRIMARY KEY (id),
  KEY idx_transaction_items_tx (transaction_id),
  KEY idx_transaction_items_product (product_id),
  CONSTRAINT chk_transaction_items_price CHECK (unit_price >= 0),
  CONSTRAINT chk_transaction_items_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- staff_shifts
-- ---------------------------------------------------------------------------
CREATE TABLE staff_shifts (
  id         INT NOT NULL AUTO_INCREMENT,
  user_id    INT NOT NULL,
  store_id   INT NOT NULL,
  status     VARCHAR(20) NOT NULL DEFAULT 'active',
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at   DATETIME NULL DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_staff_shifts_user (user_id),
  KEY idx_staff_shifts_store (store_id),
  KEY idx_staff_shifts_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- stock_transfers
-- ---------------------------------------------------------------------------
CREATE TABLE stock_transfers (
  id             INT NOT NULL AUTO_INCREMENT,
  from_store_id  INT NULL DEFAULT NULL,
  to_store_id    INT NOT NULL,
  requested_by   INT NOT NULL,
  status         VARCHAR(20) NOT NULL DEFAULT 'requested',
  notes          TEXT NULL,
  created_at     DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  dispatched_at  DATETIME NULL DEFAULT NULL,
  received_at    DATETIME NULL DEFAULT NULL,
  completed_at   DATETIME NULL DEFAULT NULL,
  approved_by    INT NULL DEFAULT NULL,
  declined_at    DATETIME NULL DEFAULT NULL,
  declined_by    INT NULL DEFAULT NULL,
  decline_reason TEXT NULL,
  PRIMARY KEY (id),
  KEY idx_stock_transfers_from_store (from_store_id),
  KEY idx_stock_transfers_to_store (to_store_id),
  KEY idx_stock_transfers_status (status),
  KEY idx_stock_transfers_requested_by (requested_by),
  CONSTRAINT chk_stock_transfers_different_stores CHECK (from_store_id <> to_store_id),
  CONSTRAINT chk_stock_transfers_status
    CHECK (status IN ('requested','declined','in_transit','completed','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- stock_transfer_items
-- ---------------------------------------------------------------------------
CREATE TABLE stock_transfer_items (
  id            INT NOT NULL AUTO_INCREMENT,
  transfer_id   INT NOT NULL,
  product_id    INT NOT NULL,
  quantity      INT NOT NULL,
  base_price    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  selling_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  PRIMARY KEY (id),
  KEY idx_stock_transfer_items_transfer (transfer_id),
  KEY idx_stock_transfer_items_product (product_id),
  CONSTRAINT chk_stock_transfer_items_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- transfer_events  (NEW: full audit trail shown on the store admin's account)
-- ---------------------------------------------------------------------------
CREATE TABLE transfer_events (
  id          INT NOT NULL AUTO_INCREMENT,
  transfer_id INT NOT NULL,
  event       VARCHAR(40) NOT NULL,
  actor_id    INT NULL DEFAULT NULL,
  actor_name  VARCHAR(100) NOT NULL DEFAULT '',
  note        TEXT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_transfer_events_transfer (transfer_id),
  KEY idx_transfer_events_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- subscriptions
-- ---------------------------------------------------------------------------
CREATE TABLE subscriptions (
  id             INT NOT NULL AUTO_INCREMENT,
  owner_user_id  INT NOT NULL,
  plan           VARCHAR(20) NOT NULL DEFAULT 'free',
  billing_period VARCHAR(20) NULL DEFAULT NULL,
  status         VARCHAR(20) NOT NULL DEFAULT 'active',
  starts_at      DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at     DATETIME NULL DEFAULT NULL,
  trial_ends_at  DATETIME NULL DEFAULT NULL,
  created_at     DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_subscriptions_owner (owner_user_id),
  CONSTRAINT chk_subscription_period CHECK (billing_period IS NULL OR billing_period IN ('monthly','yearly')),
  CONSTRAINT chk_subscription_plan   CHECK (plan IN ('free','premium')),
  CONSTRAINT chk_subscription_status CHECK (status IN ('active','trial','expired','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- subscription_requests
-- ---------------------------------------------------------------------------
CREATE TABLE subscription_requests (
  id                INT NOT NULL AUTO_INCREMENT,
  user_id           INT NOT NULL,
  store_id          INT NULL DEFAULT NULL,
  plan              VARCHAR(50) NOT NULL DEFAULT 'premium',
  billing_period    VARCHAR(20) NOT NULL DEFAULT 'monthly',
  price             DECIMAL(10,2) NOT NULL DEFAULT 699.00,
  status            VARCHAR(20) NOT NULL DEFAULT 'pending',
  payment_reference TEXT NULL,
  notes             TEXT NULL,
  rejection_reason  TEXT NULL,
  reviewed_by       INT NULL DEFAULT NULL,
  created_at        DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at       DATETIME NULL DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_sub_req_user (user_id),
  KEY idx_sub_req_status (status),
  KEY idx_sub_req_store (store_id),
  KEY idx_sub_req_reviewed_by (reviewed_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- audit_logs
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
  id         INT NOT NULL AUTO_INCREMENT,
  user_name  VARCHAR(100) NOT NULL,
  role       VARCHAR(50)  NOT NULL,
  action     VARCHAR(100) NOT NULL,
  target     VARCHAR(100) NOT NULL,
  store_name VARCHAR(100) DEFAULT 'System',
  details    TEXT NULL,
  created_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_logs_created (created_at),
  KEY idx_audit_logs_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ---------------------------------------------------------------------------
-- Foreign keys (added last: users <-> stores is circular)
-- ---------------------------------------------------------------------------
ALTER TABLE users
  ADD CONSTRAINT fk_users_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE RESTRICT,
  ADD CONSTRAINT users_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE SET NULL,
  ADD CONSTRAINT users_parent_id_fk  FOREIGN KEY (parent_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE businesses
  ADD CONSTRAINT businesses_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE stores
  ADD CONSTRAINT stores_owner_id_fkey    FOREIGN KEY (owner_id)    REFERENCES users(id),
  ADD CONSTRAINT stores_manager_id_fkey  FOREIGN KEY (manager_id)  REFERENCES users(id) ON DELETE SET NULL,
  ADD CONSTRAINT stores_business_id_fkey FOREIGN KEY (business_id) REFERENCES businesses(id) ON DELETE SET NULL;

ALTER TABLE categories
  ADD CONSTRAINT fk_categories_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE RESTRICT;

ALTER TABLE products
  ADD CONSTRAINT fk_products_store    FOREIGN KEY (store_id)    REFERENCES stores(id)     ON DELETE RESTRICT,
  ADD CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL;

ALTER TABLE transactions
  ADD CONSTRAINT fk_transactions_store FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_transactions_user  FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE RESTRICT;

ALTER TABLE transaction_items
  ADD CONSTRAINT fk_transaction_items_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_transaction_items_product     FOREIGN KEY (product_id)     REFERENCES products(id)     ON DELETE RESTRICT;

ALTER TABLE staff_shifts
  ADD CONSTRAINT staff_shifts_user_id_fkey  FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE CASCADE,
  ADD CONSTRAINT staff_shifts_store_id_fkey FOREIGN KEY (store_id) REFERENCES stores(id) ON DELETE CASCADE;

ALTER TABLE stock_transfers
  ADD CONSTRAINT fk_stock_transfers_from_store    FOREIGN KEY (from_store_id) REFERENCES stores(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_stock_transfers_to_store      FOREIGN KEY (to_store_id)   REFERENCES stores(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_stock_transfers_requested_by  FOREIGN KEY (requested_by)  REFERENCES users(id)  ON DELETE RESTRICT,
  ADD CONSTRAINT stock_transfers_approved_by_fkey FOREIGN KEY (approved_by)   REFERENCES users(id)  ON DELETE SET NULL,
  ADD CONSTRAINT stock_transfers_declined_by_fkey FOREIGN KEY (declined_by)   REFERENCES users(id)  ON DELETE SET NULL;

ALTER TABLE stock_transfer_items
  ADD CONSTRAINT fk_stock_transfer_items_transfer FOREIGN KEY (transfer_id) REFERENCES stock_transfers(id) ON DELETE RESTRICT,
  ADD CONSTRAINT fk_stock_transfer_items_product  FOREIGN KEY (product_id)  REFERENCES products(id)        ON DELETE RESTRICT;

ALTER TABLE transfer_events
  ADD CONSTRAINT fk_transfer_events_transfer FOREIGN KEY (transfer_id) REFERENCES stock_transfers(id) ON DELETE CASCADE;

ALTER TABLE subscriptions
  ADD CONSTRAINT fk_subscriptions_owner FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE subscription_requests
  ADD CONSTRAINT subscription_requests_user_id_fkey    FOREIGN KEY (user_id)    REFERENCES users(id)  ON DELETE CASCADE,
  ADD CONSTRAINT subscription_requests_store_id_fkey   FOREIGN KEY (store_id)   REFERENCES stores(id) ON DELETE SET NULL,
  ADD CONSTRAINT subscription_requests_reviewed_by_fkey FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL;

SET FOREIGN_KEY_CHECKS = 1;
