<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/subscription_helper.php';

$data = json_decode(file_get_contents("php://input"), true);

$requestedStoreId = isset($data['store_id']) ? (int)$data['store_id'] : 0;
$identity = auth_require_user($conn);
auth_require_roles($identity, ['store_admin', 'manager', 'staff']);
$storeId = auth_resolve_store_context($conn, $identity, $requestedStoreId, true);

// Enforce 15-product limit for free accounts
subscription_check_product_limit($conn, $storeId, $identity);

$categoryId = isset($data['category_id']) && (int)$data['category_id'] > 0
    ? (int)$data['category_id']
    : null;
$categoryName = trim($data['category_name'] ?? ($data['category'] ?? ''));
$productName = trim($data['product_name'] ?? '');
$price = isset($data['price']) ? (float)$data['price'] : -1;
$costPrice = isset($data['cost_price']) ? (float)$data['cost_price'] : 0.00;
$stock = isset($data['stock']) ? (int)$data['stock'] : -1;

if ($productName === '' || $price < 0 || $stock < 0 || $costPrice < 0) {
    echo json_encode([
        "success" => false,
        "message" => "A valid store, product name, nonnegative price, cost price, and stock are required"
    ]);
    exit;
}

try {
    // If category name is provided but category_id is missing/zero, resolve or insert into categories table
    if ($categoryId === null && $categoryName !== '') {
        $catFind = $conn->prepare("SELECT id FROM categories WHERE store_id = :sid AND LOWER(name) = LOWER(:name)");
        $catFind->execute(['sid' => $storeId, 'name' => $categoryName]);
        $existingCatId = $catFind->fetchColumn();
        if ($existingCatId) {
            $categoryId = (int)$existingCatId;
        } else {
            $insCat = $conn->prepare("INSERT INTO categories (store_id, name) VALUES (:sid, :name) RETURNING id");
            $insCat->execute(['sid' => $storeId, 'name' => $categoryName]);
            $categoryId = (int)$insCat->fetchColumn();
        }
    } elseif ($categoryId !== null && $categoryName === '') {
        $catNameStmt = $conn->prepare("SELECT name FROM categories WHERE id = :cid");
        $catNameStmt->execute(['cid' => $categoryId]);
        $categoryName = (string)$catNameStmt->fetchColumn();
    }

    if ($categoryId !== null) {
        $categoryStmt = $conn->prepare("SELECT id, name FROM categories WHERE id = :category_id AND store_id = :store_id");
        $categoryStmt->execute(['category_id' => $categoryId, 'store_id' => $storeId]);
        $catRow = $categoryStmt->fetch(PDO::FETCH_ASSOC);
        if (!$catRow) {
            echo json_encode(["success" => false, "message" => "Category does not belong to the selected store"]);
            exit;
        }
        $categoryName = $catRow['name'];
    }

    $stmt = $conn->prepare("INSERT INTO products
        (store_id, category_id, category, product_name, price, cost_price, stock)
        VALUES (:store_id, :category_id, :category, :product_name, :price, :cost_price, :stock)
        RETURNING id, store_id, product_name, category, category_id, price, cost_price, stock, created_at");
    $stmt->bindValue(':store_id', $storeId, PDO::PARAM_INT);
    $stmt->bindValue(':category_id', $categoryId, $categoryId === null ? PDO::PARAM_NULL : PDO::PARAM_INT);
    $stmt->bindValue(':category', $categoryName !== '' ? $categoryName : 'General', PDO::PARAM_STR);
    $stmt->bindValue(':product_name', $productName, PDO::PARAM_STR);
    $stmt->bindValue(':price', $price);
    $stmt->bindValue(':cost_price', $costPrice);
    $stmt->bindValue(':stock', $stock, PDO::PARAM_INT);
    $stmt->execute();

    $newProduct = $stmt->fetch(PDO::FETCH_ASSOC);

    echo json_encode([
        "success" => true,
        "message" => "Product added successfully",
        "category_id" => $categoryId,
        "category_name" => $categoryName,
        "product" => $newProduct
    ]);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Unable to add product: " . $e->getMessage()]);
}
?>
