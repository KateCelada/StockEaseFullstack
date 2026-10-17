<?php
/**
 * Adds a parent link on users so the account hierarchy can be enforced even
 * before a manager is assigned to a branch:
 *
 *   super_admin -> store_owner -> manager -> store_admin -> staff (cashier/inventory)
 *
 * Run once: http://localhost/stockease_api/apply_parent_link_migration.php
 */
require_once __DIR__ . '/db.php';

header('Content-Type: text/plain; charset=UTF-8');

try {
    $exists = $conn->query("
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'users' AND column_name = 'parent_id'
    ")->fetchColumn();

    if (!(int)$exists) {
        $conn->exec("ALTER TABLE users ADD COLUMN parent_id INTEGER");
        echo "Added users.parent_id\n";
    } else {
        echo "users.parent_id already exists\n";
    }

    $fk = $conn->query("
        SELECT COUNT(*) FROM pg_constraint
        WHERE conname = 'users_parent_id_fk' AND conrelid = 'users'::regclass
    ")->fetchColumn();
    if (!(int)$fk) {
        $conn->exec("ALTER TABLE users ADD CONSTRAINT users_parent_id_fk
                     FOREIGN KEY (parent_id) REFERENCES users(id) ON DELETE SET NULL");
        echo "Added users_parent_id_fk\n";
    } else {
        echo "users_parent_id_fk already exists\n";
    }

    // Backfill existing rows from the branch relationships we already know.
    $conn->exec("
        UPDATE users u
        SET parent_id = s.owner_id
        FROM stores s
        WHERE u.store_id = s.id
          AND u.parent_id IS NULL
          AND s.owner_id IS NOT NULL
          AND u.id <> s.owner_id
          AND u.role IN ('manager', 'store_admin', 'staff', 'inventory_checker')
    ");
    $conn->exec("
        UPDATE users u
        SET parent_id = s.manager_id
        FROM stores s
        WHERE u.store_id = s.id
          AND u.parent_id IS NULL
          AND s.manager_id IS NOT NULL
          AND u.id <> s.manager_id
          AND u.role IN ('store_admin', 'staff', 'inventory_checker')
    ");
    $conn->exec("
        UPDATE users u
        SET parent_id = b.owner_id
        FROM businesses b
        WHERE u.role = 'store_owner'
          AND u.parent_id IS NULL
          AND b.owner_id = u.id
          AND 1 = 0
    ");

    echo "Backfill complete.\n";
    echo "parent_id set for " . $conn->query("SELECT COUNT(*) FROM users WHERE parent_id IS NOT NULL")->fetchColumn() . " user(s).\n";
} catch (PDOException $e) {
    http_response_code(500);
    echo "ERROR: " . $e->getMessage() . "\n";
}
