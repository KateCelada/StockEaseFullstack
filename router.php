<?php
/**
 * Router for PHP's built-in web server (Render.com deployment).
 *
 * Render has no Apache; the service is started with:
 *
 *     php -S 0.0.0.0:$PORT router.php
 *
 * The repository root becomes the document root so both halves of the
 * system share one domain (same-origin session cookies, and the admin
 * panel's relative ../stockease_api/ fetches keep working unchanged):
 *
 *     /                        -> stockease_admin/index.php   (web panel)
 *     /stockease_admin/...     -> panel assets + pages
 *     /stockease_api/...       -> PHP API endpoints (executed normally)
 */

$uri     = urldecode(parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH));
$root    = realpath(__DIR__);
$target  = realpath(__DIR__ . $uri);

// Block path traversal outside the repository
if ($target !== false && strpos($target, $root) !== 0) {
    $target = false;
}

// Panel entry point
if ($uri === '/' || $uri === '') {
    require __DIR__ . '/stockease_admin/index.php';
    return true;
}

// Directory requests (/stockease_admin/) resolve to their index.php
if ($target !== false && is_dir($target) && is_file($target . '/index.php')) {
    require $target . '/index.php';
    return true;
}

// Existing files (assets, .php endpoints) -> default handling
// (built-in server executes .php files and serves static ones)
if ($target !== false && is_file($target)) {
    return false;
}

http_response_code(404);
header('Content-Type: application/json');
echo json_encode(['success' => false, 'message' => 'Not found']);
