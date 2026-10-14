<?php
// Intentionally retired: no supported Android or PHP flow calls this endpoint.
header('Content-Type: application/json; charset=UTF-8');
http_response_code(410);
echo json_encode([
    'success' => false,
    'message' => 'Endpoint retired',
]);
exit;
