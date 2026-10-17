<?php
require_once 'db.php';

try {
    // 1. Add description to stores if not exists
    $conn->exec("
        DO $$
        BEGIN
            IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='stores' AND column_name='description') THEN
                ALTER TABLE stores ADD COLUMN description TEXT DEFAULT '';
            END IF;
            IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='stores' AND column_name='manager_id') THEN
                ALTER TABLE stores ADD COLUMN manager_id INT REFERENCES users(id) ON DELETE SET NULL;
            END IF;
        END $$;
    ");

    // 2. Add approved_by to stock_transfers if not exists
    $conn->exec("
        DO $$
        BEGIN
            IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='stock_transfers' AND column_name='approved_by') THEN
                ALTER TABLE stock_transfers ADD COLUMN approved_by INT REFERENCES users(id) ON DELETE SET NULL;
            END IF;
        END $$;
    ");

    echo json_encode(["success" => true, "message" => "Database columns updated successfully."]);
} catch (Exception $e) {
    error_log('migrate_stores_schema caught exception: ' . $e->getMessage());
    echo json_encode(["success" => false, "error" => "Unexpected server error. Details were written to the server log."]);
}

