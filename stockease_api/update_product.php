<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$data = json_decode(file_get_contents("php://input"), true);
if (!$data) {
    $data = $_POST;
}

$identity = auth_require_user($conn);
auth_require_roles($identity, ['store_admin', 'manager', 'super_admin', 'inventory_checker']);

$productId = isset($data['product_id']) ? (int)$data['product_id'] : 0;
$productName = trim($data['product_name'] ?? '');
$price = isset($data['price']) ? (float)$data['price'] : -1;
$costPrice = isset($data['cost_price']) ? (float)$data['cost_price'] : -1;
$stock = isset($data['stock']) ? (int)$data['stock'] : -1;
$categoryName = trim($data['category_name'] ?? ($data['category'] ?? ''));

if ($productId <= 0) {
    http_response_code(400);
    echo json_encode(["success" => false, "message" => "Valid product ID is required."]);
    exit;
}

try {
    // Fetch existing product
    $stmt = $conn->prepare("SELECT * FROM products WHERE id = :id");
    $stmt->execute(['id' => $productId]);
    $prod = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$prod) {
        http_response_code(404);
        echo json_encode(["success" => false, "message" => "Product not found."]);
        exit;
    }

    $storeId = (int)$prod['store_id'];

    // Resolve category if provided
    $categoryId = $prod['category_id'];
    if ($categoryName !== '') {
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
    } else {
        $categoryName = $prod['category'];
    }

    $finalName = $productName !== '' ? $productName : $prod['product_name'];
    $finalPrice = $price >= 0 ? $price : (float)$prod['price'];
    $finalCost = $costPrice >= 0 ? $costPrice : (float)$prod['cost_price'];
    $finalStock = $stock >= 0 ? $stock : (int)$prod['stock'];

    $upStmt = $conn->prepare("
        UPDATE products
        SET product_name = :pname,
            price = :price,
            cost_price = :cost,
            stock = :stock,
            category = :cat,
            category_id = :cat_id
        WHERE id = :id
        RETURNING id, product_name, price, cost_price, stock, category, category_id
    ");
    $upStmt->execute([
        'pname' => $finalName,
        'price' => $finalPrice,
        'cost' => $finalCost,
        'stock' => $finalStock,
        'cat' => $categoryName,
        'cat_id' => $categoryId,
        'id' => $productId
    ]);

    $updatedProduct = $upStmt->fetch(PDO::FETCH_ASSOC);

    echo json_encode([
        "success" => true,
        "message" => "Product updated successfully.",
        "product" => $updatedProduct
    ]);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(["success" => false, "message" => "Database error updating product: " . $e->getMessage()]);
}
