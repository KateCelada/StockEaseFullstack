<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);
auth_require_roles($identity, ['store_admin', 'super_admin']);

$data = json_decode(file_get_contents("php://input"), true);

$requestedStoreId = isset($data['store_id']) ? (int)$data['store_id'] : 0;
$storeId = auth_resolve_store_context($conn, $identity, $requestedStoreId, true);

$name = isset($data['name']) ? trim((string)$data['name']) : '';

if (empty($name)) {
    echo json_encode([
        "success" => false,
        "message" => "Category name cannot be empty."
    ]);
    exit();
}

if (strlen($name) > 100) {
    echo json_encode([
        "success" => false,
        "message" => "Category name is too long (maximum 100 characters)."
    ]);
    exit();
}

try {
    // Check if category already exists for this store
    $checkStmt = $conn->prepare("SELECT id, name FROM categories WHERE store_id = :store_id AND LOWER(name) = LOWER(:name) LIMIT 1");
    $checkStmt->execute([
        'store_id' => $storeId,
        'name' => $name
    ]);
    $existing = $checkStmt->fetch(PDO::FETCH_ASSOC);

    if ($existing) {
        echo json_encode([
            "success" => false,
            "message" => "Category '{$existing['name']}' already exists in this store.",
            "category_id" => (int)$existing['id']
        ]);
        exit();
    }

    // Insert new category
    $insertStmt = $conn->prepare("INSERT INTO categories (name, store_id) VALUES (:name, :store_id) RETURNING id, name");
    $insertStmt->execute([
        'name' => $name,
        'store_id' => $storeId
    ]);
    $category = $insertStmt->fetch(PDO::FETCH_ASSOC);

    echo json_encode([
        "success" => true,
        "message" => "Category '{$category['name']}' created successfully.",
        "category" => [
            "id" => (int)$category['id'],
            "name" => $category['name'],
            "product_count" => 0
        ]
    ]);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode([
        "success" => false,
        "message" => "Database error creating category: " . $e->getMessage()
    ]);
}
?>
