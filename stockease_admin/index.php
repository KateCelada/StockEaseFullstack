<?php
require_once __DIR__ . '/../stockease_api/auth.php';
require_once __DIR__ . '/../stockease_api/db.php';
require_once __DIR__ . '/../stockease_api/subscription_helper.php';

auth_start_session();

if (!isset($_SESSION['user_id']) || !isset($_SESSION['role']) || $_SESSION['role'] !== 'super_admin') {
    header('Location: login.php');
    exit;
}

$currentUserId = (int)$_SESSION['user_id'];
$currentFullName = htmlspecialchars($_SESSION['full_name'] ?? 'Super Admin', ENT_QUOTES, 'UTF-8');
$currentUsername = htmlspecialchars($_SESSION['username'] ?? 'admin', ENT_QUOTES, 'UTF-8');
$currentSubscription = subscription_get_current($conn);
$isPremium = !empty($currentSubscription['is_premium']);
$planName = htmlspecialchars($currentSubscription['display_plan'] ?? 'StockEase Free', ENT_QUOTES, 'UTF-8');
$planStatus = strtoupper($currentSubscription['status'] ?? 'ACTIVE');
?>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>StockEase - Super Admin Control Center</title>
  <link rel="stylesheet" href="style.css">
  <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
</head>
<body>

<div class="app-container">

  <!-- Left Sidebar Navigation -->
  <aside class="sidebar">
    <div class="sidebar-header">
      <div class="brand-badge">S</div>
      <div>
        <div class="brand-title">StockEase</div>
        <div class="brand-subtitle">Executive Portal</div>
      </div>
    </div>

    <nav class="sidebar-nav">
      <div class="nav-section-title">Core Operations</div>
      <a class="nav-item active" data-tab="dashboard">
        <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"/></svg>
        <span>Overview</span>
      </a>

      <div class="nav-section-title">Retail Stores &amp; Portfolios</div>
      <a class="nav-item" data-tab="stores">
        <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"/></svg>
        <span>Stores</span>
      </a>

      <div class="nav-section-title">Administration</div>
      <a class="nav-item" data-tab="users">
        <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"/></svg>
        <span>Users &amp; Roles</span>
      </a>

      <div class="nav-section-title">Analytics &amp; Intelligence</div>
      <a class="nav-item" data-tab="insights">
        <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"/></svg>
        <span>StockEase Insights</span>
      </a>

      <a class="nav-item" data-tab="reports">
        <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"/></svg>
        <span>Branch Reports</span>
      </a>

      <a class="nav-item" data-tab="audit">
        <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/></svg>
        <span>Security Audit Logs</span>
      </a>

      <div class="nav-section-title">System &amp; Billing</div>
      <a class="nav-item" data-tab="subscription">
        <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"/></svg>
        <span>Subscription &amp; Plans</span>
      </a>

      <a class="nav-item" data-tab="settings">
        <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/></svg>
        <span>System Settings</span>
      </a>
    </nav>

    <div class="sidebar-footer">
      <div class="user-snippet">
        <div class="avatar-circle">SA</div>
        <div class="user-info">
          <div class="user-name"><?= $currentFullName ?></div>
          <div class="user-role">Super Administrator</div>
        </div>
      </div>
      <a href="logout.php" class="btn-logout" title="Sign Out">
        <svg width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"/></svg>
      </a>
    </div>
  </aside>

  <!-- Main Content Workspace -->
  <main class="main-wrapper">

    <!-- Top Global Header -->
    <header class="top-header">
      <div class="header-left">
        <h1 id="current-page-title" class="page-title">Executive Overview</h1>
      </div>
      <div class="header-right">
        <div id="live-header-date" class="header-pill">Loading date...</div>
        <div id="sub-pending-notification" style="display: none; background: #FEF3C7; border: 1px solid #FCD34D; color: #92400E; padding: 4px 10px; border-radius: 4px; font-size: 11px; font-weight: 700; cursor: pointer;" onclick="app.showTab('subscription')">🔔 <span id="sub-pending-count">0</span> Pending Request(s)</div>
        <div id="live-plan-badge" class="header-pill plan-pill"><?= $planName ?> (<?= $planStatus ?>)</div>
        <button id="btn-global-refresh" class="btn btn-secondary btn-sm" title="Refresh Current View">
          <svg width="14" height="14" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/></svg>
          <span>Refresh</span>
        </button>
      </div>
    </header>

    <!-- Scrollable Content Sections -->
    <div class="content-area">

      <!-- ================= SECTION: OVERVIEW / DASHBOARD ================= -->
      <section id="section-dashboard" class="content-section active">
        <!-- Daily Anomaly / System Error Banner (Visible when any error or anomaly occurs) -->
        <div id="live-system-alert-banner" style="display: none; background: #FEF2F2; border: 1px solid #F87171; border-left: 5px solid #DC2626; padding: 14px 18px; border-radius: 8px; margin-bottom: 20px;">
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <div style="display: flex; align-items: center; gap: 10px;">
              <span style="font-size: 18px;">⚠️</span>
              <div>
                <strong id="live-alert-title" style="color: #991B1B; font-size: 13px;">Daily System Alerts Detected</strong>
                <div id="live-alert-message" style="color: #B91C1C; font-size: 12px; margin-top: 2px;">Attention required for store inventory or transaction discrepancies.</div>
              </div>
            </div>
            <button class="btn btn-secondary btn-sm" onclick="app.showTab('audit')" style="border-color: #FECACA; color: #991B1B;">Review Audit Logs</button>
          </div>
        </div>

        <!-- 4 Clickable KPI Stat Cards -->
        <div class="stats-grid">
          <div class="stat-card" style="cursor: pointer;" onclick="app.showTab('reports')" title="Click to view detailed sales reports">
            <div class="stat-header">
              <span class="stat-title">Today's Total Sales</span>
            </div>
            <div id="kpi-today-sales" class="stat-value">&mdash;</div>
            <div class="stat-trend">Click to open branch reports &rarr;</div>
          </div>

          <div class="stat-card" style="cursor: pointer;" onclick="app.showTab('reports')" title="Click to view orders and transactions">
            <div class="stat-header">
              <span class="stat-title">Transactions (Today)</span>
            </div>
            <div id="kpi-today-tx" class="stat-value">&mdash;</div>
            <div class="stat-trend" style="color: #64748B;">Click to view receipts &rarr;</div>
          </div>

          <div class="stat-card" style="cursor: pointer;" onclick="app.showTab('stores')" title="Click to manage physical branches">
            <div class="stat-header">
              <span class="stat-title">Active Store Branches</span>
            </div>
            <div id="kpi-total-stores" class="stat-value">&mdash;</div>
            <div class="stat-trend" style="color: #2563EB;">Click to manage stores &rarr;</div>
          </div>

          <div class="stat-card" style="cursor: pointer;" onclick="app.showTab('users')" title="Click to manage system users and roles">
            <div class="stat-header">
              <span class="stat-title">Total Users</span>
            </div>
            <div id="kpi-total-users" class="stat-value">&mdash;</div>
            <div class="stat-trend" style="color: #64748B;">Click to manage users &rarr;</div>
          </div>
        </div>

        <!-- Store Owners & Multi-Branch Business Cards -->
        <div class="card" style="margin-bottom: 24px;">
          <div class="card-header">
            <div>
              <div class="card-title">Store Owners &amp; Multi-Branch Portfolios</div>
              <div class="card-subtitle">Separate progress cards for each business owner/manager. Click any owner or branch to inspect real-time progress.</div>
            </div>
            <button class="btn btn-secondary btn-sm" onclick="app.loadStoreOwnersCards()">↻ Refresh Portfolios</button>
          </div>
          <div id="container-store-owners-cards" style="padding: 16px; display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 16px;">
            <div style="color: #64748B; font-size: 13px; padding: 16px; grid-column: 1 / -1; text-align: center;">Loading store owner portfolios...</div>
          </div>
        </div>

        <!-- Store Performance Overview Card -->
        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">Live Branch Performance</div>
              <div class="card-subtitle">Real-time daily transaction metrics and branch operations.</div>
            </div>
            <div style="display: flex; gap: 8px;">
              <button id="btn-open-compare" class="btn btn-secondary btn-sm">Compare Stores</button>
              <button class="btn btn-primary btn-sm" onclick="app.showTab('stores')">Manage Stores</button>
            </div>
          </div>

          <div class="table-responsive">
            <table id="table-store-performance">
              <thead>
                <tr>
                  <th>Store Branch</th>
                  <th>Location</th>
                  <th style="text-align: right;">Today's Sales</th>
                  <th style="text-align: right;">Orders</th>
                  <th style="text-align: right;">Assigned Staff</th>
                  <th style="text-align: center;">Status</th>
                  <th style="text-align: center;">Actions</th>
                </tr>
              </thead>
              <tbody id="tbody-store-performance">
                <tr><td colspan="7" style="text-align: center; color: #64748B; padding: 24px;">Loading live store metrics...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- ================= SECTION: STORE BRANCHES ================= -->
      <section id="section-stores" class="content-section">
        <div class="toolbar">
          <div>
            <h2 style="font-size: 16px; font-weight: 700;">Retail Store Branches</h2>
            <div style="color: #64748B; font-size: 12px;">Manage physical retail store locations and operational status.</div>
          </div>
          <button id="btn-modal-add-store" class="btn btn-primary btn-sm">+ Add New Store</button>
        </div>

        <div class="card">
          <div class="table-responsive">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Branch Name</th>
                  <th>Location</th>
                  <th>Business Owner</th>
                  <th>Branch Managers</th>
                  <th style="text-align: center;">Status</th>
                  <th style="text-align: right;">Actions</th>
                </tr>
              </thead>
              <tbody id="tbody-stores-list">
                <tr><td colspan="7" style="text-align: center; color: #64748B; padding: 24px;">Loading store branches...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- ================= SECTION: STOCK TRANSFERS ================= -->
      <section id="section-transfers" class="content-section">
        <div class="toolbar">
          <div>
            <h2 style="font-size: 16px; font-weight: 700;">Inter-Store Stock Movement Console</h2>
            <div style="color: #64748B; font-size: 12px;">Review requested transfers, dispatch inventory, and confirm delivery receipts.</div>
          </div>
          <div class="toolbar-group">
            <select id="filter-transfer-status" class="form-select" style="width: auto; padding: 5px 10px; font-size: 12px;">
              <option value="">All Statuses</option>
              <option value="requested">Requested</option>
              <option value="in_transit">In Transit</option>
              <option value="completed">Completed</option>
              <option value="cancelled">Cancelled</option>
            </select>
            <button id="btn-modal-quick-transfer" class="btn btn-secondary btn-sm" style="border-color: #38BDF8; color: #0284C7; font-weight: 600;">⚡ Quick Direct Move</button>
            <button id="btn-modal-new-transfer" class="btn btn-primary btn-sm">+ Create Transfer</button>
          </div>
        </div>

        <div id="premium-transfer-banner" style="display: none; background-color: #FEF3C7; border: 1px solid #FCD34D; padding: 14px 18px; border-radius: 8px; margin-bottom: 20px;">
          <strong style="color: #92400E;">StockEase Premium Feature:</strong>
          <span style="color: #78350F; margin-left: 6px;">Multi-store inventory transfers require an active Premium plan.</span>
          <button class="btn btn-primary btn-sm" style="margin-left: 14px;" onclick="app.showTab('subscription')">Upgrade to Premium</button>
        </div>

        <div class="card">
          <div class="table-responsive">
            <table>
              <thead>
                <tr>
                  <th>Transfer #</th>
                  <th>Routing</th>
                  <th>Items &amp; Quantity</th>
                  <th>Requested By</th>
                  <th>Date Requested</th>
                  <th style="text-align: center;">Status</th>
                  <th style="text-align: right;">Execution Controls</th>
                </tr>
              </thead>
              <tbody id="tbody-transfers-list">
                <tr><td colspan="7" style="text-align: center; color: #64748B; padding: 24px;">Loading transfers...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- ================= SECTION: USERS & ROLES ================= -->
      <section id="section-users" class="content-section">
        <div class="toolbar">
          <div>
            <h2 style="font-size: 16px; font-weight: 700;">User Accounts &amp; Permissions</h2>
            <div style="color: #64748B; font-size: 12px;">Platform user management, role assignments, and store scope.</div>
          </div>
          <div class="toolbar-group">
            <input type="text" id="input-search-users" class="form-control" placeholder="Search name or username..." style="width: 220px; font-size: 12px; padding: 5px 10px;">
            <select id="filter-user-role" class="form-select" style="width: auto; padding: 5px 10px; font-size: 12px;">
              <option value="">All Roles</option>
              <option value="super_admin">Super Admin</option>
              <option value="store_admin">Store Admin</option>
              <option value="staff">Staff / Cashier</option>
            </select>
          </div>
        </div>

        <div class="card">
          <div class="table-responsive">
            <table>
              <thead>
                <tr>
                  <th>User ID</th>
                  <th>Full Name</th>
                  <th>Username</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Assigned Store</th>
                  <th style="text-align: center;">Account Status</th>
                  <th style="text-align: right;">Action</th>
                </tr>
              </thead>
              <tbody id="tbody-users-list">
                <tr><td colspan="8" style="text-align: center; color: #64748B; padding: 24px;">Loading users...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- ================= SECTION: STORE ADMINS ================= -->
      <section id="section-admins" class="content-section">
        <div class="toolbar">
          <div>
            <h2 style="font-size: 16px; font-weight: 700;">Branch Store Administrators</h2>
            <div style="color: #64748B; font-size: 12px;">Store managers with branch authority over products, staff, and daily registers.</div>
          </div>
          <button id="btn-modal-add-admin" class="btn btn-primary btn-sm">+ Assign Store Admin</button>
        </div>

        <div class="card">
          <div class="table-responsive">
            <table>
              <thead>
                <tr>
                  <th>Admin ID</th>
                  <th>Full Name</th>
                  <th>Username</th>
                  <th>Email</th>
                  <th>Assigned Store</th>
                  <th style="text-align: center;">Status</th>
                </tr>
              </thead>
              <tbody id="tbody-store-admins-list">
                <tr><td colspan="6" style="text-align: center; color: #64748B; padding: 24px;">Loading store admins...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- ================= SECTION: STOCKEASE INSIGHTS ================= -->
      <section id="section-insights" class="content-section">
        <div class="toolbar">
          <div>
            <h2 style="font-size: 16px; font-weight: 700;">Smart Inventory Insights &amp; Velocity</h2>
            <div style="color: #64748B; font-size: 12px;">Deterministic sales velocity, stockout risks, and cross-store rebalancing recommendations.</div>
          </div>
          <div class="toolbar-group">
            <button class="btn btn-secondary btn-sm btn-period active" data-days="7">7 Days</button>
            <button class="btn btn-secondary btn-sm btn-period" data-days="30">30 Days</button>
            <button class="btn btn-secondary btn-sm btn-period" data-days="90">Quarter</button>
          </div>
        </div>

        <div id="premium-insights-banner" style="display: none; background-color: #FEF3C7; border: 1px solid #FCD34D; padding: 14px 18px; border-radius: 8px; margin-bottom: 20px;">
          <strong style="color: #92400E;">StockEase Premium Feature:</strong>
          <span style="color: #78350F; margin-left: 6px;">Smart Inventory Velocity and Transfer Recommendations require an active Premium subscription.</span>
          <button class="btn btn-primary btn-sm" style="margin-left: 14px;" onclick="app.showTab('subscription')">Upgrade to Premium</button>
        </div>

        <!-- Period Overview Metrics -->
        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Period Revenue</span></div>
            <div id="insight-period-sales" class="stat-value">&mdash;</div>
          </div>
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Orders Processed</span></div>
            <div id="insight-period-orders" class="stat-value">&mdash;</div>
          </div>
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Units Sold</span></div>
            <div id="insight-period-units" class="stat-value">&mdash;</div>
          </div>
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Avg Order Value</span></div>
            <div id="insight-period-avg" class="stat-value">&mdash;</div>
          </div>
        </div>

        <!-- Inter-store Transfer Recommendations Card -->
        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">Stock Balancing &amp; Transfer Recommendations</div>
              <div class="card-subtitle">Automated balancing recommendations to transfer surplus inventory to branches facing stockout.</div>
            </div>
          </div>
          <div id="container-transfer-recommendations">
            <div style="color: #64748B; padding: 12px 0;">Analyzing cross-store inventory levels...</div>
          </div>
        </div>

        <!-- 2 Column Insights Grid: Stockout Risks & Slow-Moving Inventory -->
        <div class="insights-row">
          <!-- Stockout Risks -->
          <div class="card">
            <div class="card-header">
              <div>
                <div class="card-title" style="color: #DC2626;">Stockout Risk (Under 7 Days Coverage)</div>
                <div class="card-subtitle">Products selling fast relative to on-hand inventory.</div>
              </div>
            </div>
            <div id="container-stockout-risks">
              <div style="color: #64748B; padding: 12px 0;">Evaluating stock coverage...</div>
            </div>
          </div>

          <!-- Slow Moving -->
          <div class="card">
            <div class="card-header">
              <div>
                <div class="card-title">Slow-Moving &amp; Dormant Inventory</div>
                <div class="card-subtitle">Items tying up capital with over 60 days of stock.</div>
              </div>
            </div>
            <div id="container-slow-moving">
              <div style="color: #64748B; padding: 12px 0;">Evaluating slow-moving stock...</div>
            </div>
          </div>
        </div>

        <!-- Multi-store Performance Comparison -->
        <div class="card">
          <div class="card-header">
            <div class="card-title">Branch Velocity Comparison Table</div>
          </div>
          <div class="table-responsive">
            <table>
              <thead>
                <tr>
                  <th>Store</th>
                  <th>Location</th>
                  <th style="text-align: right;">Period Sales</th>
                  <th style="text-align: right;">Transactions</th>
                  <th style="text-align: right;">Units Sold</th>
                  <th style="text-align: right;">Total On-Hand Stock</th>
                </tr>
              </thead>
              <tbody id="tbody-insight-store-comparison">
                <tr><td colspan="6" style="text-align: center; color: #64748B; padding: 20px;">Loading velocity comparison...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- ================= SECTION: BRANCH REPORTS ================= -->
      <section id="section-reports" class="content-section">
        <div class="toolbar">
          <div class="toolbar-group">
            <label class="form-label" style="margin: 0; margin-right: 6px;">Select Branch:</label>
            <select id="reports-store-select" class="form-select" style="width: 220px; font-weight: 600;">
              <option value="">Loading branches...</option>
            </select>
          </div>
          <div class="toolbar-group">
            <button class="btn btn-secondary btn-sm btn-report-period active" data-period="today">Today</button>
            <button class="btn btn-secondary btn-sm btn-report-period" data-period="week">This Week</button>
            <button class="btn btn-secondary btn-sm btn-report-period" data-period="month">This Month</button>
            <button id="btn-export-reports-csv" class="btn btn-primary btn-sm">Export to CSV</button>
          </div>
        </div>

        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Branch Revenue</span></div>
            <div id="rep-total-sales" class="stat-value">&mdash;</div>
          </div>
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Orders</span></div>
            <div id="rep-total-tx" class="stat-value">&mdash;</div>
          </div>
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Average Order</span></div>
            <div id="rep-avg-order" class="stat-value">&mdash;</div>
          </div>
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Units Sold</span></div>
            <div id="rep-units-sold" class="stat-value">&mdash;</div>
          </div>
        </div>

        <!-- Line Chart: Store Earnings Progress & Top Products Visualizer -->
        <div class="insights-row" style="margin-bottom: 20px;">
          <div class="card" style="flex: 1.4;">
            <div class="card-header">
              <div>
                <div class="card-title">📈 Branch Earnings Progress (Revenue Trend)</div>
                <div class="card-subtitle">Real-time daily earnings trajectory across operational dates</div>
              </div>
            </div>
            <div style="height: 240px; position: relative;">
              <canvas id="chart-branch-earnings"></canvas>
            </div>
          </div>

          <div class="card" style="flex: 1;">
            <div class="card-header">
              <div>
                <div class="card-title">🏆 Most Purchased Products</div>
                <div class="card-subtitle">Units sold for leading inventory catalog items</div>
              </div>
            </div>
            <div style="height: 240px; position: relative;">
              <canvas id="chart-top-products"></canvas>
            </div>
          </div>
        </div>

        <div class="insights-row">
          <!-- Payment Methods Breakdown -->
          <div class="card">
            <div class="card-header"><div class="card-title">Payment Methods Breakdown</div></div>
            <div id="container-payment-methods">
              <div style="color: #64748B;">Select a store to view payment distribution.</div>
            </div>
          </div>

          <!-- Top Selling Products List -->
          <div class="card">
            <div class="card-header"><div class="card-title">Top Selling Catalog Items Breakdown</div></div>
            <div id="container-top-products">
              <div style="color: #64748B;">Select a store to view top items.</div>
            </div>
          </div>
        </div>
      </section>

      <!-- ================= SECTION: SECURITY AUDIT LOGS ================= -->
      <section id="section-audit" class="content-section">
        <div class="toolbar">
          <div>
            <h2 style="font-size: 16px; font-weight: 700;">System Security &amp; Operational Audit Trail</h2>
            <div style="color: #64748B; font-size: 12px;">Immutable record of authentication, inventory movements, and system configuration updates.</div>
          </div>
          <div class="toolbar-group">
            <input type="text" id="input-audit-search" class="form-control" placeholder="Search logs..." style="width: 200px; font-size: 12px; padding: 5px 10px;">
            <select id="filter-audit-action" class="form-select" style="width: auto; padding: 5px 10px; font-size: 12px;">
              <option value="">All Actions</option>
              <option value="STOCK_TRANSFER_REQUESTED">Transfer Requested</option>
              <option value="STOCK_TRANSFER_DISPATCHED">Transfer Dispatched</option>
              <option value="STOCK_TRANSFER_COMPLETED">Transfer Completed</option>
              <option value="STOCK_TRANSFER_CANCELLED">Transfer Cancelled</option>
              <option value="SUBSCRIPTION_UPDATED">Subscription Updated</option>
              <option value="STORE_CREATED">Store Created</option>
              <option value="SYSTEM_LOGIN">System Login</option>
            </select>
            <button id="btn-audit-refresh" class="btn btn-secondary btn-sm">Refresh</button>
          </div>
        </div>

        <div class="card">
          <div class="table-responsive">
            <table>
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>User</th>
                  <th>Role</th>
                  <th>Action</th>
                  <th>Target</th>
                  <th>Store</th>
                  <th>Details</th>
                </tr>
              </thead>
              <tbody id="tbody-audit-logs">
                <tr><td colspan="7" style="text-align: center; color: #64748B; padding: 24px;">Loading audit trail...</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <!-- ================= SECTION: SUBSCRIPTION & BILLING ================= -->
      <section id="section-subscription" class="content-section">
        <div class="toolbar">
          <div>
            <h2 style="font-size: 16px; font-weight: 700;">Subscription &amp; Freemium Plan Center</h2>
            <div style="color: #64748B; font-size: 12px;">Manage platform subscription tier, review store applications, and oversee licensing.</div>
          </div>
        </div>

        <!-- Card: Pending Store Admin Subscription Applications -->
        <div class="card" id="card-pending-subscriptions" style="border-left: 4px solid #F59E0B; margin-bottom: 24px;">
          <div class="card-header">
            <div>
              <div class="card-title" style="color: #92400E; display: flex; align-items: center; gap: 8px;">
                <span>Pending Store Admin Subscription Applications</span>
                <span id="badge-pending-sub-count" class="badge badge-warning" style="font-size: 11px;">0 Pending</span>
              </div>
              <div class="card-subtitle">Review store manager applications. Premium features unlock immediately upon your approval for the requested timeframe.</div>
            </div>
          </div>
          <div class="table-responsive">
            <table>
              <thead>
                <tr>
                  <th>Request #</th>
                  <th>Store Admin</th>
                  <th>Branch Store</th>
                  <th>Requested Plan</th>
                  <th>Rate</th>
                  <th>Date Submitted</th>
                  <th style="text-align: right;">Approval Actions</th>
                </tr>
              </thead>
              <tbody id="tbody-pending-subscriptions">
                <tr><td colspan="7" style="text-align: center; color: #64748B; padding: 20px;">No pending subscription applications.</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Active Plan Banner -->
        <div class="card" style="border-left: 4px solid var(--color-primary);">
          <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: gap;">
            <div>
              <span class="badge badge-info" id="sub-active-badge" style="font-size: 12px; padding: 4px 10px;"><?= $planName ?></span>
              <h3 id="sub-active-title" style="font-size: 18px; margin-top: 8px; font-weight: 700;"><?= $planName ?> &bull; <?= $planStatus ?></h3>
              <p id="sub-active-desc" style="color: #64748B; font-size: 13px; margin-top: 4px;">Active platform subscription powering your retail stores.</p>
            </div>
            <div id="sub-expires-display" style="text-align: right; color: #475569; font-size: 12px;">
              Valid billing status
            </div>
          </div>
        </div>

        <!-- Comparative Plan Grid -->
        <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 20px; margin-bottom: 24px;">
          <!-- Card: Free -->
          <div class="card" style="display: flex; flex-direction: column;">
            <div style="font-size: 15px; font-weight: 700; color: #0F172A;">StockEase Free</div>
            <div style="font-size: 26px; font-weight: 800; color: #0F172A; margin: 12px 0;">&#8369;0 <span style="font-size: 13px; font-weight: 500; color: #64748B;">/ forever</span></div>
            <p style="color: #64748B; font-size: 12px; margin-bottom: 16px;">Core POS checkout and single-store inventory management.</p>
            <ul style="list-style: none; margin-bottom: 20px; flex: 1;">
              <li style="padding: 6px 0; color: #334155;">&check; Core POS Checkout &amp; Receipts</li>
              <li style="padding: 6px 0; color: #334155;">&check; Local Catalog Management</li>
              <li style="padding: 6px 0; color: #334155;">&check; Basic Low Stock Alerts</li>
              <li style="padding: 6px 0; color: #334155;">&check; Store Staff Accounts</li>
              <li style="padding: 6px 0; color: #94A3B8;">&cross; Cross-Store Stock Transfers</li>
              <li style="padding: 6px 0; color: #94A3B8;">&cross; Velocity &amp; Insights Analytics</li>
            </ul>
            <div style="text-align: center; padding: 8px; background: #F1F5F9; color: #475569; font-weight: 600; font-size: 13px; border-radius: 6px;">Included Base Tier</div>
          </div>

          <!-- Card: Monthly -->
          <div class="card" style="display: flex; flex-direction: column;">
            <div style="font-size: 15px; font-weight: 700; color: #0F172A;">StockEase Premium (Monthly)</div>
            <div style="font-size: 26px; font-weight: 800; color: #0F172A; margin: 12px 0;">&#8369;699 <span style="font-size: 13px; font-weight: 500; color: #64748B;">/ month</span></div>
            <p style="color: #64748B; font-size: 12px; margin-bottom: 16px;">Full multi-store intelligence with flexible month-to-month billing.</p>
            <ul style="list-style: none; margin-bottom: 20px; flex: 1;">
              <li style="padding: 6px 0; color: #334155;">&check; Everything in Free Plan</li>
              <li style="padding: 6px 0; color: #334155; font-weight: 600;">&check; Multi-Store Stock Transfers</li>
              <li style="padding: 6px 0; color: #334155; font-weight: 600;">&check; Smart Inventory Velocity Analytics</li>
              <li style="padding: 6px 0; color: #334155; font-weight: 600;">&check; Automated Transfer Balancing</li>
              <li style="padding: 6px 0; color: #334155; font-weight: 600;">&check; Stockout Risk Detection</li>
              <li style="padding: 6px 0; color: #334155; font-weight: 600;">&check; Multi-Store Comparison Analytics</li>
            </ul>
            <div style="text-align: center; padding: 8px; background: #EFF6FF; color: #2563EB; font-weight: 600; font-size: 13px; border-radius: 6px;">Store Admin Application Tier</div>
          </div>

          <!-- Card: Yearly (Best Value) -->
          <div class="card" style="display: flex; flex-direction: column; border: 2px solid #2563EB;">
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <div style="font-size: 15px; font-weight: 700; color: #0F172A;">Premium (Yearly)</div>
              <span class="badge badge-success" style="font-size: 11px;">Best Value</span>
            </div>
            <div style="font-size: 26px; font-weight: 800; color: #0F172A; margin: 12px 0;">&#8369;6,990 <span style="font-size: 13px; font-weight: 500; color: #64748B;">/ year</span></div>
            <p style="color: #059669; font-size: 12px; font-weight: 600; margin-bottom: 16px;">Save &#8369;1,398 compared with monthly billing!</p>
            <ul style="list-style: none; margin-bottom: 20px; flex: 1;">
              <li style="padding: 6px 0; color: #334155;">&check; Complete Premium Capabilities</li>
              <li style="padding: 6px 0; color: #334155;">&check; Priority Multi-Store Balancing</li>
              <li style="padding: 6px 0; color: #334155;">&check; 365 Days Uninterrupted Access</li>
              <li style="padding: 6px 0; color: #334155;">&check; Annual Savings Included</li>
            </ul>
            <div style="text-align: center; padding: 8px; background: #ECFDF5; color: #059669; font-weight: 600; font-size: 13px; border-radius: 6px;">Store Admin Application Tier (Annual)</div>
          </div>
        </div>
      </section>

      <!-- ================= SECTION: SYSTEM SETTINGS ================= -->
      <section id="section-settings" class="content-section">
        <div class="toolbar">
          <div>
            <h2 style="font-size: 16px; font-weight: 700;">System Settings &amp; Platform Architecture</h2>
            <div style="color: #64748B; font-size: 12px;">Technical environment parameters, server identity, and database status.</div>
          </div>
        </div>

        <div class="card">
          <div class="card-header"><div class="card-title">Environment &amp; Infrastructure</div></div>
          <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px;">
            <div>
              <span style="color: #64748B; font-size: 12px;">Database Engine</span>
              <div style="font-weight: 600; font-size: 14px; margin-top: 2px;">PostgreSQL (stockease_manual)</div>
            </div>
            <div>
              <span style="color: #64748B; font-size: 12px;">Web Application Server</span>
              <div style="font-weight: 600; font-size: 14px; margin-top: 2px;">Apache / XAMPP (Port 80)</div>
            </div>
            <div>
              <span style="color: #64748B; font-size: 12px;">PHP Runtime</span>
              <div style="font-weight: 600; font-size: 14px; margin-top: 2px;">PHP <?= phpversion() ?></div>
            </div>
            <div>
              <span style="color: #64748B; font-size: 12px;">Authenticated Super Admin</span>
              <div style="font-weight: 600; font-size: 14px; margin-top: 2px;"><?= $currentFullName ?> (ID: <?= $currentUserId ?>)</div>
            </div>
          </div>
        </div>
      </section>

    </div>
  </main>
</div>

<!-- ================= MODALS ================= -->

<!-- Modal: Add Store -->
<div id="modal-add-store" class="modal-overlay">
  <div class="modal-content" style="max-width: 600px;">
    <div class="modal-header">
      <div>
        <div class="modal-title">Create Retail Store &amp; Assign Leadership</div>
        <div style="font-size: 12px; color: #64748B;">Add store name, location, store owner, and store manager.</div>
      </div>
      <button class="modal-close" onclick="app.closeModals()">&times;</button>
    </div>
    <form id="form-add-store">
      <div class="modal-body">
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 14px;">
          <div class="form-group">
            <label class="form-label" for="new-store-name">Store Name *</label>
            <input type="text" id="new-store-name" class="form-control" placeholder="e.g. Apex Mart - Downtown" required>
          </div>
          <div class="form-group">
            <label class="form-label" for="new-store-location">Physical Location *</label>
            <input type="text" id="new-store-location" class="form-control" placeholder="e.g. Colon St, Cebu City" required>
          </div>
        </div>

        <div class="form-group">
          <label class="form-label" for="new-store-description">Store Description (Optional)</label>
          <textarea id="new-store-description" class="form-control" rows="2" placeholder="e.g. Prime flagship store. Edit anytime via the Store Description button."></textarea>
        </div>

        <!-- Store Owner Section -->
        <div style="background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 8px; padding: 12px; margin-bottom: 12px;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
            <label class="form-label" style="margin: 0; font-weight: 700; color: #0F172A;">Store Owner *</label>
            <span style="font-size: 11px; color: #2563EB;">Multi-branch portfolio owner</span>
          </div>
          <select id="new-store-owner" class="form-select" onchange="app.toggleNewOwnerFields()">
            <option value="">-- Select Existing Store Owner --</option>
            <option value="__new__">+ Create New Store Owner Account</option>
          </select>

          <!-- Collapsible New Owner Fields -->
          <div id="new-owner-fields" style="display: none; margin-top: 10px; padding-top: 10px; border-top: 1px dashed #CBD5E1;">
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 8px;">
              <div>
                <label class="form-label" style="font-size: 11px;">Owner Full Name</label>
                <input type="text" id="new-owner-fullname" class="form-control" placeholder="e.g. Juan dela Cruz">
              </div>
              <div>
                <label class="form-label" style="font-size: 11px;">Owner Username</label>
                <input type="text" id="new-owner-username" class="form-control" placeholder="e.g. jdelacruz">
              </div>
            </div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
              <div>
                <label class="form-label" style="font-size: 11px;">Owner Email</label>
                <input type="email" id="new-owner-email" class="form-control" placeholder="e.g. juan@stockease.com">
              </div>
              <div>
                <label class="form-label" style="font-size: 11px;">Initial Password</label>
                <input type="password" id="new-owner-password" class="form-control" placeholder="e.g. admin123" value="admin123">
              </div>
            </div>
          </div>
        </div>

        <!-- Store Manager Section -->
        <div style="background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 8px; padding: 12px;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
            <label class="form-label" style="margin: 0; font-weight: 700; color: #0F172A;">Store Manager (Optional)</label>
            <span style="font-size: 11px; color: #059669;">Branch operations manager</span>
          </div>
          <select id="new-store-manager" class="form-select" onchange="app.toggleNewManagerFields()">
            <option value="">-- No Separate Manager (Same as Owner) --</option>
            <option value="__new__">+ Create New Store Manager Account</option>
          </select>

          <!-- Collapsible New Manager Fields -->
          <div id="new-manager-fields" style="display: none; margin-top: 10px; padding-top: 10px; border-top: 1px dashed #CBD5E1;">
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 8px;">
              <div>
                <label class="form-label" style="font-size: 11px;">Manager Full Name</label>
                <input type="text" id="new-manager-fullname" class="form-control" placeholder="e.g. Maria Santos">
              </div>
              <div>
                <label class="form-label" style="font-size: 11px;">Manager Username</label>
                <input type="text" id="new-manager-username" class="form-control" placeholder="e.g. msantos">
              </div>
            </div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
              <div>
                <label class="form-label" style="font-size: 11px;">Manager Email</label>
                <input type="email" id="new-manager-email" class="form-control" placeholder="e.g. maria@stockease.com">
              </div>
              <div>
                <label class="form-label" style="font-size: 11px;">Initial Password</label>
                <input type="password" id="new-manager-password" class="form-control" placeholder="e.g. admin123" value="admin123">
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" onclick="app.closeModals()">Cancel</button>
        <button type="submit" class="btn btn-primary">Create Store &amp; Setup Accounts</button>
      </div>
    </form>
  </div>
</div>

<!-- Modal: Edit Store Description -->
<div id="modal-edit-description" class="modal-overlay">
  <div class="modal-content">
    <div class="modal-header">
      <div class="modal-title">Store Description &amp; Profile</div>
      <button class="modal-close" onclick="app.closeModals()">&times;</button>
    </div>
    <form id="form-edit-description">
      <input type="hidden" id="desc-store-id">
      <div class="modal-body">
        <div class="form-group">
          <label class="form-label" id="desc-store-name-label">Store Description</label>
          <textarea id="desc-store-text" class="form-control" rows="4" placeholder="Enter store description, highlights, business model, operating hours, etc."></textarea>
          <div style="font-size: 11px; color: #64748B; margin-top: 4px;">Super admin and managers can view and edit this description anytime.</div>
        </div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" onclick="app.closeModals()">Cancel</button>
        <button type="submit" class="btn btn-primary">Save Description</button>
      </div>
    </form>
  </div>
</div>

<!-- Modal: User Account Details -->
<div id="modal-user-detail" class="modal-overlay">
  <div class="modal-content">
    <div class="modal-header">
      <div class="modal-title">User Account Details</div>
      <button class="modal-close" onclick="app.closeModals()">&times;</button>
    </div>
    <div class="modal-body">
      <div style="display: flex; align-items: center; gap: 14px; margin-bottom: 16px;">
        <div id="detail-user-avatar" style="width: 48px; height: 48px; border-radius: 50%; background: #2563EB; color: #FFF; display: flex; align-items: center; justify-content: center; font-size: 18px; font-weight: 700;">U</div>
        <div>
          <h3 id="detail-user-fullname" style="margin: 0; font-size: 16px; color: #0F172A;">Full Name</h3>
          <div id="detail-user-username" style="color: #64748B; font-size: 13px;">@username</div>
        </div>
      </div>
      <div style="background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 8px; padding: 12px; font-size: 13px; display: grid; gap: 8px;">
        <div><strong>Role:</strong> <span id="detail-user-role" class="badge badge-primary">Role</span></div>
        <div><strong>Email:</strong> <span id="detail-user-email">email@example.com</span></div>
        <div><strong>Account Status:</strong> <span id="detail-user-status" class="badge badge-success">Active</span></div>
        <div><strong>Cashier Duty / Last Transaction:</strong> <span id="detail-user-duty">None</span></div>
      </div>
    </div>
    <div class="modal-footer">
      <button type="button" class="btn btn-secondary" onclick="app.closeModals()">Close</button>
    </div>
  </div>
</div>

<!-- Modal: Assign Store Admin -->
<div id="modal-add-admin" class="modal-overlay">
  <div class="modal-content">
    <div class="modal-header">
      <div class="modal-title">Register Store Administrator</div>
      <button class="modal-close" onclick="app.closeModals()">&times;</button>
    </div>
    <form id="form-add-admin">
      <div class="modal-body">
        <div class="form-group">
          <label class="form-label" for="admin-fullname">Full Name</label>
          <input type="text" id="admin-fullname" class="form-control" placeholder="e.g. Juan dela Cruz" required>
        </div>
        <div class="form-group">
          <label class="form-label" for="admin-username">Username</label>
          <input type="text" id="admin-username" class="form-control" placeholder="e.g. jdelacruz" required>
        </div>
        <div class="form-group">
          <label class="form-label" for="admin-email">Email Address</label>
          <input type="email" id="admin-email" class="form-control" placeholder="e.g. jdelacruz@stockease.com" required>
        </div>
        <div class="form-group">
          <label class="form-label" for="admin-password">Initial Password</label>
          <input type="password" id="admin-password" class="form-control" placeholder="Enter secure password" required minlength="4">
        </div>
        <div class="form-group">
          <label class="form-label" for="admin-role-select">Account Role</label>
          <select id="admin-role-select" class="form-select">
            <option value="store_admin">Store Admin (Branch Administrator)</option>
            <option value="manager">Manager (Multi-Branch Owner)</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label" for="admin-store-select">Assign to Primary Branch</label>
          <select id="admin-store-select" class="form-select" required>
            <option value="">Select a store branch...</option>
          </select>
        </div>
        <div style="background: #EFF6FF; border: 1px solid #BFDBFE; border-radius: 8px; padding: 10px 12px; font-size: 12px; color: #1E40AF; margin-top: 10px;">
          ✉️ <strong>Direct Email Dispatch &amp; First-Login Reset:</strong> Credentials will be emailed directly to the provided address. The user will be required to choose a new password on their first login.
        </div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" onclick="app.closeModals()">Cancel</button>
        <button type="submit" class="btn btn-primary">Create Account &amp; Email Credentials</button>
      </div>
    </form>
  </div>
</div>

<!-- Modal: Update User Role & Store Assignment -->
<div id="modal-update-role" class="modal-overlay">
  <div class="modal-content">
    <div class="modal-header">
      <div class="modal-title">Update User Role &amp; Store Scope</div>
      <button class="modal-close" onclick="app.closeModals()">&times;</button>
    </div>
    <form id="form-update-role">
      <input type="hidden" id="edit-user-id">
      <div class="modal-body">
        <div class="form-group">
          <label class="form-label">User</label>
          <div id="edit-user-display" style="font-weight: 600; font-size: 14px; padding: 6px 0;"></div>
        </div>
        <div class="form-group">
          <label class="form-label" for="edit-user-role">System Role</label>
          <select id="edit-user-role" class="form-select" required>
            <option value="super_admin">Super Administrator (Platform Owner)</option>
            <option value="store_admin">Store Administrator (Branch Manager)</option>
            <option value="staff">Staff (Cashier / POS Terminal)</option>
          </select>
        </div>
        <div class="form-group" id="group-edit-store">
          <label class="form-label" for="edit-user-store">Assigned Store Branch</label>
          <select id="edit-user-store" class="form-select">
            <option value="">None / Not Assigned</option>
          </select>
          <div style="font-size: 11px; color: #64748B; margin-top: 4px;">Store Admins and Staff must be assigned to an active branch.</div>
        </div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" onclick="app.closeModals()">Cancel</button>
        <button type="submit" class="btn btn-primary">Save Changes</button>
      </div>
    </form>
  </div>
</div>

<!-- Modal: New Stock Transfer -->
<div id="modal-new-transfer" class="modal-overlay">
  <div class="modal-content">
    <div class="modal-header">
      <div class="modal-title">Initiate Inter-Store Stock Transfer</div>
      <button class="modal-close" onclick="app.closeModals()">&times;</button>
    </div>
    <form id="form-new-transfer">
      <div class="modal-body">
        <div class="form-group">
          <label class="form-label" for="transfer-from-store">Source Branch (Donor)</label>
          <select id="transfer-from-store" class="form-select" required>
            <option value="">Select source branch...</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label" for="transfer-to-store">Destination Branch (Recipient)</label>
          <select id="transfer-to-store" class="form-select" required>
            <option value="">Select destination branch...</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label" for="transfer-product-select">Product Item</label>
          <select id="transfer-product-select" class="form-select" required disabled>
            <option value="">Select source store first...</option>
          </select>
          <div id="transfer-stock-hint" style="font-size: 12px; color: #2563EB; margin-top: 4px; font-weight: 500;"></div>
        </div>
        <div class="form-group">
          <label class="form-label" for="transfer-quantity">Transfer Quantity</label>
          <input type="number" id="transfer-quantity" class="form-control" min="1" placeholder="Enter units to transfer" required>
        </div>
        <div class="form-group">
          <label class="form-label" for="transfer-notes">Transfer Manifest Notes</label>
          <textarea id="transfer-notes" class="form-control" rows="2" placeholder="e.g. Emergency replenishment for weekend surge"></textarea>
        </div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" onclick="app.closeModals()">Cancel</button>
        <button type="submit" class="btn btn-primary">Submit Transfer Request</button>
      </div>
    </form>
  </div>
</div>

<!-- Modal: Quick Direct Move (Car Rebalance) -->
<div id="modal-quick-transfer" class="modal-overlay">
  <div class="modal-content">
    <div class="modal-header">
      <div>
        <div class="modal-title">⚡ Quick Direct Stock Transfer</div>
        <div style="color: #64748B; font-size: 11px; margin-top: 2px;">Direct car move: Immediately decrements Store A and increments Store B in one instant step.</div>
      </div>
      <button class="modal-close" onclick="app.closeModals()">&times;</button>
    </div>
    <form id="form-quick-transfer">
      <div class="modal-body">
        <div class="form-group">
          <label class="form-label" for="quick-from-store">Source Store (Store A - Surplus)</label>
          <select id="quick-from-store" class="form-select" required>
            <option value="">Select source branch...</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label" for="quick-to-store">Destination Store (Store B - Needs Stock)</label>
          <select id="quick-to-store" class="form-select" required>
            <option value="">Select destination branch...</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label" for="quick-product-select">Product Item</label>
          <select id="quick-product-select" class="form-select" required disabled>
            <option value="">Select source store first...</option>
          </select>
          <div id="quick-stock-hint" style="font-size: 12px; color: #2563EB; margin-top: 4px; font-weight: 500;"></div>
        </div>
        <div class="form-group">
          <label class="form-label" for="quick-quantity">Quantity to Move</label>
          <input type="number" id="quick-quantity" class="form-control" min="1" placeholder="e.g. 20" required>
        </div>
        <div class="form-group">
          <label class="form-label" for="quick-notes">Manifest Note</label>
          <input type="text" id="quick-notes" class="form-control" placeholder="e.g. Direct car transfer between stores" value="Direct car transfer">
        </div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" onclick="app.closeModals()">Cancel</button>
        <button type="submit" class="btn btn-primary" style="background-color: #0284C7; border-color: #0284C7;">⚡ Move Stock Instantly</button>
      </div>
    </form>
  </div>
</div>

<!-- Modal: Compare Stores Dialog -->
<div id="modal-compare-stores" class="modal-overlay">
  <div class="modal-content modal-lg">
    <div class="modal-header">
      <div class="modal-title">Side-by-Side Store Performance Comparison</div>
      <button class="modal-close" onclick="app.closeModals()">&times;</button>
    </div>
    <div class="modal-body">
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
        <div>
          <label class="form-label" for="compare-select-a">Branch A</label>
          <select id="compare-select-a" class="form-select"></select>
        </div>
        <div>
          <label class="form-label" for="compare-select-b">Branch B</label>
          <select id="compare-select-b" class="form-select"></select>
        </div>
      </div>

      <div class="table-responsive">
        <table>
          <thead>
            <tr>
              <th>Performance Metric</th>
              <th id="compare-header-a">Branch A</th>
              <th id="compare-header-b">Branch B</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td style="font-weight: 600;">Today's Gross Sales</td>
              <td id="comp-sales-a">&mdash;</td>
              <td id="comp-sales-b">&mdash;</td>
            </tr>
            <tr>
              <td style="font-weight: 600;">Today's Orders</td>
              <td id="comp-orders-a">&mdash;</td>
              <td id="comp-orders-b">&mdash;</td>
            </tr>
            <tr>
              <td style="font-weight: 600;">Assigned Staff Count</td>
              <td id="comp-staff-a">&mdash;</td>
              <td id="comp-staff-b">&mdash;</td>
            </tr>
            <tr>
              <td style="font-weight: 600;">Branch Location</td>
              <td id="comp-loc-a">&mdash;</td>
              <td id="comp-loc-b">&mdash;</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div class="modal-footer">
      <button type="button" class="btn btn-secondary" onclick="app.closeModals()">Close</button>
    </div>
  </div>
</div>

<!-- Toast Container -->
<div id="toast-container"></div>

<script src="app.js"></script>
</body>
</html>
