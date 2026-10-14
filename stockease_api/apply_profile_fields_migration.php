<?php
require_once __DIR__ . '/db.php';

try {
    echo "Running Profile Fields Migration...\n";

    // users: editable profile columns (phone, address, occupation, sex)
    $conn->exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(30) DEFAULT NULL;");
    echo "+ users.phone column added or verified.\n";

    $conn->exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS address VARCHAR(255) DEFAULT NULL;");
    echo "+ users.address column added or verified.\n";

    $conn->exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS occupation VARCHAR(100) DEFAULT NULL;");
    echo "+ users.occupation column added or verified.\n";

    $conn->exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS sex VARCHAR(20) DEFAULT NULL;");
    echo "+ users.sex column added or verified.\n";

    echo "Profile Fields Migration completed successfully!\n";
} catch (PDOException $e) {
    echo "Migration Error: " . $e->getMessage() . "\n";
}
?>
