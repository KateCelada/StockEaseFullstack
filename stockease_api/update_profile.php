<?php
header("Access-Control-Allow-Origin: *");
header("Content-Type: application/json; charset=UTF-8");
header("Access-Control-Allow-Methods: POST");

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth.php';

$identity = auth_require_user($conn);

$rawBody = file_get_contents('php://input');
$jsonBody = json_decode($rawBody, true);
if (!is_array($jsonBody)) {
    $jsonBody = [];
}

$input = !empty($jsonBody) ? $jsonBody : $_POST;

$fullName = trim((string)($input['full_name'] ?? ''));
$email = trim((string)($input['email'] ?? ''));
$phone = trim((string)($input['phone'] ?? ''));
$address = trim((string)($input['address'] ?? ''));
$occupation = trim((string)($input['occupation'] ?? ''));
$sex = trim((string)($input['sex'] ?? ''));

if ($fullName === '') {
    echo json_encode(['success' => false, 'message' => 'Full name cannot be empty.']);
    exit;
}

if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
    echo json_encode(['success' => false, 'message' => 'Please enter a valid email address.']);
    exit;
}

if ($phone !== '' && !preg_match('/^[0-9()+\-\s]{7,25}$/', $phone)) {
    echo json_encode(['success' => false, 'message' => 'Please enter a valid phone number.']);
    exit;
}

$limits = [
    'full_name' => [mb_strlen($fullName), 120],
    'email' => [mb_strlen($email), 255],
    'phone' => [mb_strlen($phone), 30],
    'address' => [mb_strlen($address), 255],
    'occupation' => [mb_strlen($occupation), 100],
    'sex' => [mb_strlen($sex), 20],
];
foreach ($limits as $field => $limit) {
    if ($limit[0] > $limit[1]) {
        echo json_encode(['success' => false, 'message' => ucfirst(str_replace('_', ' ', $field)) . ' exceeds the maximum allowed length.']);
        exit;
    }
}

// Profile columns are optional until the migration has been run.
$colStmt = $conn->query(
    "SELECT column_name FROM information_schema.columns
     WHERE table_schema = current_schema() AND table_name = 'users'"
);
$existingColumns = array_map('strtolower', $colStmt->fetchAll(PDO::FETCH_COLUMN));
$missingColumns = array_diff(['phone', 'address', 'occupation', 'sex'], $existingColumns);
if (!empty($missingColumns)) {
    auth_json_error(500, 'Profile fields are not migrated yet. Run apply_profile_fields_migration.php on the server first.');
}

try {
    // Email uniqueness (excluding the current account)
    $dupeStmt = $conn->prepare(
        "SELECT id FROM users WHERE lower(email) = lower(:email) AND id <> :id LIMIT 1"
    );
    $dupeStmt->execute(['email' => $email, 'id' => $identity['user_id']]);
    if ($dupeStmt->fetchColumn()) {
        echo json_encode(['success' => false, 'message' => 'That email address is already in use by another account.']);
        exit;
    }

    $upStmt = $conn->prepare("
        UPDATE users
        SET full_name = :full_name,
            email = :email,
            phone = :phone,
            address = :address,
            occupation = :occupation,
            sex = :sex
        WHERE id = :id
    ");
    $upStmt->execute([
        'full_name' => $fullName,
        'email' => $email,
        'phone' => $phone,
        'address' => $address,
        'occupation' => $occupation,
        'sex' => $sex,
        'id' => $identity['user_id'],
    ]);

    // Return the updated user (same shape as get_user_profile.php)
    $stmt = $conn->prepare("
        SELECT u.id, u.username, u.email, u.full_name, u.role, u.store_id, u.is_active,
               u.phone, u.address, u.occupation, u.sex,
               s.store_name, s.location AS store_location
        FROM users u
        LEFT JOIN stores s ON u.store_id = s.id
        WHERE u.id = :user_id
        LIMIT 1
    ");
    $stmt->execute(['user_id' => $identity['user_id']]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    // Record in audit log
    $auditStmt = $conn->prepare("
        INSERT INTO audit_logs (user_name, role, action, target, store_name, details)
        VALUES (?, ?, ?, ?, ?, ?)
    ");
    $auditStmt->execute([
        $user['full_name'] ?? $identity['full_name'],
        $identity['role'],
        'PROFILE_UPDATED',
        "User #{$identity['user_id']} ({$identity['username']})",
        ($user['store_name'] ?? '') !== '' ? $user['store_name'] : 'System',
        'User updated their own profile details.'
    ]);

    echo json_encode([
        'success' => true,
        'message' => 'Profile updated successfully.',
        'user' => [
            'id' => (int)$user['id'],
            'username' => $user['username'],
            'email' => $user['email'] ?? '',
            'full_name' => $user['full_name'],
            'role' => $user['role'],
            'store_id' => $user['store_id'] !== null ? (int)$user['store_id'] : null,
            'store_name' => $user['store_name'] ?? '',
            'store_location' => $user['store_location'] ?? '',
            'is_active' => in_array($user['is_active'], [true, 1, '1', 't', 'true'], true),
            'phone' => (string)($user['phone'] ?? ''),
            'address' => (string)($user['address'] ?? ''),
            'occupation' => (string)($user['occupation'] ?? ''),
            'sex' => (string)($user['sex'] ?? '')
        ]
    ]);
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode(['success' => false, 'message' => 'Database error updating profile.']);
}
?>
