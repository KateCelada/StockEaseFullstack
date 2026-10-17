<?php
require_once __DIR__ . '/../stockease_api/auth.php';

auth_start_session();

if (!isset($_SESSION['user_id']) || !isset($_SESSION['role']) || $_SESSION['role'] !== 'super_admin') {
    header('Location: login.php');
    exit;
}

$currentFullName = htmlspecialchars($_SESSION['full_name'] ?? 'Super Admin', ENT_QUOTES, 'UTF-8');
$currentUsername = htmlspecialchars($_SESSION['username'] ?? 'admin', ENT_QUOTES, 'UTF-8');
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
      <div class="nav-group" data-group="overview">
        <button type="button" class="nav-group-header" aria-expanded="true">
          <span>Overview</span>
          <span class="nav-group-arrow" aria-hidden="true"></span>
        </button>
        <div class="nav-group-items">
          <a class="nav-item active" data-tab="dashboard">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"/></svg>
            <span>Executive Overview</span>
          </a>
        </div>
      </div>

      <div class="nav-group" data-group="management">
        <button type="button" class="nav-group-header" aria-expanded="true">
          <span>Management</span>
          <span class="nav-group-arrow" aria-hidden="true"></span>
        </button>
        <div class="nav-group-items">
          <a class="nav-item" data-tab="businesses">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 13.255A23.931 23.931 0 0112 15c-3.183 0-6.22-.62-9-1.745M16 6V4a2 2 0 00-2-2h-4a2 2 0 00-2 2v2m4 6h.01M5 20h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"/></svg>
            <span>Businesses</span>
          </a>

          <a class="nav-item" data-tab="users">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"/></svg>
            <span>Users &amp; Roles</span>
          </a>
        </div>
      </div>

      <div class="nav-group" data-group="analytics">
        <button type="button" class="nav-group-header" aria-expanded="true">
          <span>Analytics</span>
          <span class="nav-group-arrow" aria-hidden="true"></span>
        </button>
        <div class="nav-group-items">
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
        </div>
      </div>

      <div class="nav-group" data-group="system">
        <button type="button" class="nav-group-header" aria-expanded="true">
          <span>System</span>
          <span class="nav-group-arrow" aria-hidden="true"></span>
        </button>
        <div class="nav-group-items">
          <a class="nav-item" data-tab="subscription">
            <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"/></svg>
            <span>Subscription &amp; Plans</span>
          </a>
        </div>
      </div>
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

          <div class="stat-card" style="cursor: pointer;" onclick="app.showTab('businesses')" title="Click to manage business portfolios and their branches">
            <div class="stat-header">
              <span class="stat-title">Active Store Branches</span>
            </div>
            <div id="kpi-total-stores" class="stat-value">&mdash;</div>
            <div class="stat-trend" style="color: #2563EB;">Click to manage businesses &rarr;</div>
          </div>

          <div class="stat-card" style="cursor: pointer;" onclick="app.showTab('users')" title="Click to manage system users and roles">
            <div class="stat-header">
              <span class="stat-title">Total Users</span>
            </div>
            <div id="kpi-total-users" class="stat-value">&mdash;</div>
            <div class="stat-trend" style="color: #64748B;">Click to manage users &rarr;</div>
          </div>
        </div>

        <!-- Business Name Cards (click opens the business progress page) -->
        <div class="card" style="margin-bottom: 24px;">
          <div class="card-header">
            <div>
              <div class="card-title">Business Portfolios</div>
              <div class="card-subtitle">Click a business name to open its full progress line chart, branch breakdown, and Excel export.</div>
            </div>
            <div style="display: flex; gap: 8px;">
              <button class="btn btn-secondary btn-sm" onclick="app.loadBusinessCards()">↻ Refresh</button>
              <button class="btn btn-primary btn-sm" onclick="app.showTab('businesses')">+ Add Business</button>
            </div>
          </div>
          <div id="container-business-cards" style="padding: 16px; display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px;">
            <div style="color: #64748B; font-size: 13px; padding: 16px; grid-column: 1 / -1; text-align: center;">Loading businesses...</div>
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
              <button class="btn btn-secondary btn-sm" data-export-table="table-store-performance">Export CSV</button>
              <button id="btn-open-compare" class="btn btn-secondary btn-sm">Compare Stores</button>
              <button class="btn btn-primary btn-sm" onclick="app.showTab('businesses')">Manage Businesses</button>
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
      <!-- ================= SECTION: BUSINESSES (+ BRANCH MANAGEMENT) ================= -->
      <section id="section-businesses" class="content-section">
        <div class="toolbar">
          <div>
            <h2 style="font-size: 16px; font-weight: 700;">Business Portfolios</h2>
            <div style="color: #64748B; font-size: 12px;">Register businesses, create store owner accounts, and group store branches under a single owner portfolio.</div>
          </div>
          <div class="toolbar-group">
            <button class="btn btn-secondary btn-sm" data-export-table="table-businesses">Export CSV</button>
            <button id="btn-modal-add-business" class="btn btn-primary btn-sm">+ Add Business</button>
          </div>
        </div>

        <div class="card">
          <div class="table-responsive">
            <table id="table-businesses">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Business Name</th>
                  <th>Business Owner</th>
                  <th style="text-align: center;">Active Branches</th>
                  <th style="text-align: right;">Actions</th>
                </tr>
              </thead>
              <tbody id="tbody-businesses-list">
                <tr><td colspan="5" style="text-align: center; color: #64748B; padding: 24px;">Loading businesses...</td></tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Store Branches (functions moved here from the removed Stores button) -->
        <div class="card" style="margin-top: 24px;">
          <div class="card-header">
            <div>
              <div class="card-title">Store Branches</div>
              <div class="card-subtitle">Physical retail store locations that roll up into the business portfolios above.</div>
            </div>
            <div class="toolbar-group">
              <button class="btn btn-secondary btn-sm" data-export-table="table-stores">Export CSV</button>
            </div>
          </div>
          <div class="table-responsive">
            <table id="table-stores">
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

      <!-- ============ SECTION: BUSINESS PROGRESS (drill-down page) ============ -->
      <section id="section-business-detail" class="content-section">
        <div class="toolbar">
          <div class="toolbar-group">
            <button class="btn btn-secondary btn-sm" onclick="app.showTab('dashboard')">&larr; Back to Overview</button>
            <div>
              <h2 id="business-detail-title" style="font-size: 16px; font-weight: 700;">Business</h2>
              <div id="business-detail-subtitle" style="color: #64748B; font-size: 12px;">Loading business progress...</div>
            </div>
          </div>
          <div class="toolbar-group">
            <button class="btn btn-secondary btn-sm btn-business-period active" data-bperiod="today">Today</button>
            <button class="btn btn-secondary btn-sm btn-business-period" data-bperiod="this_week">This Week</button>
            <button class="btn btn-secondary btn-sm btn-business-period" data-bperiod="this_month">This Month</button>
            <button id="btn-export-business-excel" class="btn btn-primary btn-sm">Export to Excel</button>
          </div>
        </div>

        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Business Revenue</span></div>
            <div id="biz-revenue" class="stat-value">&mdash;</div>
          </div>
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Orders</span></div>
            <div id="biz-orders" class="stat-value">&mdash;</div>
          </div>
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Average Order</span></div>
            <div id="biz-avg" class="stat-value">&mdash;</div>
          </div>
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Units Sold</span></div>
            <div id="biz-units" class="stat-value">&mdash;</div>
          </div>
        </div>

        <!-- Whole-business progress line chart (Excel exportable) -->
        <div class="card" style="margin-bottom: 24px;">
          <div class="card-header">
            <div>
              <div class="card-title">📈 Whole Business Progress</div>
              <div id="business-chart-subtitle" class="card-subtitle">Combined sales trajectory of every branch in this business.</div>
            </div>
            <div class="toolbar-group">
              <button class="btn btn-secondary btn-sm" id="btn-business-chart-excel">Chart &rarr; Excel</button>
              <button class="btn btn-secondary btn-sm" id="btn-business-chart-png">⤓ PNG</button>
            </div>
          </div>
          <div style="height: 300px; position: relative;">
            <canvas id="chart-business-progress"></canvas>
          </div>
        </div>

        <!-- Branch cards of this business -->
        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">Branches of this Business</div>
              <div class="card-subtitle">Click a branch card to open its progress, top selling products, and assigned accounts.</div>
            </div>
            <span id="business-branch-count" class="badge badge-slate">0 Branch(es)</span>
          </div>
          <div id="container-business-branches" style="padding: 16px; display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px;">
            <div style="color: #64748B; font-size: 13px; padding: 16px; grid-column: 1 / -1; text-align: center;">Loading branches...</div>
          </div>
        </div>
      </section>

      <!-- ============ SECTION: BRANCH PROGRESS (drill-down page) ============ -->
      <section id="section-branch-detail" class="content-section">
        <div class="toolbar">
          <div class="toolbar-group">
            <button id="btn-branch-detail-back" class="btn btn-secondary btn-sm">&larr; Back</button>
            <div>
              <h2 id="branch-detail-title" style="font-size: 16px; font-weight: 700;">Branch</h2>
              <div id="branch-detail-subtitle" style="color: #64748B; font-size: 12px;">Loading branch progress...</div>
            </div>
          </div>
          <div class="toolbar-group">
            <button class="btn btn-secondary btn-sm btn-branch-period active" data-brperiod="today">Today</button>
            <button class="btn btn-secondary btn-sm btn-branch-period" data-brperiod="this_week">This Week</button>
            <button class="btn btn-secondary btn-sm btn-branch-period" data-brperiod="this_month">This Month</button>
            <button id="btn-export-branch-excel" class="btn btn-primary btn-sm">Export to Excel</button>
          </div>
        </div>

        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Branch Revenue</span></div>
            <div id="br-revenue" class="stat-value">&mdash;</div>
          </div>
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Orders</span></div>
            <div id="br-orders" class="stat-value">&mdash;</div>
          </div>
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Average Order</span></div>
            <div id="br-avg" class="stat-value">&mdash;</div>
          </div>
          <div class="stat-card">
            <div class="stat-header"><span class="stat-title">Units Sold</span></div>
            <div id="br-units" class="stat-value">&mdash;</div>
          </div>
        </div>

        <div class="insights-row" style="margin-bottom: 20px;">
          <!-- Branch progress line chart (Excel exportable) -->
          <div class="card" style="flex: 1.4;">
            <div class="card-header">
              <div>
                <div class="card-title">📈 Branch Progress</div>
                <div id="branch-chart-subtitle" class="card-subtitle">Sales trajectory for the selected period.</div>
              </div>
              <div class="toolbar-group">
                <button class="btn btn-secondary btn-sm" id="btn-branch-chart-excel">Chart &rarr; Excel</button>
                <button class="btn btn-secondary btn-sm" id="btn-branch-chart-png">⤓ PNG</button>
              </div>
            </div>
            <div style="height: 260px; position: relative;">
              <canvas id="chart-branch-progress"></canvas>
            </div>
          </div>

          <!-- Top selling products -->
          <div class="card" style="flex: 1;">
            <div class="card-header">
              <div>
                <div class="card-title">🏆 Top Selling Products</div>
                <div class="card-subtitle">Best performing items in this branch for the selected period.</div>
              </div>
              <button class="btn btn-secondary btn-sm" data-export-table="table-branch-top-products">Export CSV</button>
            </div>
            <div class="table-responsive" style="max-height: 260px; overflow-y: auto;">
              <table id="table-branch-top-products">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Product</th>
                    <th style="text-align: right;">Units</th>
                    <th style="text-align: right;">Revenue</th>
                  </tr>
                </thead>
                <tbody id="tbody-branch-top-products">
                  <tr><td colspan="4" style="text-align: center; color: #64748B; padding: 20px;">Loading top products...</td></tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- Assigned accounts card (click to reveal) -->
        <div class="card">
          <div class="card-header" style="cursor: pointer;" onclick="app.toggleBranchAccounts()" title="Click to display every account assigned to this branch">
            <div>
              <div class="card-title">👥 Accounts Assigned to this Branch</div>
              <div class="card-subtitle">Click this card to display all accounts assigned in this branch.</div>
            </div>
            <span id="branch-accounts-toggle" class="btn btn-secondary btn-sm">Show Accounts</span>
          </div>
          <div id="container-branch-accounts" style="display: none; padding: 0 16px 16px;">
            <div style="color: #64748B; font-size: 13px;">Loading accounts...</div>
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
            <button class="btn btn-secondary btn-sm" data-export-table="table-transfers">Export CSV</button>
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
            <table id="table-transfers">
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
              <option value="store_owner">Store Owner</option>
              <option value="manager">Manager</option>
              <option value="store_admin">Store Admin</option>
              <option value="staff">Staff &ndash; Cashier</option>
              <option value="inventory_checker">Staff &ndash; Inventory</option>
            </select>
            <button class="btn btn-secondary btn-sm" data-export-table="table-users">Export CSV</button>
          </div>
        </div>

        <div class="card">
          <div class="table-responsive">
            <table id="table-users">
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
          <div class="toolbar-group">
            <button class="btn btn-secondary btn-sm" data-export-table="table-store-admins">Export CSV</button>
            <button id="btn-modal-add-admin" class="btn btn-primary btn-sm">+ Assign Store Admin</button>
          </div>
        </div>

        <div class="card">
          <div class="table-responsive">
            <table id="table-store-admins">
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
            <button class="btn btn-secondary btn-sm" data-export-table="table-insight-comparison">Export CSV</button>
          </div>
          <div class="table-responsive">
            <table id="table-insight-comparison">
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

        <div id="reports-period-scope" style="color: #64748B; font-size: 12px; margin: -8px 0 16px;">Today</div>

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
                <div id="chart-branch-earnings-subtitle" class="card-subtitle">Real-time daily earnings trajectory across operational dates</div>
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

        <!-- Other Sales: manual (non-POS) sales + voided/refunded transactions -->
        <div class="card" style="margin-top: 20px;">
          <div class="card-header" style="display: flex; justify-content: space-between; align-items: center;">
            <div>
              <div class="card-title">🧾 Other Sales (Non-POS)</div>
              <div class="card-subtitle">Sales recorded manually and voided/refunded transactions, tracked separately from POS sales</div>
            </div>
            <button id="btn-record-manual-sale" class="btn btn-primary btn-sm">+ Record Manual Sale</button>
          </div>
          <div class="stats-grid" style="margin-bottom: 12px;">
            <div class="stat-card">
              <div class="stat-header"><span class="stat-title">Manual Sales</span></div>
              <div id="rep-other-total" class="stat-value">&mdash;</div>
              <div id="rep-other-count" style="color: #64748B; font-size: 11px;">&mdash;</div>
            </div>
            <div class="stat-card">
              <div class="stat-header"><span class="stat-title">Voided / Refunded</span></div>
              <div id="rep-voided-total" class="stat-value">&mdash;</div>
              <div id="rep-voided-count" style="color: #64748B; font-size: 11px;">&mdash;</div>
            </div>
          </div>
          <div class="table-responsive">
            <table id="table-other-sales">
              <thead>
                <tr>
                  <th>#</th><th>Date</th><th>Items</th><th>Recorded By</th><th>Payment</th><th>Status</th><th class="text-right">Amount</th>
                </tr>
              </thead>
              <tbody id="tbody-other-sales">
                <tr><td colspan="7" style="text-align: center; color: #64748B; padding: 16px;">Select a branch to view other sales.</td></tr>
              </tbody>
            </table>
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
            <button id="btn-audit-export" class="btn btn-secondary btn-sm">Export CSV</button>
            <button id="btn-audit-refresh" class="btn btn-secondary btn-sm">Refresh</button>
          </div>
        </div>

        <div class="card">
          <div class="table-responsive">
            <table id="table-audit-logs">
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

          <!-- Audit Log Pagination -->
          <div id="audit-pagination" class="pagination-bar">
            <div class="pagination-left">
              <label for="audit-per-page" class="pagination-label">Rows per page</label>
              <select id="audit-per-page" class="form-select" style="width: auto; padding: 5px 8px; font-size: 12px;">
                <option value="10">10</option>
                <option value="25" selected>25</option>
                <option value="50">50</option>
                <option value="100">100</option>
              </select>
              <span id="audit-range-label" class="pagination-info">Loading&hellip;</span>
            </div>
            <div class="pagination-right">
              <button id="audit-first-page" class="page-btn" type="button" title="First page">&laquo;</button>
              <button id="audit-prev-page" class="page-btn" type="button" title="Previous page">&lsaquo;</button>
              <span id="audit-page-numbers" class="page-numbers"></span>
              <button id="audit-next-page" class="page-btn" type="button" title="Next page">&rsaquo;</button>
              <button id="audit-last-page" class="page-btn" type="button" title="Last page">&raquo;</button>
            </div>
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

        <!-- (System Settings removed by request) -->
      </section>

    </div>
  </main>
</div>

<!-- ================= MODALS ================= -->

<!-- Modal: Add Business Portfolio -->
<div id="modal-add-business" class="modal-overlay">
  <div class="modal-content">
    <div class="modal-header">
      <div>
        <div class="modal-title">Register New Business Portfolio</div>
        <div style="font-size: 12px; color: #64748B;">Group store branches under a named business owned by a Store Owner account.</div>
      </div>
      <button class="modal-close" onclick="app.closeModals()">&times;</button>
    </div>
    <form id="form-add-business">
      <div class="modal-body">
        <div class="form-group">
          <label class="form-label" for="new-business-name">Business Name *</label>
          <input type="text" id="new-business-name" class="form-control" placeholder="e.g. Apex Retail Group" required>
        </div>
        <div class="form-group">
          <label class="form-label" for="business-owner-select">Business Owner</label>
          <select id="business-owner-select" class="form-select" onchange="app.toggleNewBusinessOwnerFields()">
            <option value="">-- No Owner (Assign Later) --</option>
            <option value="__new__">+ Create New Store Owner Account</option>
          </select>
          <div style="font-size: 11px; color: #64748B; margin-top: 4px;">Pick an existing Store Owner / Manager, or create the store owner account together with this business.</div>
        </div>

        <!-- Collapsible New Store Owner Account Fields -->
        <div id="new-business-owner-fields" style="display: none; background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 8px; padding: 12px; margin-top: 8px;">
          <div style="font-size: 12px; font-weight: 700; color: #0F172A; margin-bottom: 8px;">New Store Owner Account</div>
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 8px;">
            <div>
              <label class="form-label" style="font-size: 11px;">Owner Full Name</label>
              <input type="text" id="new-business-owner-fullname" class="form-control" placeholder="e.g. Juan dela Cruz">
            </div>
            <div>
              <label class="form-label" style="font-size: 11px;">Owner Username</label>
              <input type="text" id="new-business-owner-username" class="form-control" placeholder="e.g. jdelacruz">
            </div>
          </div>
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
            <div>
              <label class="form-label" style="font-size: 11px;">Owner Email</label>
              <input type="email" id="new-business-owner-email" class="form-control" placeholder="e.g. juan@stockease.com">
            </div>
            <div>
              <label class="form-label" style="font-size: 11px;">Initial Password</label>
              <div class="password-wrap">
                <input type="password" id="new-business-owner-password" class="form-control" placeholder="e.g. admin123" value="admin123">
                <button type="button" class="btn-eye" title="Show password" aria-label="Show password" aria-pressed="false" onclick="app.togglePassword('new-business-owner-password', this)">
                  <svg class="eye-open" width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/></svg>
                  <svg class="eye-closed" width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24" style="display: none;"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21"/></svg>
                </button>
              </div>
              <div class="pw-meter" id="new-business-owner-password-meter" aria-live="polite">
                <div class="pw-meter-track"><div class="pw-meter-bar"></div></div>
                <div class="pw-meter-label">Password strength: —</div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" onclick="app.closeModals()">Cancel</button>
        <button type="submit" class="btn btn-primary">Create Business</button>
      </div>
    </form>
  </div>
</div>

<!-- Modal: Rename Business Portfolio -->
<div id="modal-rename-business" class="modal-overlay">
  <div class="modal-content" style="max-width: 460px;">
    <div class="modal-header">
      <div>
        <div class="modal-title">Rename Business Portfolio</div>
        <div style="font-size: 12px; color: #64748B;">The business name is the real name of the company, not the owner&rsquo;s name.</div>
      </div>
      <button class="modal-close" onclick="app.closeModals()">&times;</button>
    </div>
    <form id="form-rename-business">
      <input type="hidden" id="rename-business-id">
      <div class="modal-body">
        <div class="form-group">
          <label class="form-label" for="rename-business-name">Business Name *</label>
          <input type="text" id="rename-business-name" class="form-control" placeholder="e.g. Apex Retail Group" required>
        </div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" onclick="app.closeModals()">Cancel</button>
        <button type="submit" class="btn btn-primary">Save Name</button>
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
          <div class="password-wrap">
            <input type="password" id="admin-password" class="form-control" placeholder="Enter secure password" required minlength="8">
            <button type="button" class="btn-eye" title="Show password" aria-label="Show password" aria-pressed="false" onclick="app.togglePassword('admin-password', this)">
              <svg class="eye-open" width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/></svg>
              <svg class="eye-closed" width="18" height="18" fill="none" stroke="currentColor" viewBox="0 0 24 24" style="display: none;"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21"/></svg>
            </button>
          </div>
          <div class="pw-meter" id="admin-password-meter" aria-live="polite">
            <div class="pw-meter-track"><div class="pw-meter-bar"></div></div>
            <div class="pw-meter-label">Password strength: —</div>
          </div>
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

<!-- Modal: User Role & Store Scope (read-only) -->
<div id="modal-update-role" class="modal-overlay">
  <div class="modal-content">
    <div class="modal-header">
      <div>
        <div class="modal-title">User Role &amp; Store Scope</div>
        <div style="font-size: 12px; color: #64748B;">Roles and branch scope are fixed when the account is created.</div>
      </div>
      <button class="modal-close" onclick="app.closeModals()">&times;</button>
    </div>
    <div class="modal-body">
      <div class="form-group">
        <label class="form-label">User</label>
        <div id="edit-user-display" style="font-weight: 600; font-size: 14px; padding: 6px 0;"></div>
      </div>
      <div class="form-group">
        <label class="form-label">System Role</label>
        <div style="padding: 6px 0;"><span id="edit-user-role-text" class="badge badge-slate">&mdash;</span></div>
      </div>
      <div class="form-group">
        <label class="form-label">Assigned Store Branch</label>
        <div id="edit-user-store-text" style="padding: 6px 0; font-weight: 600; font-size: 13px; color: #334155;">&mdash;</div>
        <div style="font-size: 11px; color: #64748B; margin-top: 4px;">The System Super Admin cannot change a user&rsquo;s role or branch scope from this console. Create the account with the correct role and branch instead.</div>
      </div>
    </div>
    <div class="modal-footer">
      <button type="button" class="btn btn-secondary" onclick="app.closeModals()">Close</button>
    </div>
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

<!-- Modal: Record Manual (Other) Sale -->
<div id="modal-manual-sale" class="modal-overlay">
  <div class="modal-content">
    <div class="modal-header">
      <div>
        <div class="modal-title">🧾 Record Manual Sale</div>
        <div class="card-subtitle" style="font-size: 12px; color: #64748B;">Sales made outside the POS (phone orders, bulk deals, delivery). Stock levels are adjusted automatically.</div>
      </div>
      <button class="modal-close" onclick="app.closeModals()">&times;</button>
    </div>
    <form id="form-manual-sale">
      <div class="modal-body">
        <div class="form-group">
          <label class="form-label" for="ms-payment">Payment Method</label>
          <select id="ms-payment" class="form-select">
            <option>Cash</option>
            <option>GCash</option>
            <option>Bank Transfer</option>
            <option>Other</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Add Item</label>
          <div style="display: grid; grid-template-columns: 1fr 76px 110px auto; gap: 8px;">
            <select id="ms-product" class="form-select"><option value="">Select a product...</option></select>
            <input type="number" id="ms-qty" class="form-control" min="1" value="1" title="Quantity">
            <input type="number" id="ms-price" class="form-control" min="0" step="0.01" placeholder="Unit price" title="Unit price">
            <button type="button" id="btn-ms-add" class="btn btn-secondary">Add</button>
          </div>
          <div id="ms-items" style="margin-top: 8px; font-size: 12px;"></div>
        </div>
        <div class="form-group">
          <label class="form-label" for="ms-notes">Notes (optional)</label>
          <textarea id="ms-notes" class="form-control" rows="2" placeholder="e.g. Bulk order via phone, delivered by owner"></textarea>
        </div>
        <div style="font-weight: 700; font-size: 14px;">Total: <span id="ms-total">₱0.00</span></div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" onclick="app.closeModals()">Cancel</button>
        <button type="submit" class="btn btn-primary">Record Sale</button>
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
