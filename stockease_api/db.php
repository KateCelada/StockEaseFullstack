<?php
// StockEase database connection - MySQL / MariaDB
//
// Local development uses the MySQL server that ships with XAMPP
// (MariaDB 10.4 on port 3306). The same file works in the cloud
// (Render + Railway MySQL) by reading connection settings from
// environment variables - no code changes between environments.
//
// Resolution order for each setting:
//   1. DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASS   (explicit override)
//   2. MYSQLHOST / MYSQLPORT / MYSQLDATABASE / MYSQLUSER / MYSQLPASSWORD
//                                                        (Railway sets these)
//   3. XAMPP local defaults below
//
// Railway (and most managed MySQL) requires TLS for external connections.
// If the MYSQL_CERT env var is present (Railway exposes the CA PEM there),
// it is written to a temp file and passed to PDO as the SSL CA.
//
// Local setup (unchanged):
//   1. Start MySQL in the XAMPP Control Panel.
//   2. C:\xampp\mysql\bin\mysql.exe -u root < schema_mysql.sql
//   3. C:\xampp\mysql\bin\mysql.exe -u root stockease < seed_mysql.sql
//
// Default XAMPP root has an empty password - set $password if you changed it.

$env = function ($key, $default = null) {
    $value = getenv($key);
    return ($value !== false && $value !== '') ? $value : $default;
};

$host     = $env('DB_HOST', $env('MYSQLHOST', '127.0.0.1'));
$port     = $env('DB_PORT', $env('MYSQLPORT', '3306'));
$dbname   = $env('DB_NAME', $env('MYSQLDATABASE', 'stockease'));
$user     = $env('DB_USER', $env('MYSQLUSER', 'root'));
$password = $env('DB_PASS', $env('MYSQLPASSWORD', ''));

// The machine and MySQL both run at UTC+8 while php.ini ships with
// date.timezone=Europe/Berlin. Timestamps written from PHP into DATETIME
// columns must be in the same zone as the database's CURRENT_TIMESTAMP,
// otherwise every PHP-generated date lands 6 hours off.
date_default_timezone_set('Asia/Singapore');

$options = [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    // The endpoints reuse named placeholders (e.g. ":uid OR manager_id = :uid"),
    // which PostgreSQL's PDO rewrote client-side. MySQL's native prepares
    // reject that (HY093), so keep emulation on - it matches the old behaviour.
    PDO::ATTR_EMULATE_PREPARES => true
];

// TLS for managed MySQL (Railway, Aiven, ...). Local XAMPP needs none.
$railwayCert = getenv('MYSQL_CERT');
$sslCa       = getenv('DB_SSL_CA');
if ($railwayCert !== false && $railwayCert !== '') {
    $caFile = sys_get_temp_dir() . '/stockease_db_ca.pem';
    if (!file_exists($caFile) || sha1_file($caFile) !== sha1($railwayCert)) {
        file_put_contents($caFile, $railwayCert);
    }
    $options[PDO::MYSQL_ATTR_SSL_CA] = $caFile;
} elseif ($sslCa !== false && $sslCa !== '') {
    $options[PDO::MYSQL_ATTR_SSL_CA] = $sslCa;
}
if ($env('DB_SSL_NO_VERIFY') === '1') {
    $options[PDO::MYSQL_ATTR_SSL_VERIFY_SERVER_CERT] = false;
}

try {
    $conn = new PDO(
        "mysql:host=$host;port=$port;dbname=$dbname;charset=utf8mb4",
        $user,
        $password,
        $options
    );
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Database connection failed"]);
    exit();
}
?>
