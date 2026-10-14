<?php
require_once __DIR__ . '/../stockease_api/auth.php';
auth_clear_session();
header('Location: login.php');
exit;
