<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: GET");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);

try {
    // Optional profile columns (added by apply_profile_fields_migration.php).
    // The client treats phone/address/occupation/sex as optional and falls
    // back to blanks, so missing columns must not break this endpoint.
    $colStmt = $conn->query(
        "SELECT column_name FROM information_schema.columns
         WHERE table_schema = current_schema() AND table_name = 'users'"
    );
    $existingColumns = array_map('strtolower', $colStmt->fetchAll(PDO::FETCH_COLUMN));

    $optionalSelect = '';
    foreach (['phone', 'address', 'occupation', 'sex'] as $field) {
        if (in_array($field, $existingColumns, true)) {
            $optionalSelect .= ", u.{$field}";
        }
    }

    $stmt = $conn->prepare("
        SELECT u.id, u.username, u.email, u.full_name, u.role, u.store_id, u.is_active{$optionalSelect},
               s.store_name, s.location AS store_location
        FROM users u
        LEFT JOIN stores s ON u.store_id = s.id
        WHERE u.id = :user_id
        LIMIT 1
    ");
    $stmt->execute(['user_id' => $identity['user_id']]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$user) {
        http_response_code(404);
        echo json_encode(["success" => false, "message" => "User account not found."]);
        exit();
    }

    echo json_encode([
        "success" => true,
        "user" => [
            "id" => (int)$user['id'],
            "username" => $user['username'],
            "email" => $user['email'] ?? '',
            "full_name" => $user['full_name'],
            "role" => $user['role'],
            "store_id" => $user['store_id'] !== null ? (int)$user['store_id'] : null,
            "store_name" => $user['store_name'] ?? '',
            "store_location" => $user['store_location'] ?? '',
            "is_active" => in_array($user['is_active'], [true, 1, '1', 't', 'true'], true),
            "phone" => (string)($user['phone'] ?? ''),
            "address" => (string)($user['address'] ?? ''),
            "occupation" => (string)($user['occupation'] ?? ''),
            "sex" => (string)($user['sex'] ?? '')
        ]
    ]);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Database error loading profile."]);
}
?>
