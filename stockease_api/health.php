<?php
// StockEase health check - reports whether the API can reach its database.
// Intentionally unauthenticated: it exposes nothing but "ok / not ok",
// so it is safe to open in a browser or ping from a monitoring tool.
//
//   GET /stockease_api/health.php   -> 200 {"success":true,...} when the DB answers
//                                    -> 503 {"success":false,...} when it does not
require __DIR__ . '/db.php';   // connects (env vars on Render, XAMPP locally) or exits 503

header('Content-Type: application/json');
try {
    $users = (int)$conn->query("SELECT COUNT(*) FROM users")->fetchColumn();
    echo json_encode([
        'success'   => true,
        'message'   => 'StockEase API is healthy',
        'database'  => 'ok',
        'users'     => $users,
        'timestamp' => date('Y-m-d H:i:s'),
    ]);
} catch (PDOException $e) {
    http_response_code(503);
    echo json_encode(["success" => false, "message" => "Database unreachable"]);
}
?>
