<?php
// StockEase database connection - MySQL / MariaDB (XAMPP)
//
// The project standardizes on a single database: the MySQL server that ships
// with XAMPP (MariaDB 10.4 on port 3306). The previous PostgreSQL connection
// (port 5432) has been removed so the API and phpMyAdmin share one store.
//
// Setup:
//   1. Start MySQL in the XAMPP Control Panel.
//   2. C:\xampp\mysql\bin\mysql.exe -u root < schema_mysql.sql
//   3. C:\xampp\mysql\bin\mysql.exe -u root stockease < seed_mysql.sql
//
// Default XAMPP root has an empty password - set $password if you changed it.
$host = "127.0.0.1";
$port = "3306";
$dbname = "stockease";
$user = "root";
$password = "";

// The machine and MySQL both run at UTC+8 while php.ini ships with
// date.timezone=Europe/Berlin. Timestamps written from PHP into DATETIME
// columns must be in the same zone as the database's CURRENT_TIMESTAMP,
// otherwise every PHP-generated date lands 6 hours off.
date_default_timezone_set('Asia/Singapore');

try {
    $conn = new PDO(
        "mysql:host=$host;port=$port;dbname=$dbname;charset=utf8mb4",
        $user,
        $password,
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            // The endpoints reuse named placeholders (e.g. ":uid OR manager_id = :uid"),
            // which PostgreSQL's PDO rewrote client-side. MySQL's native prepares
            // reject that (HY093), so keep emulation on - it matches the old behaviour.
            PDO::ATTR_EMULATE_PREPARES => true
        ]
    );
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Database connection failed"]);
    exit();
}
?>
