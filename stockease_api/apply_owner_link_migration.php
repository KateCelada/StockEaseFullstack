<?php
/**
 * Owner Link Migration
 *
 * - users.owner_id: persistent link from a team member (manager / store admin /
 *   staff) back to the store owner who leads them.
 *
 * Why: the flow is "store owner creates the manager account -> the manager adds
 * the branches -> the manager creates the store admin assigned to a branch".
 * The manager exists before any branch does, so the old link (which only
 * existed through stores.owner_id / stores.manager_id) could not yet describe
 * who that manager works for.
 *
 * Run once: php apply_owner_link_migration.php   (or open it in the browser)
 */

require_once __DIR__ . '/db.php';

$log = [];

function run_migration(PDO $conn, string $label, string $sql, array &$log): void
{
    try {
        $conn->exec($sql);
        $log[] = "OK: $label";
    } catch (PDOException $e) {
        $log[] = "SKIP/ERR: $label -> " . $e->getMessage();
    }
}

// All statements below are passed with the shared $log collector.
$logRef =& $log;

try {
    // 1. The link column
    run_migration($conn, 'Add users.owner_id column', "
        ALTER TABLE users ADD COLUMN IF NOT EXISTS owner_id INTEGER
    ", $log);

    // 2. Self-referencing foreign key (deleting the owner leaves the member unlinked)
    run_migration($conn, 'Add users.owner_id foreign key', "
        DO \$\$ BEGIN
            IF NOT EXISTS (
                SELECT 1 FROM pg_constraint WHERE conname = 'users_owner_id_fkey'
            ) THEN
                ALTER TABLE users
                    ADD CONSTRAINT users_owner_id_fkey
                    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE SET NULL;
            END IF;
        END \$\$;
    ", $log);

    // 3. Backfill from an existing branch assignment
    run_migration($conn, 'Backfill owner link from assigned store', "
        UPDATE users u SET owner_id = s.owner_id
        FROM stores s
        WHERE u.store_id = s.id
          AND u.owner_id IS NULL
          AND s.owner_id IS NOT NULL
          AND u.id <> s.owner_id
    ", $log);

    // 4. Backfill from a branch the member already manages
    run_migration($conn, 'Backfill owner link from managed store', "
        UPDATE users u SET owner_id = s.owner_id
        FROM stores s
        WHERE s.manager_id = u.id
          AND u.owner_id IS NULL
          AND s.owner_id IS NOT NULL
          AND u.id <> s.owner_id
    ", $log);
} catch (PDOException $e) {
    $log[] = "FATAL: " . $e->getMessage();
}

header('Content-Type: text/plain; charset=UTF-8');
echo "Owner link migration\n";
echo implode("\n", $log) . "\n";
