<?php
require_once __DIR__ . '/../stockease_api/auth.php';
auth_start_session();

if (isset($_SESSION['user_id']) && isset($_SESSION['role']) && $_SESSION['role'] === 'super_admin') {
    header('Location: index.php');
    exit;
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>StockEase - Super Admin Portal</title>
  <link rel="stylesheet" href="style.css">
  <style>
    body.login-body {
      background-color: #0F172A;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      margin: 0;
      padding: 20px;
    }
    .login-card {
      background-color: #FFFFFF;
      width: 100%;
      max-width: 420px;
      border-radius: var(--radius-md);
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.2), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
      padding: 36px 32px;
    }
    .login-brand {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 24px;
    }
    .login-title {
      font-size: 20px;
      font-weight: 700;
      color: #0F172A;
      letter-spacing: -0.02em;
    }
    .login-subtitle {
      font-size: 13px;
      color: #64748B;
      margin-top: 4px;
      margin-bottom: 24px;
    }
    .alert-error {
      display: none;
      background-color: #FEF2F2;
      border: 1px solid #FECACA;
      color: #991B1B;
      padding: 10px 14px;
      border-radius: var(--radius-sm);
      font-size: 12px;
      margin-bottom: 18px;
      font-weight: 500;
    }
    .login-footer-text {
      margin-top: 24px;
      text-align: center;
      font-size: 11px;
      color: #94A3B8;
    }
  </style>
</head>
<body class="login-body">

<div class="login-card">
  <div class="login-brand">
    <div class="brand-badge">S</div>
    <div>
      <div class="brand-title" style="color: #0F172A;">StockEase</div>
      <div class="brand-subtitle">Executive Console</div>
    </div>
  </div>

  <div class="login-title">Administrator Sign In</div>
  <div class="login-subtitle">Access enterprise store operations, inventory transfers, and platform settings.</div>

  <div id="login-error" class="alert-error"></div>

  <form id="login-form">
    <div class="form-group">
      <label class="form-label" for="username">Username</label>
      <input type="text" id="username" class="form-control" placeholder="Enter super admin username" required autofocus autocomplete="username">
    </div>

    <div class="form-group" style="margin-bottom: 22px;">
      <label class="form-label" for="password">Password</label>
      <input type="password" id="password" class="form-control" placeholder="Enter password" required autocomplete="current-password">
    </div>

    <button type="submit" id="btn-submit" class="btn btn-primary" style="width: 100%; padding: 10px;">
      Sign In to Portal
    </button>
  </form>

  <div class="login-footer-text">
    StockEase Retail POS & ERP Platform &bull; Secure Super Admin Gateway
  </div>
</div>

<script>
  const form = document.getElementById('login-form');
  const errorBox = document.getElementById('login-error');
  const btnSubmit = document.getElementById('btn-submit');

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    errorBox.style.display = 'none';
    btnSubmit.disabled = true;
    btnSubmit.textContent = 'Verifying credentials...';

    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;

    try {
      const response = await fetch('../stockease_api/login.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ username, password })
      });

      const data = await response.json();

      if (data.success) {
        const userRole = (data.user && data.user.role) ? data.user.role : data.role;
        if (userRole !== 'super_admin') {
          // Gated: only super_admin can enter web portal
          errorBox.textContent = 'Access Restricted: This web console is reserved for Super Administrator accounts. Branch managers and staff must use the mobile application.';
          errorBox.style.display = 'block';
          btnSubmit.disabled = false;
          btnSubmit.textContent = 'Sign In to Portal';
          return;
        }

        // Redirect to portal home
        window.location.href = 'index.php';
      } else {
        errorBox.textContent = data.message || 'Invalid username or password.';
        errorBox.style.display = 'block';
        btnSubmit.disabled = false;
        btnSubmit.textContent = 'Sign In to Portal';
      }
    } catch (err) {
      errorBox.textContent = 'Unable to reach the authentication service. Check server connection.';
      errorBox.style.display = 'block';
      btnSubmit.disabled = false;
      btnSubmit.textContent = 'Sign In to Portal';
    }
  });
</script>

</body>
</html>
