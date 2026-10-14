<?php
$hash = '$2y$10$udc6xwxIyJ883rLfcr.GBOQuto1BUBfy/u4BUTS/scAMQdyqrq54O';
foreach (['admin123', 'superadmin', 'superadmin123', 'admin', 'password', 'root'] as $p) {
    if (password_verify($p, $hash)) {
        echo "Password is: $p\n";
    }
}
