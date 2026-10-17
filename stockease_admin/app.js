/**
 * StockEase Super Admin Portal - Frontend Application Controller
 * High-density enterprise dashboard with reactive data binding and zero external dependencies.
 */

const app = {
  activeTab: 'dashboard',
  currentSubscription: null,
  stores: [],
  users: [],
  storeAdmins: [],
  businesses: [],
  currentPerformanceList: [],
  currentInsightsPeriod: 7,
  currentReportPeriod: 'today',
  currentReportStoreId: null,

  // Audit log pagination
  auditPage: 1,
  auditPerPage: 25,
  auditPagination: null,

  // Business / Branch drill-down pages
  currentBusinessId: null,
  currentBusinessData: null,
  businessPeriod: 'today',
  currentBranchId: null,
  currentBranchData: null,
  branchPeriod: 'today',
  branchReturnTab: 'dashboard',

  // Initialization
  init() {
    this.setupDateDisplay();
    this.setupNavigation();
    this.setupModalEvents();
    this.setupFormSubmissions();
    this.setupPasswordMeters();
    this.setupManualSaleForm();
    this.loadSubscription();
    this.loadDashboardData();
  },

  setupDateDisplay() {
    const el = document.getElementById('live-header-date');
    if (el) {
      const now = new Date();
      const options = { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' };
      el.textContent = now.toLocaleDateString('en-US', options);
    }
  },

  setupNavigation() {
    document.querySelectorAll('.sidebar-nav .nav-item').forEach(item => {
      item.addEventListener('click', (e) => {
        const tab = item.getAttribute('data-tab');
        if (tab) {
          this.showTab(tab);
        }
      });
    });

    // Accordion-style collapsible sidebar groups (mirrors the Android sidebar:
    // opening a section closes the others; tapping an open one closes it).
    this.navGroups = Array.from(document.querySelectorAll('.sidebar-nav .nav-group'));
    this.navGroups.forEach(group => {
      const header = group.querySelector('.nav-group-header');
      if (!header) return;
      header.addEventListener('click', () => {
        const willOpen = !group.classList.contains('open');
        this.navGroups.forEach(other => {
          other.classList.remove('open');
          const h = other.querySelector('.nav-group-header');
          if (h) h.setAttribute('aria-expanded', 'false');
        });
        if (willOpen) {
          group.classList.add('open');
          header.setAttribute('aria-expanded', 'true');
        }
      });
    });

    // Start with the group owning the initial tab expanded.
    this.expandNavGroupForTab(this.activeTab || 'dashboard');

    const refreshBtn = document.getElementById('btn-global-refresh');
    if (refreshBtn) {
      refreshBtn.addEventListener('click', () => {
        this.refreshCurrentTab();
      });
    }

    // Insights Period Buttons
    document.querySelectorAll('.btn-period').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.btn-period').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        this.currentInsightsPeriod = parseInt(btn.getAttribute('data-days'), 10) || 7;
        this.loadInsights();
      });
    });

    // Reports Period Buttons
    document.querySelectorAll('.btn-report-period').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.btn-report-period').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        this.currentReportPeriod = btn.getAttribute('data-period') || 'today';
        this.loadReports();
      });
    });

    // Reports Store Select
    const repSelect = document.getElementById('reports-store-select');
    if (repSelect) {
      repSelect.addEventListener('change', () => {
        this.currentReportStoreId = repSelect.value;
        this.loadReports();
      });
    }

    // Transfer Status Filter
    const trFilter = document.getElementById('filter-transfer-status');
    if (trFilter) {
      trFilter.addEventListener('change', () => {
        this.loadTransfers();
      });
    }

    // Audit Filters
    const auditSearch = document.getElementById('input-audit-search');
    const auditAction = document.getElementById('filter-audit-action');
    const auditRefresh = document.getElementById('btn-audit-refresh');
    const auditPerPage = document.getElementById('audit-per-page');
    if (auditRefresh) auditRefresh.addEventListener('click', () => { this.auditPage = 1; this.loadAuditLogs(); });
    if (auditSearch) auditSearch.addEventListener('keyup', (e) => { if (e.key === 'Enter') { this.auditPage = 1; this.loadAuditLogs(); } });
    if (auditAction) auditAction.addEventListener('change', () => { this.auditPage = 1; this.loadAuditLogs(); });
    if (auditPerPage) auditPerPage.addEventListener('change', () => {
      this.auditPerPage = parseInt(auditPerPage.value, 10) || 25;
      this.auditPage = 1;
      this.loadAuditLogs();
    });
    const auditExport = document.getElementById('btn-audit-export');
    if (auditExport) auditExport.addEventListener('click', () => this.exportAuditLogsCsv());

    // Audit pagination controls
    const bindAuditPage = (id, pageFn) => {
      const el = document.getElementById(id);
      if (el) el.addEventListener('click', () => {
        const next = pageFn(this.auditPage, this.auditPagination);
        if (next && next !== this.auditPage) {
          this.auditPage = next;
          this.loadAuditLogs();
        }
      });
    };
    bindAuditPage('audit-first-page', () => 1);
    bindAuditPage('audit-prev-page', (p) => Math.max(1, p - 1));
    bindAuditPage('audit-next-page', (p, pg) => Math.min((pg ? pg.total_pages : p) || p, p + 1));
    bindAuditPage('audit-last-page', (p, pg) => (pg && pg.total_pages ? pg.total_pages : p));
    const pageNumbers = document.getElementById('audit-page-numbers');
    if (pageNumbers) {
      pageNumbers.addEventListener('click', (e) => {
        const btn = e.target.closest('[data-page]');
        if (!btn) return;
        const page = parseInt(btn.getAttribute('data-page'), 10);
        if (page && page !== this.auditPage) {
          this.auditPage = page;
          this.loadAuditLogs();
        }
      });
    }

    // Business drill-down page period filters
    document.querySelectorAll('.btn-business-period').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.btn-business-period').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        this.businessPeriod = btn.getAttribute('data-bperiod') || 'today';
        this.loadBusinessDetail();
      });
    });

    // Branch drill-down page period filters
    document.querySelectorAll('.btn-branch-period').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.btn-branch-period').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        this.branchPeriod = btn.getAttribute('data-brperiod') || 'today';
        this.loadBranchDetail();
      });
    });

    const bizExcel = document.getElementById('btn-export-business-excel');
    if (bizExcel) bizExcel.addEventListener('click', () => this.exportBusinessProgressExcel());
    const bizChartExcel = document.getElementById('btn-business-chart-excel');
    if (bizChartExcel) bizChartExcel.addEventListener('click', () => this.exportChartSeriesToExcel('chart-business-progress', 'stockease_business_progress_' + new Date().toISOString().split('T')[0] + '.csv'));
    const bizChartPng = document.getElementById('btn-business-chart-png');
    if (bizChartPng) bizChartPng.addEventListener('click', () => this.downloadChartPng(this.getChartInstance('chart-business-progress'), 'stockease_business_progress_' + new Date().toISOString().split('T')[0] + '.png'));

    const brExcel = document.getElementById('btn-export-branch-excel');
    if (brExcel) brExcel.addEventListener('click', () => this.exportBranchProgressExcel());
    const brChartExcel = document.getElementById('btn-branch-chart-excel');
    if (brChartExcel) brChartExcel.addEventListener('click', () => this.exportChartSeriesToExcel('chart-branch-progress', 'stockease_branch_progress_' + new Date().toISOString().split('T')[0] + '.csv'));
    const brChartPng = document.getElementById('btn-branch-chart-png');
    if (brChartPng) brChartPng.addEventListener('click', () => this.downloadChartPng(this.getChartInstance('chart-branch-progress'), 'stockease_branch_progress_' + new Date().toISOString().split('T')[0] + '.png'));

    const branchBack = document.getElementById('btn-branch-detail-back');
    if (branchBack) branchBack.addEventListener('click', () => this.showTab(this.branchReturnTab || 'dashboard'));

    // User Filter
    const userRoleFilter = document.getElementById('filter-user-role');
    const userSearch = document.getElementById('input-search-users');
    if (userRoleFilter) userRoleFilter.addEventListener('change', () => this.renderUsersTable());
    if (userSearch) userSearch.addEventListener('input', () => this.renderUsersTable());

    // Export CSV
    const btnCsv = document.getElementById('btn-export-reports-csv');
    if (btnCsv) {
      btnCsv.addEventListener('click', () => this.exportReportsCSV());
    }

    // Generic per-table CSV export buttons (data-export-table="<table id>")
    document.querySelectorAll('[data-export-table]').forEach(btn => {
      btn.addEventListener('click', () => {
        const tableId = btn.getAttribute('data-export-table');
        this.exportTableToCsv(tableId, this.csvExportName(tableId.replace(/^table-/, '')));
      });
    });
  },

  // Which sidebar group owns each tab (drives the accordion auto-expand).
  // business-detail / branch-detail are sub-pages of Businesses.
  tabToNavGroup(tabId) {
    const map = {
      dashboard: 'overview',
      businesses: 'management',
      'business-detail': 'management',
      'branch-detail': 'management',
      users: 'management',
      insights: 'analytics',
      reports: 'analytics',
      audit: 'analytics',
      subscription: 'system'
    };
    return map[tabId] || null;
  },

  // Opens the sidebar group that owns tabId (accordion: others close).
  expandNavGroupForTab(tabId) {
    const groupName = this.tabToNavGroup(tabId);
    if (!groupName || !this.navGroups || this.navGroups.length === 0) return;

    this.navGroups.forEach(group => {
      const open = group.getAttribute('data-group') === groupName;
      group.classList.toggle('open', open);
      const header = group.querySelector('.nav-group-header');
      if (header) header.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
  },

  showTab(tabId) {
    this.activeTab = tabId;

    // Update navigation
    document.querySelectorAll('.sidebar-nav .nav-item').forEach(item => {
      item.classList.toggle('active', item.getAttribute('data-tab') === tabId);
    });

    // Keep the owning sidebar group open so the active item stays visible.
    this.expandNavGroupForTab(tabId);

    // Update content sections
    document.querySelectorAll('.content-section').forEach(sec => {
      sec.classList.remove('active');
    });

    const activeSec = document.getElementById('section-' + tabId);
    if (activeSec) {
      activeSec.classList.add('active');
    }

    // Update Page Header Title
    const titles = {
      dashboard: 'Executive Overview',
      businesses: 'Business Portfolio Management',
      'business-detail': 'Business Progress Report',
      'branch-detail': 'Branch Progress Report',
      transfers: 'Inter-Store Stock Transfers',
      users: 'User Accounts & Roles',
      admins: 'Branch Store Administrators',
      insights: 'StockEase Smart Inventory Insights',
      reports: 'Branch Sales & Performance Reports',
      audit: 'Security & Operational Audit Logs',
      subscription: 'Subscription & Freemium Management'
    };
    const titleEl = document.getElementById('current-page-title');
    if (titleEl && titles[tabId]) {
      titleEl.textContent = titles[tabId];
    }

    // Load data for selected tab
    this.refreshCurrentTab();
  },

  refreshCurrentTab() {
    switch (this.activeTab) {
      case 'dashboard':
        this.loadDashboardData();
        break;
      case 'businesses':
        this.loadBusinesses();
        this.loadStores();
        break;
      case 'business-detail':
        this.loadBusinessDetail();
        break;
      case 'branch-detail':
        this.loadBranchDetail();
        break;
      case 'transfers':
        this.loadTransfers();
        break;
      case 'users':
        this.loadUsers();
        break;
      case 'admins':
        this.loadStoreAdmins();
        break;
      case 'insights':
        this.loadInsights();
        break;
      case 'reports':
        this.loadReports();
        break;
      case 'audit':
        this.loadAuditLogs();
        break;
      case 'subscription':
        this.loadSubscription();
        break;
    }
  },

  // Modal Dialogs
  setupModalEvents() {
    const btnAddBusiness = document.getElementById('btn-modal-add-business');
    if (btnAddBusiness) {
      btnAddBusiness.addEventListener('click', async () => {
        await this.setupBusinessOwnerSelect();
        this.openModal('modal-add-business');
      });
    }

    const btnAddAdmin = document.getElementById('btn-modal-add-admin');
    if (btnAddAdmin) {
      btnAddAdmin.addEventListener('click', async () => {
        // The branch assignment is required, so make sure the store list is
        // loaded before populating (populateStoreSelect no-ops when empty).
        if (!this.stores || this.stores.length === 0) {
          await this.loadStores();
        }
        this.populateStoreSelect('admin-store-select');
        this.openModal('modal-add-admin');
      });
    }

    const btnNewTransfer = document.getElementById('btn-modal-new-transfer');
    if (btnNewTransfer) {
      btnNewTransfer.addEventListener('click', () => {
        this.setupNewTransferModal();
        this.openModal('modal-new-transfer');
      });
    }

    const btnQuickTransfer = document.getElementById('btn-modal-quick-transfer');
    if (btnQuickTransfer) {
      btnQuickTransfer.addEventListener('click', () => {
        this.setupQuickTransferModal();
        this.openModal('modal-quick-transfer');
      });
    }

    const btnCompare = document.getElementById('btn-open-compare');
    if (btnCompare) {
      btnCompare.addEventListener('click', () => this.openCompareModal());
    }
  },

  // -------------------------------------------------------------
  // Business Portfolios
  // -------------------------------------------------------------
  async loadBusinesses() {
    const tbody = document.getElementById('tbody-businesses-list');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: #64748B; padding: 24px;">Loading businesses...</td></tr>';

    try {
      const res = await fetch('../stockease_api/manage_businesses.php?action=list', { credentials: 'include' });
      const data = await res.json();
      if (!data.success) throw new Error(data.message);

      this.businesses = data.businesses || [];
      if (this.businesses.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: #64748B; padding: 20px;">No businesses registered yet. Click "+ Add Business" to create one.</td></tr>';
        return;
      }

      tbody.innerHTML = this.businesses.map(b => `
        <tr>
          <td style="font-weight: 600; color: #64748B;">#${b.id}</td>
          <td style="font-weight: 600;">${this.escape(b.business_name)}</td>
          <td>
            ${b.owner_name
              ? `<span class="badge badge-info" title="Owner ID #${b.owner_id}">👤 ${this.escape(b.owner_name)}</span>`
              : `<span style="color: #94A3B8; font-size: 12px; font-style: italic;">No owner assigned</span>`}
          </td>
          <td style="text-align: center;"><span class="badge badge-slate">${parseInt(b.branch_count, 10) || 0} Branch(es)</span></td>
          <td style="text-align: right;">
            <button class="btn btn-outline-secondary btn-sm" onclick="app.openRenameBusiness(${b.id})" title="Rename this business">✎ Rename</button>
            <button class="btn btn-outline-primary btn-sm" onclick="app.openBusinessDetail(${b.id})">View Progress</button>
          </td>
        </tr>
      `).join('');
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: #DC2626; padding: 20px;">Error: ${err.message}</td></tr>`;
    }
  },

  openRenameBusiness(businessId) {
    const business = (this.businesses || []).find(b => b.id === businessId);
    if (!business) return;
    document.getElementById('rename-business-id').value = business.id;
    document.getElementById('rename-business-name').value = business.business_name || '';
    this.openModal('modal-rename-business');
    const input = document.getElementById('rename-business-name');
    if (input) { input.focus(); input.select(); }
  },

  // -------------------------------------------------------------
  // Dashboard business cards + business / branch drill-down pages
  // -------------------------------------------------------------
  async loadBusinessCards() {
    const container = document.getElementById('container-business-cards');
    if (!container) return;

    try {
      const res = await fetch('../stockease_api/manage_businesses.php?action=list', { credentials: 'include' });
      const data = await res.json();
      if (!data.success) throw new Error(data.message || 'Unable to load businesses');

      const list = data.businesses || [];
      this.businesses = list;

      if (list.length === 0) {
        container.innerHTML = '<div style="color: #64748B; font-size: 13px; padding: 16px; grid-column: 1 / -1; text-align: center;">No businesses registered yet. Click "+ Add Business" to create the first one.</div>';
        return;
      }

      container.innerHTML = list.map(b => {
        const branches = parseInt(b.branch_count, 10) || 0;
        return `
          <div class="business-name-card" style="background: linear-gradient(135deg, #FFFFFF 0%, #F8FAFC 100%); border: 1px solid #CBD5E1; border-left: 4px solid #2563EB; border-radius: 12px; padding: 16px 18px; cursor: pointer; transition: transform .12s ease, box-shadow .12s ease;"
               onclick="app.openBusinessDetail(${b.id})"
               title="Click to open this business progress page">
            <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 10px;">
              <div style="font-size: 12px; font-weight: 700; color: #2563EB; letter-spacing: .4px; text-transform: uppercase;">Business</div>
              <span class="badge badge-slate">${branches} Branch(es)</span>
            </div>
            <div style="font-size: 17px; font-weight: 800; color: #0F172A; margin-top: 8px; line-height: 1.3;">${this.escape(b.business_name)}</div>
            <div style="font-size: 12px; color: #64748B; margin-top: 6px;">
              👤 ${b.owner_name ? this.escape(b.owner_name) : '<em>No owner assigned</em>'}
            </div>
            <div style="font-size: 12px; color: #2563EB; font-weight: 600; margin-top: 12px;">View progress &rarr;</div>
          </div>
        `;
      }).join('');
    } catch (err) {
      container.innerHTML = `<div style="color: #DC2626; font-size: 13px; padding: 16px; grid-column: 1 / -1; text-align: center;">Error loading businesses: ${this.escape(err.message)}</div>`;
    }
  },

  openBusinessDetail(businessId) {
    this.currentBusinessId = businessId;
    this.setPeriodButtons('.btn-business-period', 'data-bperiod', 'today');
    this.businessPeriod = 'today';
    this.showTab('business-detail');
  },

  setPeriodButtons(selector, attr, value) {
    document.querySelectorAll(selector).forEach(b => {
      b.classList.toggle('active', b.getAttribute(attr) === value);
    });
  },

  periodLabel(period) {
    if (period === 'this_week') return 'This Week';
    if (period === 'this_month') return 'This Month';
    if (period === 'custom') return 'Custom Range';
    return 'Today';
  },

  async loadBusinessDetail() {
    if (!this.currentBusinessId) return;
    const container = document.getElementById('container-business-branches');
    if (container) container.innerHTML = '<div style="color: #64748B; font-size: 13px; padding: 16px; grid-column: 1 / -1; text-align: center;">Loading branches...</div>';

    try {
      const url = `../stockease_api/get_business_progress.php?business_id=${this.currentBusinessId}&period=${encodeURIComponent(this.businessPeriod)}`;
      const res = await fetch(url, { credentials: 'include' });
      const data = await res.json();
      if (!data.success) throw new Error(data.message || 'Unable to load business progress');

      this.currentBusinessData = data;
      const bizName = data.business ? data.business.business_name : 'Business #' + this.currentBusinessId;
      const s = data.summary || {};

      const titleEl = document.getElementById('business-detail-title');
      if (titleEl) titleEl.textContent = '🏢 ' + bizName;
      const subEl = document.getElementById('business-detail-subtitle');
      if (subEl) subEl.textContent = `${this.periodLabel(this.businessPeriod)} progress across every branch of this business.`;
      const chartSub = document.getElementById('business-chart-subtitle');
      if (chartSub) chartSub.textContent = `Whole business sales trajectory - ${this.periodLabel(this.businessPeriod)}.`;

      this.setStat('biz-revenue', s.total_sales, true);
      this.setStat('biz-orders', s.transaction_count, false);
      this.setStat('biz-avg', s.avg_transaction, true);
      this.setStat('biz-units', s.products_sold, false);

      const branches = data.branches || [];
      const countEl = document.getElementById('business-branch-count');
      if (countEl) countEl.textContent = `${branches.length} Branch(es)`;

      if (container) {
        if (branches.length === 0) {
          container.innerHTML = '<div style="color: #64748B; font-size: 13px; padding: 16px; grid-column: 1 / -1; text-align: center;">No branches are linked to this business yet. Branch Managers can add branches in the StockEase app, then the owner assigns them to this business.</div>';
        } else {
          container.innerHTML = branches.map(b => `
            <div style="background: #F8FAFC; border: 1px solid #CBD5E1; border-radius: 10px; padding: 14px; cursor: pointer;"
                 onclick="app.openBranchDetail(${b.store_id}, 'business-detail')"
                 title="Click to open this branch progress page">
              <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 8px;">
                <div>
                  <div style="font-size: 15px; font-weight: 700; color: #0F172A;">📍 ${this.escape(b.store_name)}</div>
                  <div style="font-size: 11px; color: #64748B; margin-top: 2px;">${this.escape(b.location || 'Retail Location')}</div>
                </div>
                <span class="badge ${String(b.status).toLowerCase() === 'active' ? 'badge-success' : 'badge-danger'}" style="font-size: 10px;">${this.escape(b.status || 'Active')}</span>
              </div>
              <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-top: 12px; background: #FFFFFF; border: 1px solid #EEF2F6; border-radius: 6px; padding: 8px 10px; font-size: 11px;">
                <div><span style="color: #64748B;">Sales:</span><strong style="display: block; color: #2563EB;">₱${parseFloat(b.total_sales || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}</strong></div>
                <div><span style="color: #64748B;">Orders:</span><strong style="display: block; color: #0F172A;">${parseInt(b.transaction_count, 10) || 0}</strong></div>
                <div><span style="color: #64748B;">Share:</span><strong style="display: block; color: #059669;">${parseFloat(b.contribution_pct || 0)}%</strong></div>
              </div>
              <div style="font-size: 12px; color: #2563EB; font-weight: 600; margin-top: 10px;">Open branch page &rarr;</div>
            </div>
          `).join('');
        }
      }

      const chart = data.line_chart || [];
      this.renderProgressLineChart('chart-business-progress', chart, 'businessProgressChart', 'Business Sales (₱)', '#2563EB', 'rgba(37, 99, 235, 0.10)');
    } catch (err) {
      if (container) container.innerHTML = `<div style="color: #DC2626; font-size: 13px; padding: 16px; grid-column: 1 / -1; text-align: center;">Error: ${this.escape(err.message)}</div>`;
      this.showToast('Unable to load business progress: ' + err.message, 'error');
    }
  },

  openBranchDetail(storeId, returnTab) {
    this.currentBranchId = storeId;
    this.branchReturnTab = returnTab || this.activeTab || 'dashboard';
    this.setPeriodButtons('.btn-branch-period', 'data-brperiod', 'today');
    this.branchPeriod = 'today';
    this.showTab('branch-detail');
  },

  async loadBranchDetail() {
    if (!this.currentBranchId) return;
    const prodBody = document.getElementById('tbody-branch-top-products');
    if (prodBody) prodBody.innerHTML = '<tr><td colspan="4" style="text-align: center; color: #64748B; padding: 20px;">Loading top products...</td></tr>';

    try {
      const url = `../stockease_api/get_business_progress.php?store_id=${this.currentBranchId}&period=${encodeURIComponent(this.branchPeriod)}`;
      const res = await fetch(url, { credentials: 'include' });
      const data = await res.json();
      if (!data.success) throw new Error(data.message || 'Unable to load branch progress');

      this.currentBranchData = data;
      const store = data.store || {};
      const branchName = store.store_name || this.getStoreNameById(this.currentBranchId);
      const s = data.summary || {};

      const backBtn = document.getElementById('btn-branch-detail-back');
      if (backBtn) backBtn.innerHTML = '&larr; ' + (this.branchReturnTab === 'business-detail' ? 'Back to Business' : 'Back to Overview');

      const titleEl = document.getElementById('branch-detail-title');
      if (titleEl) titleEl.textContent = '📍 ' + branchName;
      const subEl = document.getElementById('branch-detail-subtitle');
      if (subEl) subEl.textContent = [store.location, this.periodLabel(this.branchPeriod)].filter(Boolean).join(' • ');
      const chartSub = document.getElementById('branch-chart-subtitle');
      if (chartSub) chartSub.textContent = `Branch sales trajectory - ${this.periodLabel(this.branchPeriod)}.`;

      this.setStat('br-revenue', s.total_sales, true);
      this.setStat('br-orders', s.transaction_count, false);
      this.setStat('br-avg', s.avg_transaction, true);
      this.setStat('br-units', s.products_sold, false);

      // Top selling products
      const products = data.ranked_products || [];
      if (prodBody) {
        if (products.length === 0) {
          prodBody.innerHTML = '<tr><td colspan="4" style="text-align: center; color: #64748B; padding: 20px;">No sales recorded in this period.</td></tr>';
        } else {
          prodBody.innerHTML = products.slice(0, 20).map((p, idx) => `
            <tr>
              <td style="color: #64748B;">#${idx + 1}</td>
              <td style="font-weight: 600;">${this.escape(p.product_name)}<div style="font-size: 10px; color: #94A3B8;">${this.escape(p.category_name || '')}</div></td>
              <td style="text-align: right; font-weight: 700;">${parseInt(p.total_sold_qty, 10) || 0}</td>
              <td style="text-align: right; color: #059669; font-weight: 700;">₱${parseFloat(p.total_revenue || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
            </tr>
          `).join('');
        }
      }

      // Reset the accounts card to its collapsed state for the new branch
      const accountsBox = document.getElementById('container-branch-accounts');
      if (accountsBox) {
        accountsBox.style.display = 'none';
        accountsBox.dataset.loaded = '1';
        accountsBox.innerHTML = '<div style="color: #64748B; font-size: 13px;">Loading accounts...</div>';
      }
      const toggle = document.getElementById('branch-accounts-toggle');
      if (toggle) toggle.textContent = 'Show Accounts';
      this.loadBranchAccounts();

      const chart = data.line_chart || [];
      this.renderProgressLineChart('chart-branch-progress', chart, 'branchProgressChart', 'Branch Sales (₱)', '#059669', 'rgba(5, 150, 105, 0.10)');
    } catch (err) {
      this.showToast('Unable to load branch progress: ' + err.message, 'error');
    }
  },

  async loadBranchAccounts() {
    const box = document.getElementById('container-branch-accounts');
    if (!box || !this.currentBranchId) return;

    try {
      const res = await fetch('../stockease_api/get_users.php', { credentials: 'include' });
      const data = await res.json();
      if (!data.success) throw new Error(data.message || 'Unable to load accounts');

      const accounts = (data.users || []).filter(u =>
        u.role !== 'super_admin' && parseInt(u.store_id, 10) === parseInt(this.currentBranchId, 10)
      );

      if (accounts.length === 0) {
        box.innerHTML = '<div style="color: #64748B; font-size: 13px; padding: 12px 0;">No accounts are currently assigned to this branch.</div>';
        return;
      }

      box.innerHTML = `
        <div style="font-size: 12px; color: #64748B; margin-bottom: 10px;">${accounts.length} account(s) assigned to this branch.</div>
        <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 12px;">
          ${accounts.map(u => `
            <div style="background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 10px; padding: 12px; display: flex; gap: 10px; align-items: center;">
              <div style="width: 36px; height: 36px; border-radius: 50%; background: #2563EB; color: #FFF; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; flex-shrink: 0;">
                ${this.escape((u.full_name || 'U').substring(0, 2).toUpperCase())}
              </div>
              <div style="min-width: 0;">
                <div style="font-size: 13px; font-weight: 700; color: #0F172A;">${this.escape(u.full_name || '—')}</div>
                <div style="font-size: 11px; color: #64748B;">@${this.escape(u.username)}${u.email ? ' • ' + this.escape(u.email) : ''}</div>
                <div style="margin-top: 6px; display: flex; gap: 6px; align-items: center;">
                  <span class="badge ${this.roleBadgeClass(u.role)}" style="font-size: 10px;">${this.escape(this.roleLabel(u.role))}</span>
                  <span class="badge ${u.is_active ? 'badge-success' : 'badge-danger'}" style="font-size: 10px;">${u.is_active ? 'Active' : 'Inactive'}</span>
                </div>
              </div>
            </div>
          `).join('')}
        </div>
      `;
    } catch (err) {
      box.innerHTML = `<div style="color: #DC2626; font-size: 13px; padding: 12px 0;">Error: ${this.escape(err.message)}</div>`;
    }
  },

  toggleBranchAccounts() {
    const box = document.getElementById('container-branch-accounts');
    if (!box) return;
    const opening = box.style.display === 'none' || !box.style.display;
    box.style.display = opening ? 'block' : 'none';
    const toggle = document.getElementById('branch-accounts-toggle');
    if (toggle) toggle.textContent = opening ? 'Hide Accounts' : 'Show Accounts';
    if (opening && !box.dataset.loaded) {
      box.dataset.loaded = '1';
      this.loadBranchAccounts();
    }
  },

  setStat(id, value, asCurrency) {
    const el = document.getElementById(id);
    if (!el) return;
    const num = parseFloat(value || 0);
    el.textContent = asCurrency
      ? '₱' + num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
      : num.toLocaleString('en-US');
  },

  renderProgressLineChart(canvasId, series, chartKey, datasetLabel, borderColor, fillColor) {
    const canvas = document.getElementById(canvasId);
    if (!canvas || typeof Chart === 'undefined') return;

    const labels = (series || []).map(p => p.label || p.date || '');
    const values = (series || []).map(p => parseFloat(p.sales || 0));

    if (this[chartKey]) {
      this[chartKey].destroy();
      this[chartKey] = null;
    }

    if (labels.length === 0) {
      labels.push('No data');
      values.push(0);
    }

    this[chartKey] = new Chart(canvas, {
      type: 'line',
      data: {
        labels: labels,
        datasets: [{
          label: datasetLabel,
          data: values,
          borderColor: borderColor,
          backgroundColor: fillColor,
          fill: true,
          tension: 0.35,
          borderWidth: 2,
          pointRadius: 3,
          pointBackgroundColor: borderColor
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          y: { beginAtZero: true, grid: { color: '#F1F5F9' }, ticks: { font: { size: 10 }, callback: v => '₱' + v.toLocaleString() } },
          x: { grid: { display: false }, ticks: { font: { size: 10 } } }
        }
      }
    });
  },

  exportChartSeriesToExcel(canvasId, filename) {
    const chart = this.getChartInstance(canvasId);
    if (!chart) {
      this.showToast('Chart is not ready for export yet', 'error');
      return;
    }
    const labels = chart.data.labels || [];
    const values = (chart.data.datasets && chart.data.datasets[0] ? chart.data.datasets[0].data : []) || [];
    const label = (chart.data.datasets && chart.data.datasets[0] ? chart.data.datasets[0].label : 'Value') || 'Value';

    let csv = 'Period,' + label + '\r\n';
    labels.forEach((l, i) => {
      csv += `"${String(l).replace(/"/g, '""')}",${values[i]}\r\n`;
    });
    this.downloadTextCsv(csv, filename);
  },

  exportBusinessProgressExcel() {
    const d = this.currentBusinessData;
    if (!d) {
      this.showToast('No business data to export yet', 'error');
      return;
    }
    const s = d.summary || {};
    const period = this.periodLabel(this.businessPeriod);

    let csv = 'StockEase Business Progress Report\r\n';
    csv += `Business,"${d.business ? d.business.business_name : ''}"\r\n`;
    csv += `Period,"${period}"\r\n\r\n`;
    csv += 'Summary\r\nMetric,Value\r\n';
    csv += `Revenue,${s.total_sales || 0}\r\n`;
    csv += `Orders,${s.transaction_count || 0}\r\n`;
    csv += `Average Order,${s.avg_transaction || 0}\r\n`;
    csv += `Units Sold,${s.products_sold || 0}\r\n\r\n`;
    csv += 'Progress Line Chart\r\nPeriod,Sales,Transactions\r\n';
    (d.line_chart || []).forEach(p => {
      csv += `"${p.label || p.date || ''}",${parseFloat(p.sales || 0)},${parseInt(p.tx_count, 10) || 0}\r\n`;
    });
    csv += '\r\nBranches\r\nBranch,Location,Status,Sales,Orders,Contribution %\r\n';
    (d.branches || []).forEach(b => {
      csv += `"${String(b.store_name || '').replace(/"/g, '""')}","${String(b.location || '').replace(/"/g, '""')}",${b.status || ''},${parseFloat(b.total_sales || 0)},${parseInt(b.transaction_count, 10) || 0},${parseFloat(b.contribution_pct || 0)}\r\n`;
    });

    this.downloadTextCsv(csv, 'stockease_business_progress_' + new Date().toISOString().split('T')[0] + '.csv');
  },

  exportBranchProgressExcel() {
    const d = this.currentBranchData;
    if (!d) {
      this.showToast('No branch data to export yet', 'error');
      return;
    }
    const s = d.summary || {};
    const store = d.store || {};

    let csv = 'StockEase Branch Progress Report\r\n';
    csv += `Branch,"${store.store_name || ''}"\r\n`;
    csv += `Location,"${store.location || ''}"\r\n`;
    csv += `Period,"${this.periodLabel(this.branchPeriod)}"\r\n\r\n`;
    csv += 'Summary\r\nMetric,Value\r\n';
    csv += `Revenue,${s.total_sales || 0}\r\n`;
    csv += `Orders,${s.transaction_count || 0}\r\n`;
    csv += `Average Order,${s.avg_transaction || 0}\r\n`;
    csv += `Units Sold,${s.products_sold || 0}\r\n\r\n`;
    csv += 'Progress Line Chart\r\nPeriod,Sales,Transactions\r\n';
    (d.line_chart || []).forEach(p => {
      csv += `"${p.label || p.date || ''}",${parseFloat(p.sales || 0)},${parseInt(p.tx_count, 10) || 0}\r\n`;
    });
    csv += '\r\nTop Selling Products\r\nProduct,Category,Units Sold,Revenue,Net Profit\r\n';
    (d.ranked_products || []).forEach(p => {
      csv += `"${String(p.product_name || '').replace(/"/g, '""')}","${String(p.category_name || '').replace(/"/g, '""')}",${parseInt(p.total_sold_qty, 10) || 0},${parseFloat(p.total_revenue || 0)},${parseFloat(p.total_net_profit || 0)}\r\n`;
    });

    this.downloadTextCsv(csv, 'stockease_branch_progress_' + new Date().toISOString().split('T')[0] + '.csv');
  },

  downloadTextCsv(csv, filename) {
    const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    this.showToast('Excel-ready CSV exported: ' + filename, 'success');
  },

  async setupBusinessOwnerSelect() {
    const select = document.getElementById('business-owner-select');
    if (!select) return;
    const currentVal = select.value;
    let owners = [];

    try {
      const res = await fetch('../stockease_api/get_store_owners.php', { credentials: 'include' });
      const data = await res.json();
      if (data.success && Array.isArray(data.owners)) owners = data.owners;
    } catch (e) { /* leave empty list */ }

    select.innerHTML = '<option value="">-- No Owner (Assign Later) --</option>' +
      '<option value="__new__">+ Create New Store Owner Account</option>' +
      owners
        .filter(o => o.owner_id)
        .map(o => `<option value="${o.owner_id}">${this.escape(o.owner_name)} (ID #${o.owner_id})</option>`)
        .join('');
    if (currentVal) select.value = currentVal;
    this.toggleNewBusinessOwnerFields();
  },

  toggleNewBusinessOwnerFields() {
    const sel = document.getElementById('business-owner-select');
    const f = document.getElementById('new-business-owner-fields');
    if (f) f.style.display = (sel && sel.value === '__new__') ? 'block' : 'none';
  },

  openModal(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.add('open');
  },

  closeModals() {
    document.querySelectorAll('.modal-overlay').forEach(m => m.classList.remove('open'));
  },

  showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = 'toast toast-' + type;
    toast.textContent = message;
    container.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transition = 'opacity 0.3s ease';
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  },

  // Form Submissions
  setupFormSubmissions() {
    // Add Business Form
    const formAddBusiness = document.getElementById('form-add-business');
    if (formAddBusiness) {
      formAddBusiness.addEventListener('submit', async (e) => {
        e.preventDefault();
        const businessName = document.getElementById('new-business-name').value.trim();
        const ownerId = document.getElementById('business-owner-select')?.value || '';

        const payload = { business_name: businessName };
        if (ownerId === '__new__') {
          payload.new_owner_name = document.getElementById('new-business-owner-fullname')?.value.trim();
          payload.new_owner_username = document.getElementById('new-business-owner-username')?.value.trim();
          payload.new_owner_email = document.getElementById('new-business-owner-email')?.value.trim();
          payload.new_owner_password = document.getElementById('new-business-owner-password')?.value.trim();
          // Same rule the API enforces - fail early with a friendly hint
          if (payload.new_owner_password && !this.passwordIsAcceptable(payload.new_owner_password)) {
            this.showToast(this.passwordRequirementText(), 'error');
            return;
          }
        } else if (ownerId) {
          payload.owner_id = parseInt(ownerId, 10);
        }

        try {
          const res = await fetch('../stockease_api/manage_businesses.php?action=add', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify(payload)
          });
          const data = await res.json();
          if (data.success) {
            this.showToast(data.message || 'Business registered successfully!', 'success');
            this.closeModals();
            formAddBusiness.reset();
            const ownerFields = document.getElementById('new-business-owner-fields');
            if (ownerFields) ownerFields.style.display = 'none';
            this.loadBusinesses();
            this.loadBusinessCards();
          } else {
            this.showToast(data.message || 'Failed to register business', 'error');
          }
        } catch (err) {
          this.showToast('Network error registering business', 'error');
        }
      });
    }

    // Rename Business Form
    const formRenameBusiness = document.getElementById('form-rename-business');
    if (formRenameBusiness) {
      formRenameBusiness.addEventListener('submit', async (e) => {
        e.preventDefault();
        const businessId = parseInt(document.getElementById('rename-business-id').value, 10);
        const businessName = document.getElementById('rename-business-name').value.trim();
        if (!businessId || !businessName) return;

        try {
          const res = await fetch('../stockease_api/manage_businesses.php?action=update', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ business_id: businessId, business_name: businessName })
          });
          const data = await res.json();
          if (data.success) {
            this.showToast(data.message || 'Business name updated!', 'success');
            this.closeModals();
            this.loadBusinesses();
            this.loadBusinessCards();
          } else {
            this.showToast(data.message || 'Failed to rename business', 'error');
          }
        } catch (err) {
          this.showToast('Network error renaming business', 'error');
        }
      });
    }

    // Edit Store Description Form
    const formEditDesc = document.getElementById('form-edit-description');
    if (formEditDesc) {
      formEditDesc.addEventListener('submit', async (e) => {
        e.preventDefault();
        const storeId = document.getElementById('desc-store-id')?.value;
        const desc = document.getElementById('desc-store-text')?.value.trim();
        if (!storeId) return;

        try {
          const res = await fetch('../stockease_api/manage_stores.php?action=update_description', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ store_id: parseInt(storeId, 10), description: desc })
          });
          const data = await res.json();
          if (data.success) {
            this.showToast('Store description updated!', 'success');
            this.closeModals();
            this.loadStores();
          } else {
            this.showToast(data.message || 'Failed to update description', 'error');
          }
        } catch (e) {
          this.showToast('Network error updating description', 'error');
        }
      });
    }

    // Add Store Admin Form
    const formAddAdmin = document.getElementById('form-add-admin');
    if (formAddAdmin) {
      formAddAdmin.addEventListener('submit', async (e) => {
        e.preventDefault();
        const fullName = document.getElementById('admin-fullname').value.trim();
        const username = document.getElementById('admin-username').value.trim();
        const email = document.getElementById('admin-email').value.trim();
        const password = document.getElementById('admin-password').value;
        const storeId = parseInt(document.getElementById('admin-store-select').value, 10);
        const role = (document.getElementById('admin-role-select')?.value) || 'store_admin';

        // Same rule the API enforces - fail early with a friendly hint
        if (!this.passwordIsAcceptable(password)) {
          this.showToast(this.passwordRequirementText(), 'error');
          return;
        }

        try {
          const res = await fetch('../stockease_api/manage_store_admins.php?action=add', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ full_name: fullName, username, email, password, store_id: storeId, role })
          });
          const data = await res.json();
          if (data.success) {
            this.showToast(data.message || 'Account registered and credentials dispatched!', 'success');
            this.closeModals();
            formAddAdmin.reset();
            this.loadStoreAdmins();
          } else {
            this.showToast(data.message || 'Failed to create store admin', 'error');
          }
        } catch (err) {
          this.showToast('Network error creating store admin', 'error');
        }
      });
    }

    // New Transfer Form
    const formNewTransfer = document.getElementById('form-new-transfer');
    if (formNewTransfer) {
      formNewTransfer.addEventListener('submit', async (e) => {
        e.preventDefault();
        const fromStoreId = parseInt(document.getElementById('transfer-from-store').value, 10);
        const toStoreId = parseInt(document.getElementById('transfer-to-store').value, 10);
        const productId = parseInt(document.getElementById('transfer-product-select').value, 10);
        const quantity = parseInt(document.getElementById('transfer-quantity').value, 10);
        const notes = document.getElementById('transfer-notes').value.trim();

        if (fromStoreId === toStoreId) {
          this.showToast('Source and destination stores cannot be the same', 'error');
          return;
        }

        try {
          const res = await fetch('../stockease_api/manage_transfers.php?action=create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ from_store_id: fromStoreId, to_store_id: toStoreId, product_id: productId, quantity, notes })
          });
          const data = await res.json();
          if (data.success) {
            this.showToast('Stock transfer created successfully!', 'success');
            this.closeModals();
            formNewTransfer.reset();
            this.loadTransfers();
          } else {
            this.showToast(data.message || 'Failed to create transfer', 'error');
          }
        } catch (err) {
          this.showToast('Network error creating transfer', 'error');
        }
      });
    }

    // Quick Direct Move Transfer Form
    const formQuickTransfer = document.getElementById('form-quick-transfer');
    if (formQuickTransfer) {
      formQuickTransfer.addEventListener('submit', async (e) => {
        e.preventDefault();
        const fromStoreId = parseInt(document.getElementById('quick-from-store').value, 10);
        const toStoreId = parseInt(document.getElementById('quick-to-store').value, 10);
        const productId = parseInt(document.getElementById('quick-product-select').value, 10);
        const quantity = parseInt(document.getElementById('quick-quantity').value, 10);
        const notes = document.getElementById('quick-notes').value.trim();

        if (fromStoreId === toStoreId) {
          this.showToast('Source and destination stores cannot be the same', 'error');
          return;
        }

        try {
          const res = await fetch('../stockease_api/manage_transfers.php?action=quick_transfer', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ from_store_id: fromStoreId, to_store_id: toStoreId, product_id: productId, quantity, notes })
          });
          const data = await res.json();
          if (data.success) {
            this.showToast(data.message || 'Direct quick transfer completed!', 'success');
            this.closeModals();
            formQuickTransfer.reset();
            this.loadTransfers();
            this.loadDashboardData();
          } else {
            this.showToast(data.message || 'Failed to execute quick transfer', 'error');
          }
        } catch (err) {
          this.showToast('Network error executing quick transfer', 'error');
        }
      });
    }
  },

  // -------------------------------------------------------------
  // Data Loaders
  // -------------------------------------------------------------

  async loadSubscription() {
    try {
      const res = await fetch('../stockease_api/manage_subscription.php?action=fetch', { credentials: 'include' });
      const data = await res.json();
      if (data.success && data.subscription) {
        this.currentSubscription = data.subscription;
        const sub = data.subscription;
        const isPrem = !!sub.is_premium;

        // Update subscription tab elements
        const subBadge = document.getElementById('sub-active-badge');
        const subTitle = document.getElementById('sub-active-title');
        const subDesc = document.getElementById('sub-active-desc');
        const subExp = document.getElementById('sub-expires-display');

        if (subBadge) subBadge.textContent = sub.display_plan || 'StockEase Free';
        if (subTitle) subTitle.textContent = (sub.display_plan || 'StockEase Free') + ' • ' + (sub.status || 'ACTIVE').toUpperCase();
        if (subDesc) {
          subDesc.textContent = isPrem 
            ? 'Active enterprise subscription with multi-store transfers and velocity intelligence enabled.'
            : 'Standard freemium tier with core POS and store inventory management.';
        }
        if (subExp) {
          subExp.textContent = sub.expires_at ? 'Renews/Expires: ' + sub.expires_at : 'No expiration (Standard Free)';
        }

        // Feature banners
        const trBanner = document.getElementById('premium-transfer-banner');
        if (trBanner) trBanner.style.display = isPrem ? 'none' : 'block';

        const insBanner = document.getElementById('premium-insights-banner');
        if (insBanner) insBanner.style.display = isPrem ? 'none' : 'block';

        // Pending subscription requests (for Super Admin review queue)
        const pendingList = data.pending_requests || [];
        const pendingCount = pendingList.length;

        // Header notification pill
        const notifPill = document.getElementById('sub-pending-notification');
        const notifCount = document.getElementById('sub-pending-count');
        if (notifPill && notifCount) {
          if (pendingCount > 0) {
            notifCount.textContent = pendingCount;
            notifPill.style.display = 'inline-block';
          } else {
            notifPill.style.display = 'none';
          }
        }

        // Pending count badge in subscription tab
        const subBadgeCount = document.getElementById('badge-pending-sub-count');
        if (subBadgeCount) {
          subBadgeCount.textContent = `${pendingCount} Pending`;
          subBadgeCount.className = `badge ${pendingCount > 0 ? 'badge-warning' : 'badge-slate'}`;
        }

        // Render pending subscriptions table
        const tbodyPending = document.getElementById('tbody-pending-subscriptions');
        if (tbodyPending) {
          if (pendingCount === 0) {
            tbodyPending.innerHTML = '<tr><td colspan="7" style="text-align: center; color: #059669; padding: 20px; font-weight: 600;">&check; All store admin subscription applications have been reviewed. None pending.</td></tr>';
          } else {
            tbodyPending.innerHTML = pendingList.map(req => {
              const planLabel = req.billing_period === 'yearly' ? 'Premium (Yearly)' : 'Premium (Monthly)';
              const rate = req.billing_period === 'yearly' ? '₱6,990 / yr' : '₱699 / mo';
              return `
                <tr>
                  <td style="font-weight: 700; color: #64748B;">#${req.id}</td>
                  <td style="font-weight: 600;">
                    <div>${this.escape(req.user_name)}</div>
                    <div style="color: #64748B; font-size: 11px;">@${this.escape(req.username)}</div>
                  </td>
                  <td>${this.escape(req.store_name)}</td>
                  <td><span class="badge badge-info">${planLabel}</span></td>
                  <td style="font-weight: 600; color: #0F172A;">${rate}</td>
                  <td style="color: #64748B; font-size: 11px;">${this.escape(req.created_at || '—')}</td>
                  <td style="text-align: right; white-space: nowrap;">
                    <button class="btn btn-sm" style="background-color: #059669; color: #FFF; border-color: #059669;" onclick="app.approveSubscriptionRequest(${req.id}, '${this.escape(req.user_name)}', '${planLabel}')">
                      &check; Approve &amp; Activate
                    </button>
                  </td>
                </tr>
              `;
            }).join('');
          }
        }
      }
    } catch (err) {
      console.warn('Unable to load subscription details', err);
    }
  },

  async approveSubscriptionRequest(requestId, adminName, planLabel) {
    if (!confirm(`Approve subscription application #${requestId} for ${adminName} (${planLabel})?\n\nThis will immediately unlock Premium features (Stock Transfers, Velocity Intelligence, Cross-Store Rebalancing) for the duration of the plan.`)) {
      return;
    }

    try {
      const res = await fetch('../stockease_api/manage_subscription.php?action=approve_request', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ request_id: requestId })
      });
      const data = await res.json();
      if (data.success) {
        this.showToast(data.message || 'Subscription approved successfully!', 'success');
        await this.loadSubscription();
        this.refreshCurrentTab();
      } else {
        this.showToast(data.message || 'Failed to approve subscription', 'error');
      }
    } catch (e) {
      this.showToast('Network error approving subscription', 'error');
    }
  },

  async rejectSubscriptionRequest(requestId, adminName) {
    const reason = prompt(`Decline subscription application #${requestId} for ${adminName}?\n\nEnter reason for declining:`, 'Payment verification pending / Contact super admin');
    if (reason === null) return;

    try {
      const res = await fetch('../stockease_api/manage_subscription.php?action=reject_request', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ request_id: requestId, reason })
      });
      const data = await res.json();
      if (data.success) {
        this.showToast(data.message || 'Subscription request declined.', 'info');
        await this.loadSubscription();
      } else {
        this.showToast(data.message || 'Failed to decline request', 'error');
      }
    } catch (e) {
      this.showToast('Network error declining request', 'error');
    }
  },

  async loadDashboardData() {
    const tbody = document.getElementById('tbody-store-performance');
    if (tbody) tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: #64748B; padding: 24px;">Loading store metrics...</td></tr>';

    try {
      // 1. Fetch stores
      const storeRes = await fetch('../stockease_api/manage_stores.php?action=fetch', { credentials: 'include' });
      const storeData = await storeRes.json();
      if (!storeData.success) throw new Error(storeData.message);

      this.stores = storeData.stores || [];
      const kpiStores = document.getElementById('kpi-total-stores');
      if (kpiStores) kpiStores.textContent = this.stores.length;

      // Populate store dropdowns
      this.populateStoreSelect('reports-store-select');

      // 2. Fetch users
      const usersRes = await fetch('../stockease_api/get_users.php', { credentials: 'include' });
      const usersData = await usersRes.json();
      let totalUsersCount = 0;
      const storeStaffCount = {};

      if (usersData.success && usersData.users) {
        this.users = usersData.users;
        totalUsersCount = usersData.users.length;
        usersData.users.forEach(u => {
          if (u.role === 'staff' && u.store_id) {
            storeStaffCount[u.store_id] = (storeStaffCount[u.store_id] || 0) + 1;
          }
        });
      }
      const kpiUsers = document.getElementById('kpi-total-users');
      if (kpiUsers) kpiUsers.textContent = totalUsersCount;

      // 3. For each store, fetch today's sales
      const today = new Date().toISOString().split('T')[0];
      let systemSales = 0;
      let systemOrders = 0;
      const perfList = [];

      for (const store of this.stores) {
        let storeSales = 0;
        let storeOrders = 0;

        try {
          const txRes = await fetch(`../stockease_api/get_transactions.php?store_id=${store.id}&start_date=${today}&end_date=${today}`, { credentials: 'include' });
          const txList = await txRes.json();
          if (Array.isArray(txList)) {
            txList.forEach(t => {
              if (t.status !== 'voided') {
                storeOrders++;
                storeSales += parseFloat(t.total_amount || 0);
              }
            });
          }
        } catch (e) {}

        systemSales += storeSales;
        systemOrders += storeOrders;

        perfList.push({
          id: store.id,
          name: store.store_name,
          location: store.location || 'Branch ' + store.id,
          sales: storeSales,
          orders: storeOrders,
          staffCount: storeStaffCount[store.id] || 0,
          status: store.status || 'Active'
        });
      }

      this.currentPerformanceList = perfList;

      // Check for daily anomalies or low stock to alert user immediately
      const alertBanner = document.getElementById('live-system-alert-banner');
      if (alertBanner) {
        let alertsCount = 0;
        let alertMsg = [];
        this.stores.forEach(s => {
          if (s.status && s.status.toLowerCase() !== 'active') {
            alertsCount++;
            alertMsg.push(`Branch "${s.store_name}" is currently marked inactive.`);
          }
        });
        if (alertsCount > 0) {
          alertBanner.style.display = 'block';
          const alertDesc = document.getElementById('live-alert-message');
          if (alertDesc) alertDesc.textContent = alertMsg.join(' ');
        } else {
          alertBanner.style.display = 'none';
        }
      }

      // Update KPI Cards
      const kpiSales = document.getElementById('kpi-today-sales');
      const kpiTx = document.getElementById('kpi-today-tx');
      if (kpiSales) kpiSales.textContent = '₱' + systemSales.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
      if (kpiTx) kpiTx.textContent = systemOrders;

      // Render Store Performance Table
      if (tbody) {
        if (perfList.length === 0) {
          tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: #64748B; padding: 20px;">No store branches registered.</td></tr>';
        } else {
          tbody.innerHTML = perfList.map(item => `
            <tr>
              <td style="font-weight: 600;">${this.escape(item.name)}</td>
              <td style="color: #64748B;">${this.escape(item.location)}</td>
              <td style="text-align: right; font-weight: 700;">₱${item.sales.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
              <td style="text-align: right; color: #64748B;">${item.orders}</td>
              <td style="text-align: right; color: #64748B;">${item.staffCount}</td>
              <td style="text-align: center;">
                <span class="badge ${item.status.toLowerCase() === 'active' ? 'badge-success' : 'badge-danger'}">${this.escape(item.status)}</span>
              </td>
              <td style="text-align: center;">
                <button class="btn btn-outline-primary btn-sm" onclick="app.viewStoreReports(${item.id})">Branch Report</button>
              </td>
            </tr>
          `).join('');
        }
      }

      // Load business cards
      this.loadBusinessCards();

    } catch (err) {
      console.error(err);
      if (tbody) tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #DC2626; padding: 20px;">Error loading store metrics: ${err.message}</td></tr>`;
    }
  },

  openEditDescription(storeId, storeName, currentDesc) {
    const storeIdInput = document.getElementById('desc-store-id');
    const label = document.getElementById('desc-store-name-label');
    const text = document.getElementById('desc-store-text');
    if (storeIdInput) storeIdInput.value = storeId;
    if (label) label.textContent = `Store Description for "${storeName}":`;
    if (text) text.value = currentDesc || '';
    this.openModal('modal-edit-description');
  },


  viewStoreReports(storeId) {
    this.currentReportStoreId = storeId;
    const select = document.getElementById('reports-store-select');
    if (select) select.value = storeId;
    this.showTab('reports');
  },

  async loadStores() {
    const tbody = document.getElementById('tbody-stores-list');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: #64748B; padding: 24px;">Loading store branches...</td></tr>';

    try {
      const res = await fetch('../stockease_api/manage_stores.php?action=fetch', { credentials: 'include' });
      const data = await res.json();
      if (!data.success) throw new Error(data.message);

      this.stores = data.stores || [];
      if (this.stores.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: #64748B; padding: 20px;">No stores found. Branches are added by the branch Manager in the StockEase app.</td></tr>';
        return;
      }

      tbody.innerHTML = this.stores.map(s => `
        <tr>
          <td style="font-weight: 600; color: #64748B;">#${s.id}</td>
          <td style="font-weight: 600;">${this.escape(s.store_name)}</td>
          <td style="color: #64748B;">${this.escape(s.location || '—')}</td>
          <td>
            ${s.owner_name 
              ? `<span class="badge badge-info" title="User ID #${s.owner_id}">👤 ${this.escape(s.owner_name)}</span>` 
              : `<span style="color: #94A3B8; font-size: 12px; font-style: italic;">Independent</span>`}
          </td>
          <td>${s.admin_count || 0} Manager(s)</td>
          <td style="text-align: center;">
            <span class="badge ${s.status && s.status.toLowerCase() === 'active' ? 'badge-success' : 'badge-danger'}">${this.escape(s.status || 'Active')}</span>
          </td>
          <td style="text-align: right;">
            <button class="btn btn-secondary btn-sm" onclick="app.viewStoreReports(${s.id})">Reports</button>
          </td>
        </tr>
      `).join('');
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #DC2626; padding: 20px;">Error: ${err.message}</td></tr>`;
    }
  },


  async loadTransfers() {
    const tbody = document.getElementById('tbody-transfers-list');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: #64748B; padding: 24px;">Loading transfers...</td></tr>';

    const statusFilter = document.getElementById('filter-transfer-status')?.value || '';
    let url = '../stockease_api/manage_transfers.php?action=list';
    if (statusFilter) url += '&status=' + encodeURIComponent(statusFilter);

    try {
      const res = await fetch(url, { credentials: 'include' });
      const data = await res.json();

      if (!data.success) {
        if (data.code === 'PREMIUM_REQUIRED') {
          tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: #92400E; padding: 24px;">Stock Transfers require StockEase Premium. Upgrade in the Subscription tab.</td></tr>';
          return;
        }
        throw new Error(data.message);
      }

      const transfers = data.transfers || [];
      if (transfers.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: #64748B; padding: 20px;">No stock transfers recorded.</td></tr>';
        return;
      }

      tbody.innerHTML = transfers.map(t => {
        let badgeClass = 'badge-slate';
        if (t.status === 'requested') badgeClass = 'badge-warning';
        else if (t.status === 'in_transit') badgeClass = 'badge-info';
        else if (t.status === 'completed') badgeClass = 'badge-success';
        else if (t.status === 'cancelled') badgeClass = 'badge-danger';

        let controls = '';
        if (t.status === 'requested') {
          controls = `
            <button class="btn btn-primary btn-sm" onclick="app.dispatchTransfer(${t.id})">Dispatch</button>
            <button class="btn btn-secondary btn-sm" onclick="app.cancelTransfer(${t.id})">Cancel</button>
          `;
        } else if (t.status === 'in_transit') {
          controls = `
            <button class="btn btn-sm" style="background-color: #059669; color: #fff;" onclick="app.completeTransfer(${t.id})">Confirm Delivery</button>
            <button class="btn btn-secondary btn-sm" onclick="app.cancelTransfer(${t.id})">Cancel</button>
          `;
        } else {
          controls = `<span style="color: #94A3B8; font-size: 11px;">Completed/Closed</span>`;
        }

        return `
          <tr>
            <td style="font-weight: 700;">#${t.id}</td>
            <td>
              <span style="font-weight: 600;">${this.escape(t.from_store_name)}</span>
              <span style="color: #64748B; margin: 0 4px;">&rarr;</span>
              <span style="font-weight: 600;">${this.escape(t.to_store_name)}</span>
            </td>
            <td>${t.total_items} items (${t.total_quantity} units)</td>
            <td style="color: #64748B;">${this.escape(t.requested_by_name || 'Admin')}</td>
            <td style="color: #64748B;">${t.created_at || '—'}</td>
            <td style="text-align: center;">
              <span class="badge ${badgeClass}">${t.status.toUpperCase()}</span>
            </td>
            <td style="text-align: right;">
              <div style="display: flex; gap: 6px; justify-content: flex-end;">${controls}</div>
            </td>
          </tr>
        `;
      }).join('');

    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #DC2626; padding: 20px;">Error: ${err.message}</td></tr>`;
    }
  },

  async dispatchTransfer(transferId) {
    if (!confirm(`Dispatch Stock Transfer #${transferId}? Source store inventory will be deducted immediately.`)) return;

    try {
      const res = await fetch('../stockease_api/manage_transfers.php?action=dispatch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ transfer_id: transferId })
      });
      const data = await res.json();
      if (data.success) {
        this.showToast(`Transfer #${transferId} dispatched to In Transit!`, 'success');
        this.loadTransfers();
      } else {
        this.showToast(data.message || 'Dispatch failed', 'error');
      }
    } catch (err) {
      this.showToast('Network error during dispatch', 'error');
    }
  },

  async completeTransfer(transferId) {
    if (!confirm(`Confirm delivery receipt for Transfer #${transferId}? Destination store inventory will be replenished immediately.`)) return;

    try {
      const res = await fetch('../stockease_api/manage_transfers.php?action=complete', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ transfer_id: transferId })
      });
      const data = await res.json();
      if (data.success) {
        this.showToast(`Transfer #${transferId} confirmed and completed!`, 'success');
        this.loadTransfers();
      } else {
        this.showToast(data.message || 'Confirmation failed', 'error');
      }
    } catch (err) {
      this.showToast('Network error confirming delivery', 'error');
    }
  },

  async cancelTransfer(transferId) {
    const reason = prompt(`Enter cancellation reason for Transfer #${transferId}:`, 'Operations adjustment');
    if (reason === null) return;

    try {
      const res = await fetch('../stockease_api/manage_transfers.php?action=cancel', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ transfer_id: transferId, reason })
      });
      const data = await res.json();
      if (data.success) {
        this.showToast(`Transfer #${transferId} cancelled and refunded.`, 'info');
        this.loadTransfers();
      } else {
        this.showToast(data.message || 'Cancellation failed', 'error');
      }
    } catch (err) {
      this.showToast('Network error cancelling transfer', 'error');
    }
  },

  async setupNewTransferModal() {
    this.populateStoreSelect('transfer-from-store');
    this.populateStoreSelect('transfer-to-store');

    const fromStore = document.getElementById('transfer-from-store');
    const prodSelect = document.getElementById('transfer-product-select');
    const hint = document.getElementById('transfer-stock-hint');

    if (fromStore && prodSelect) {
      fromStore.onchange = async () => {
        const storeId = fromStore.value;
        prodSelect.innerHTML = '<option value="">Loading store catalog...</option>';
        prodSelect.disabled = true;
        if (hint) hint.textContent = '';

        if (!storeId) {
          prodSelect.innerHTML = '<option value="">Select source store first...</option>';
          return;
        }

        try {
          const res = await fetch(`../stockease_api/get_products.php?store_id=${storeId}`, { credentials: 'include' });
          const data = await res.json();
          if (data.success && data.products) {
            prodSelect.innerHTML = '<option value="">Select a product...</option>' + 
              data.products.map(p => `<option value="${p.id}" data-stock="${p.stock}">${this.escape(p.product_name)} (On-Hand: ${p.stock})</option>`).join('');
            prodSelect.disabled = false;

            prodSelect.onchange = () => {
              const opt = prodSelect.options[prodSelect.selectedIndex];
              const stock = opt.getAttribute('data-stock');
              if (hint && stock !== null) {
                hint.textContent = `Available stock in source store: ${stock} units`;
              }
            };
          } else {
            prodSelect.innerHTML = '<option value="">No products in this branch</option>';
          }
        } catch (e) {
          prodSelect.innerHTML = '<option value="">Error loading products</option>';
        }
      };
    }
  },

  async setupQuickTransferModal() {
    this.populateStoreSelect('quick-from-store');
    this.populateStoreSelect('quick-to-store');

    const fromStore = document.getElementById('quick-from-store');
    const prodSelect = document.getElementById('quick-product-select');
    const hint = document.getElementById('quick-stock-hint');

    if (fromStore && prodSelect) {
      fromStore.onchange = async () => {
        const storeId = fromStore.value;
        prodSelect.innerHTML = '<option value="">Loading store catalog...</option>';
        prodSelect.disabled = true;
        if (hint) hint.textContent = '';

        if (!storeId) {
          prodSelect.innerHTML = '<option value="">Select source store first...</option>';
          return;
        }

        try {
          const res = await fetch(`../stockease_api/get_products.php?store_id=${storeId}`, { credentials: 'include' });
          const data = await res.json();
          if (data.success && data.products) {
            prodSelect.innerHTML = '<option value="">Select a product...</option>' + 
              data.products.map(p => `<option value="${p.id}" data-stock="${p.stock}">${this.escape(p.product_name)} (On-Hand: ${p.stock})</option>`).join('');
            prodSelect.disabled = false;

            prodSelect.onchange = () => {
              const opt = prodSelect.options[prodSelect.selectedIndex];
              const stock = opt.getAttribute('data-stock');
              if (hint && stock !== null) {
                hint.textContent = `Available stock in source store: ${stock} units`;
              }
            };
          } else {
            prodSelect.innerHTML = '<option value="">No products in this branch</option>';
          }
        } catch (e) {
          prodSelect.innerHTML = '<option value="">Error loading products</option>';
        }
      };
    }
  },

  async executeQuickTransfer(fromStoreId, toStoreId, productId, qty, prodName, fromStoreName, toStoreName) {
    const confirmed = confirm(`Execute Quick Direct Move?\n\nImmediately move ${qty} units of "${prodName}" from ${fromStoreName} to ${toStoreName}?\n\nThis will update stock levels across both stores instantly.`);
    if (!confirmed) return;

    try {
      const res = await fetch('../stockease_api/manage_transfers.php?action=quick_transfer', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          from_store_id: fromStoreId,
          to_store_id: toStoreId,
          product_id: productId,
          quantity: qty,
          notes: 'Quick direct move executed via Smart Inventory Insights'
        })
      });
      const data = await res.json();
      if (data.success) {
        this.showToast(data.message || 'Direct move completed!', 'success');
        this.loadInsights();
        this.loadTransfers();
        this.loadDashboardData();
      } else {
        this.showToast(data.message || 'Failed to execute quick move', 'error');
      }
    } catch (e) {
      this.showToast('Network error executing quick move', 'error');
    }
  },

  async loadUsers() {
    const tbody = document.getElementById('tbody-users-list');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: #64748B; padding: 24px;">Loading users...</td></tr>';

    try {
      const res = await fetch('../stockease_api/get_users.php', { credentials: 'include' });
      const data = await res.json();
      if (!data.success) throw new Error(data.message);

      this.users = data.users || [];
      this.renderUsersTable();
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: #DC2626; padding: 20px;">Error: ${err.message}</td></tr>`;
    }
  },

  renderUsersTable() {
    const tbody = document.getElementById('tbody-users-list');
    if (!tbody) return;

    const roleFilter = document.getElementById('filter-user-role')?.value || '';
    const search = document.getElementById('input-search-users')?.value.toLowerCase().trim() || '';

    let filtered = this.users;
    // There is only one System Super Admin and it is not manageable in Users & Roles
    filtered = filtered.filter(u => u.role !== 'super_admin');
    if (roleFilter) {
      filtered = filtered.filter(u => u.role === roleFilter);
    }
    if (search) {
      filtered = filtered.filter(u => 
        (u.full_name && u.full_name.toLowerCase().includes(search)) ||
        (u.username && u.username.toLowerCase().includes(search)) ||
        (u.email && u.email.toLowerCase().includes(search))
      );
    }

    if (filtered.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: #64748B; padding: 20px;">No users matching filter criteria.</td></tr>';
      return;
    }

    tbody.innerHTML = filtered.map(u => {
      const roleBadge = this.roleBadgeClass(u.role);

      const storeName = this.getStoreNameById(u.store_id);

      return `
        <tr>
          <td style="font-weight: 600; color: #64748B;">#${u.id}</td>
          <td style="font-weight: 600;">${this.escape(u.full_name || '—')}</td>
          <td>@${this.escape(u.username)}</td>
          <td style="color: #64748B;">${this.escape(u.email || '—')}</td>
          <td><span class="badge ${roleBadge}">${this.escape(this.roleLabel(u.role))}</span></td>
          <td>${this.escape(storeName)}</td>
          <td style="text-align: center;">
            <span class="badge ${u.is_active ? 'badge-success' : 'badge-danger'}">${u.is_active ? 'Active' : 'Inactive'}</span>
          </td>
          <td style="text-align: right;">
            <button class="btn btn-secondary btn-sm" onclick="app.openEditRoleModal(${u.id})">View Scope</button>
          </td>
        </tr>
      `;
    }).join('');
  },

  openEditRoleModal(userId) {
    const user = this.users.find(u => u.id === userId);
    if (!user) return;

    const display = document.getElementById('edit-user-display');
    if (display) display.textContent = `${user.full_name} (@${user.username})`;

    const roleText = document.getElementById('edit-user-role-text');
    if (roleText) {
      roleText.textContent = this.roleLabel(user.role);
      roleText.className = 'badge ' + this.roleBadgeClass(user.role);
    }

    const storeText = document.getElementById('edit-user-store-text');
    if (storeText) {
      storeText.textContent = this.getStoreNameById(user.store_id);
    }

    this.openModal('modal-update-role');
  },

  async loadStoreAdmins() {
    const tbody = document.getElementById('tbody-store-admins-list');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: #64748B; padding: 24px;">Loading store admins...</td></tr>';

    try {
      const res = await fetch('../stockease_api/manage_store_admins.php?action=fetch', { credentials: 'include' });
      const data = await res.json();
      if (!data.success) throw new Error(data.message);

      this.storeAdmins = data.admins || [];
      if (this.storeAdmins.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: #64748B; padding: 20px;">No store admins assigned yet.</td></tr>';
        return;
      }

      tbody.innerHTML = this.storeAdmins.map(a => `
        <tr>
          <td style="font-weight: 600; color: #64748B;">#${a.id}</td>
          <td style="font-weight: 600;">${this.escape(a.full_name)}</td>
          <td>@${this.escape(a.username)}</td>
          <td style="color: #64748B;">${this.escape(a.email || '—')}</td>
          <td style="font-weight: 600;">${this.escape(a.store_name || (a.store_id ? 'Branch ' + a.store_id : 'Unassigned'))}</td>
          <td style="text-align: center;">
            <span class="badge ${a.is_active ? 'badge-success' : 'badge-danger'}">${a.is_active ? 'Active' : 'Inactive'}</span>
          </td>
        </tr>
      `).join('');
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: #DC2626; padding: 20px;">Error: ${err.message}</td></tr>`;
    }
  },

  async loadInsights() {
    const days = this.currentInsightsPeriod;
    const banner = document.getElementById('premium-insights-banner');

    try {
      const res = await fetch(`../stockease_api/get_insights.php?days=${days}`, { credentials: 'include' });
      const data = await res.json();

      if (!data.success) {
        if (data.code === 'PREMIUM_REQUIRED') {
          if (banner) banner.style.display = 'block';
          this.setInsightPlaceholders();
          return;
        }
        throw new Error(data.message);
      }

      if (banner) banner.style.display = 'none';

      // 1. Overview KPIs
      const ov = data.overview || {};
      document.getElementById('insight-period-sales').textContent = '₱' + (ov.total_sales || 0).toLocaleString('en-US', { minimumFractionDigits: 2 });
      document.getElementById('insight-period-orders').textContent = ov.total_transactions || 0;
      document.getElementById('insight-period-units').textContent = ov.total_units_sold || 0;
      document.getElementById('insight-period-avg').textContent = '₱' + (ov.average_transaction_value || 0).toLocaleString('en-US', { minimumFractionDigits: 2 });

      // 2. Transfer Recommendations
      const recContainer = document.getElementById('container-transfer-recommendations');
      const recs = data.transfer_recommendations || [];
      if (recContainer) {
        if (recs.length === 0) {
          recContainer.innerHTML = '<div style="color: #059669; padding: 14px 0; font-weight: 600;">&check; Inventory is balanced across all branches. No urgent transfer opportunities identified.</div>';
        } else {
          recContainer.innerHTML = recs.map(r => `
            <div style="background: #F8FAFC; border: 1px solid var(--border-color); border-radius: 6px; padding: 14px; margin-bottom: 12px; display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 14px;">
              <div style="flex: 1; min-width: 280px;">
                <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px;">
                  <span class="badge badge-info" style="font-size: 11px;">⚡ Smart Recommendation</span>
                  <span style="font-weight: 700; font-size: 14px; color: #0F172A;">${this.escape(r.headline || r.product_name)}</span>
                </div>
                <div style="color: #334155; font-size: 13px; margin-top: 4px; line-height: 1.5;">
                  ${this.escape(r.reason)}
                </div>
                <div style="color: #64748B; font-size: 11px; margin-top: 6px;">
                  Source: <strong>${this.escape(r.source_store_name)}</strong> (Available: ${r.source_current_stock} units) &bull; Destination: <strong>${this.escape(r.destination_store_name)}</strong> (Current: ${r.destination_current_stock} units)
                </div>
              </div>
              <div style="display: flex; flex-direction: column; gap: 6px; align-items: flex-end;">
                <button class="btn btn-primary btn-sm" style="background-color: #0284C7; border-color: #0284C7;" onclick="app.executeQuickTransfer(${r.source_store_id}, ${r.destination_store_id}, ${r.source_product_id}, ${r.recommended_quantity}, '${this.escape(r.product_name)}', '${this.escape(r.source_store_name)}', '${this.escape(r.destination_store_name)}')">
                  ⚡ Quick Direct Move (${r.recommended_quantity} units)
                </button>
                <button class="btn btn-secondary btn-sm" style="font-size: 11px; padding: 3px 8px;" onclick="app.launchPrefilledTransfer(${r.source_store_id}, ${r.destination_store_id}, ${r.source_product_id}, ${r.recommended_quantity})">
                  Review in Transfer Form
                </button>
              </div>
            </div>
          `).join('');
        }
      }

      // 3. Stockout Risks
      const riskContainer = document.getElementById('container-stockout-risks');
      const risks = data.stockout_risks || [];
      if (riskContainer) {
        if (risks.length === 0) {
          riskContainer.innerHTML = '<div style="color: #64748B; padding: 10px 0;">No items currently at critical risk of stockout (&lt; 7 days).</div>';
        } else {
          riskContainer.innerHTML = risks.map(rk => `
            <div style="padding: 8px 0; border-bottom: 1px solid #F1F5F9; display: flex; justify-content: space-between;">
              <div>
                <strong style="color: #DC2626;">${this.escape(rk.product_name)}</strong>
                <span style="color: #64748B; font-size: 11px; margin-left: 6px;">(${this.escape(rk.store_name)})</span>
              </div>
              <div style="font-weight: 600;">${rk.stock} left (${rk.days_of_stock}d coverage)</div>
            </div>
          `).join('');
        }
      }

      // 4. Slow-Moving Stock
      const slowContainer = document.getElementById('container-slow-moving');
      const slow = data.slow_moving || [];
      if (slowContainer) {
        if (slow.length === 0) {
          slowContainer.innerHTML = '<div style="color: #64748B; padding: 10px 0;">No dormant inventory identified.</div>';
        } else {
          slowContainer.innerHTML = slow.slice(0, 6).map(sl => `
            <div style="padding: 8px 0; border-bottom: 1px solid #F1F5F9; display: flex; justify-content: space-between;">
              <div>
                <strong>${this.escape(sl.product_name)}</strong>
                <span style="color: #64748B; font-size: 11px; margin-left: 6px;">(${this.escape(sl.store_name)})</span>
              </div>
              <div style="color: #64748B;">${sl.stock} units (${sl.days_of_stock}d)</div>
            </div>
          `).join('');
        }
      }

      // 5. Comparison Table
      const compBody = document.getElementById('tbody-insight-store-comparison');
      const storeComps = data.store_comparison || [];
      if (compBody) {
        if (storeComps.length === 0) {
          compBody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: #64748B; padding: 16px;">No comparison data available.</td></tr>';
        } else {
          compBody.innerHTML = storeComps.map(sc => `
            <tr>
              <td style="font-weight: 600;">${this.escape(sc.store_name)}</td>
              <td style="color: #64748B;">${this.escape(sc.location || '—')}</td>
              <td style="text-align: right; font-weight: 700;">₱${(sc.sales || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
              <td style="text-align: right;">${sc.transactions || 0}</td>
              <td style="text-align: right;">${sc.units_sold || 0}</td>
              <td style="text-align: right; font-weight: 600;">${sc.total_stock || 0} units</td>
            </tr>
          `).join('');
        }
      }

    } catch (err) {
      console.error(err);
      this.setInsightPlaceholders();
    }
  },

  setInsightPlaceholders() {
    ['insight-period-sales', 'insight-period-orders', 'insight-period-units', 'insight-period-avg'].forEach(id => {
      const el = document.getElementById(id);
      if (el) el.textContent = '—';
    });
  },

  launchPrefilledTransfer(sourceStoreId, destStoreId, sourceProductId, qty) {
    this.showTab('transfers');
    this.openModal('modal-new-transfer');
    this.setupNewTransferModal().then(() => {
      const from = document.getElementById('transfer-from-store');
      const to = document.getElementById('transfer-to-store');
      const q = document.getElementById('transfer-quantity');
      const notes = document.getElementById('transfer-notes');

      if (from) from.value = sourceStoreId;
      if (to) to.value = destStoreId;
      if (q) q.value = qty;
      if (notes) notes.value = 'Generated from StockEase Inventory Velocity Recommendations';

      if (from && from.onchange) {
        from.onchange().then(() => {
          const prod = document.getElementById('transfer-product-select');
          if (prod) prod.value = sourceProductId;
        });
      }
    });
  },

  // ---- Other Sales: manual (non-POS) sales + voided/refunded transactions ----
  renderOtherSales(other) {
    const totalEl = document.getElementById('rep-other-total');
    const countEl = document.getElementById('rep-other-count');
    const voidTotalEl = document.getElementById('rep-voided-total');
    const voidCountEl = document.getElementById('rep-voided-count');
    const tbody = document.getElementById('tbody-other-sales');
    if (!totalEl || !tbody) return;

    const peso = v => '₱' + parseFloat(v || 0).toLocaleString('en-US', { minimumFractionDigits: 2 });
    totalEl.textContent = peso(other.manual_sales_total);
    if (countEl) countEl.textContent = (other.manual_sales_count || 0) + ' manual transaction(s)';
    if (voidTotalEl) voidTotalEl.textContent = peso(other.voided_total);
    if (voidCountEl) voidCountEl.textContent = (other.voided_count || 0) + ' voided / refunded transaction(s)';

    const entries = other.entries || [];
    if (entries.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: #64748B; padding: 16px;">No manual sales or voided transactions in this period.</td></tr>';
      return;
    }

    tbody.innerHTML = entries.map(e => {
      const isVoided = (e.status || '').toLowerCase() === 'voided';
      const badge = isVoided
        ? '<span class="badge badge-danger" style="font-size: 10px;">Voided</span>'
        : '<span class="badge badge-success" style="font-size: 10px;">Completed</span>';
      const items = (e.items_summary || '').split('\n').map(l => this.escape(l)).join('<br>');
      return `
        <tr>
          <td style="color: #64748B;">#${e.id}</td>
          <td>${this.escape((e.created_at || '').substring(0, 16))}</td>
          <td style="font-size: 12px;">${items || '—'}</td>
          <td>${this.escape(e.recorded_by || '—')}</td>
          <td>${this.escape(e.payment_type || '—')}</td>
          <td>${badge}</td>
          <td class="text-right" style="font-weight: 700;">${peso(e.total_amount)}</td>
        </tr>`;
    }).join('');
  },

  async openManualSaleModal() {
    const storeId = this.currentReportStoreId;
    if (!storeId) {
      this.showToast('Select a branch first', 'error');
      return;
    }

    this.manualSaleItems = [];
    this.renderManualSaleItems();
    const notesEl = document.getElementById('ms-notes');
    if (notesEl) notesEl.value = '';
    const payEl = document.getElementById('ms-payment');
    if (payEl) payEl.selectedIndex = 0;
    this.openModal('modal-manual-sale');

    const sel = document.getElementById('ms-product');
    if (sel) sel.innerHTML = '<option value="">Loading products...</option>';
    try {
      const res = await fetch(`../stockease_api/get_products.php?store_id=${storeId}`, { credentials: 'include' });
      const data = await res.json();
      const prods = data.products || [];
      if (sel) {
        sel.innerHTML = prods.length
          ? prods.map(p => `<option value="${p.id}" data-price="${parseFloat(p.price || 0)}" data-stock="${parseInt(p.stock, 10) || 0}">${this.escape(p.product_name)} (stock: ${parseInt(p.stock, 10) || 0})</option>`).join('')
          : '<option value="">No products in this branch</option>';
      }
    } catch (e) {
      if (sel) sel.innerHTML = '<option value="">Failed to load products</option>';
    }
  },

  setupManualSaleForm() {
    const btnOpen = document.getElementById('btn-record-manual-sale');
    if (btnOpen) btnOpen.addEventListener('click', () => this.openManualSaleModal());

    const addBtn = document.getElementById('btn-ms-add');
    if (addBtn) addBtn.addEventListener('click', () => this.addManualSaleItem());

    const prodSel = document.getElementById('ms-product');
    if (prodSel) {
      prodSel.addEventListener('change', () => {
        const opt = prodSel.options[prodSel.selectedIndex];
        const price = document.getElementById('ms-price');
        if (opt && price) price.value = opt.getAttribute('data-price') || '';
      });
    }

    const form = document.getElementById('form-manual-sale');
    if (form) {
      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        await this.submitManualSale();
      });
    }
  },

  addManualSaleItem() {
    const prodSel = document.getElementById('ms-product');
    const qtyEl = document.getElementById('ms-qty');
    const priceEl = document.getElementById('ms-price');
    if (!prodSel || !prodSel.value) {
      this.showToast('Select a product first', 'error');
      return;
    }

    const opt = prodSel.options[prodSel.selectedIndex];
    const productId = parseInt(prodSel.value, 10);
    const qty = parseInt(qtyEl && qtyEl.value, 10) || 0;
    const price = parseFloat(priceEl && priceEl.value);
    const stock = parseInt(opt.getAttribute('data-stock'), 10) || 0;

    if (qty <= 0) {
      this.showToast('Quantity must be at least 1', 'error');
      return;
    }
    if (!isFinite(price) || price < 0) {
      this.showToast('Enter a valid unit price', 'error');
      return;
    }

    this.manualSaleItems = this.manualSaleItems || [];
    const existing = this.manualSaleItems.find(i => i.product_id === productId);
    const newQty = (existing ? existing.quantity : 0) + qty;
    if (newQty > stock) {
      this.showToast(`Only ${stock} unit(s) of this product are in stock`, 'error');
      return;
    }

    if (existing) {
      existing.quantity = newQty;
      existing.unit_price = price;
    } else {
      this.manualSaleItems.push({ product_id: productId, name: opt.textContent, quantity: qty, unit_price: price });
    }

    this.renderManualSaleItems();
  },

  renderManualSaleItems() {
    const box = document.getElementById('ms-items');
    const totalEl = document.getElementById('ms-total');
    if (!box) return;

    const items = this.manualSaleItems || [];
    if (items.length === 0) {
      box.innerHTML = '<span style="color: #64748B;">No items added yet.</span>';
      if (totalEl) totalEl.textContent = '₱0.00';
      return;
    }

    let total = 0;
    box.innerHTML = items.map((it, idx) => {
      total += it.quantity * it.unit_price;
      return `<div style="display: flex; justify-content: space-between; padding: 4px 0; border-bottom: 1px solid #F1F5F9;">
        <span>${it.quantity} × ${this.escape(it.name)} @ ₱${it.unit_price.toFixed(2)}</span>
        <span><strong>₱${(it.quantity * it.unit_price).toFixed(2)}</strong>
          <a href="#" data-idx="${idx}" class="ms-remove" style="color: #DC2626; margin-left: 8px;" title="Remove">✕</a></span>
      </div>`;
    }).join('');

    box.querySelectorAll('.ms-remove').forEach(a => {
      a.addEventListener('click', (e) => {
        e.preventDefault();
        this.manualSaleItems.splice(parseInt(a.getAttribute('data-idx'), 10), 1);
        this.renderManualSaleItems();
      });
    });

    if (totalEl) totalEl.textContent = '₱' + total.toLocaleString('en-US', { minimumFractionDigits: 2 });
  },

  async submitManualSale() {
    const storeId = this.currentReportStoreId;
    if (!storeId) return;

    this.manualSaleItems = this.manualSaleItems || [];
    if (this.manualSaleItems.length === 0) {
      this.showToast('Add at least one item to the sale', 'error');
      return;
    }

    try {
      const res = await fetch('../stockease_api/manage_manual_sales.php?action=create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          store_id: parseInt(storeId, 10),
          payment_type: (document.getElementById('ms-payment') || {}).value || 'Cash',
          notes: ((document.getElementById('ms-notes') || {}).value || '').trim(),
          items: this.manualSaleItems.map(i => ({ product_id: i.product_id, quantity: i.quantity, unit_price: i.unit_price }))
        })
      });
      const data = await res.json();
      if (!data.success) throw new Error(data.message);

      this.showToast(data.message || 'Manual sale recorded', 'success');
      this.closeModals();
      this.loadReports();
    } catch (err) {
      this.showToast(err.message || 'Failed to record the manual sale', 'error');
    }
  },

  async loadReports() {
    let storeId = this.currentReportStoreId;
    if (!storeId && this.stores.length > 0) {
      storeId = this.stores[0].id;
      this.currentReportStoreId = storeId;
      const select = document.getElementById('reports-store-select');
      if (select) select.value = storeId;
    }

    if (!storeId) return;

    // Dates based on period (calendar week / calendar month so the chart matches the filter)
    const now = new Date();
    let startDate = this.localDateStr(now);
    let endDate = startDate;

    if (this.currentReportPeriod === 'week') {
      const dow = (now.getDay() + 6) % 7; // Monday = 0
      const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - dow);
      startDate = this.localDateStr(monday);
    } else if (this.currentReportPeriod === 'month') {
      startDate = this.localDateStr(new Date(now.getFullYear(), now.getMonth(), 1));
    }

    const periodLabel = this.reportPeriodLabel();
    const earningsSubtitle = document.getElementById('chart-branch-earnings-subtitle');
    if (earningsSubtitle) {
      earningsSubtitle.textContent = `${periodLabel} progress (${startDate} to ${endDate}) - updates whenever the period filter changes.`;
    }
    const reportsScope = document.getElementById('reports-period-scope');
    if (reportsScope) reportsScope.textContent = `${periodLabel}: ${startDate} to ${endDate}`;

    try {
      const url = `../stockease_api/get_store_reports.php?store_id=${storeId}&start_date=${startDate}&end_date=${endDate}`;
      const res = await fetch(url, { credentials: 'include' });
      const data = await res.json();
      if (!data.success) throw new Error(data.message);

      const s = data.summary || {};
      document.getElementById('rep-total-sales').textContent = '₱' + (s.total_sales || 0).toLocaleString('en-US', { minimumFractionDigits: 2 });
      document.getElementById('rep-total-tx').textContent = s.transaction_count || 0;
      document.getElementById('rep-avg-order').textContent = '₱' + (s.average_transaction || 0).toLocaleString('en-US', { minimumFractionDigits: 2 });
      document.getElementById('rep-units-sold').textContent = s.products_sold || 0;

      // Other Sales (manual, non-POS + voided/refunded)
      this.renderOtherSales(data.other_sales || {});

      // Payment Methods
      const payContainer = document.getElementById('container-payment-methods');
      const payMethods = data.payment_methods || [];
      if (payContainer) {
        if (payMethods.length === 0) {
          payContainer.innerHTML = '<div style="color: #64748B;">No transactions in this period.</div>';
        } else {
          payContainer.innerHTML = payMethods.map(pm => `
            <div style="display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #F1F5F9;">
              <span style="font-weight: 600;">${this.escape(pm.payment_type || pm.method || 'Unknown')} (${pm.count || 0} orders)</span>
              <span style="font-weight: 700;">₱${parseFloat(pm.total_amount || pm.total || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}</span>
            </div>
          `).join('');
        }
      }

      // Top Products
      const prodContainer = document.getElementById('container-top-products');
      const topProds = data.top_products || [];
      if (prodContainer) {
        if (topProds.length === 0) {
          prodContainer.innerHTML = '<div style="color: #64748B;">No sales recorded in this period.</div>';
        } else {
          prodContainer.innerHTML = topProds.map((tp, idx) => `
            <div style="display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #F1F5F9;">
              <div>
                <strong>#${idx + 1} ${this.escape(tp.product_name)}</strong>
                <span style="color: #64748B; font-size: 11px; margin-left: 6px;">(${tp.total_sold ?? tp.quantity_sold ?? 0} sold)</span>
              </div>
              <span style="font-weight: 700;">₱${parseFloat(tp.total_revenue || tp.revenue || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}</span>
            </div>
          `).join('');
        }
      }

      // Save for CSV export
      this.lastReportData = data;

      // Render Charts using Chart.js
      this.renderReportCharts(data);

    } catch (err) {
      console.warn('Reports error', err);
    }
  },

  renderReportCharts(data) {
    if (typeof Chart === 'undefined') return;

    // 1. Line Chart: Earnings Progress
    const lineCanvas = document.getElementById('chart-branch-earnings');
    if (lineCanvas) {
      if (this.branchEarningsChart) {
        this.branchEarningsChart.destroy();
      }

      const dailySales = (data.daily_sales || []).slice().reverse();
      const labels = dailySales.map(d => d.sale_date || 'Date');
      const salesValues = dailySales.map(d => parseFloat(d.daily_sales || 0));

      // Fallback if the period produced no buckets at all
      if (labels.length === 0) {
        labels.push(this.reportPeriodLabel());
        salesValues.push(parseFloat(data.summary?.total_sales || 0));
      }

      this.branchEarningsChart = new Chart(lineCanvas, {
        type: 'line',
        data: {
          labels: labels,
          datasets: [{
            label: 'Earnings (₱)',
            data: salesValues,
            borderColor: '#2563EB',
            backgroundColor: 'rgba(37, 99, 235, 0.1)',
            fill: true,
            tension: 0.3,
            pointBackgroundColor: '#1D4ED8',
            pointRadius: 5
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false }
          },
          scales: {
            y: {
              beginAtZero: true,
              ticks: {
                callback: function(val) { return '₱' + val.toLocaleString(); }
              }
            }
          }
        }
      });
      this.attachChartDownloadButton('chart-branch-earnings', 'stockease_branch_earnings_' + new Date().toISOString().split('T')[0] + '.png');
    }

    // 2. Bar Chart: Most Purchased Products
    const prodCanvas = document.getElementById('chart-top-products');
    if (prodCanvas) {
      if (this.topProductsChart) {
        this.topProductsChart.destroy();
      }

      const topProds = data.top_products || [];
      const prodLabels = topProds.map(p => p.product_name || 'Item');
      const prodUnits = topProds.map(p => parseInt(p.total_sold || p.quantity_sold || 0));

      this.topProductsChart = new Chart(prodCanvas, {
        type: 'bar',
        data: {
          labels: prodLabels,
          datasets: [{
            label: 'Units Purchased',
            data: prodUnits,
            backgroundColor: '#10B981',
            borderRadius: 4
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false }
          },
          scales: {
            y: {
              beginAtZero: true,
              ticks: { precision: 0 }
            }
          }
        }
      });
      this.attachChartDownloadButton('chart-top-products', 'stockease_top_products_' + new Date().toISOString().split('T')[0] + '.png');
    }
  },

  downloadChartPng(chart, filename) {
    if (!chart || typeof chart.toBase64Image !== 'function') {
      this.showToast('Chart is not ready for download yet', 'error');
      return;
    }
    try {
      const dataUrl = chart.toBase64Image('image/png', 1);
      const link = document.createElement('a');
      link.href = dataUrl;
      link.download = filename || ('stockease_chart_' + new Date().toISOString().split('T')[0] + '.png');
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      this.showToast('Chart image downloaded: ' + link.download, 'success');
    } catch (e) {
      console.error('Chart PNG export failed', e);
      this.showToast('Failed to export chart image', 'error');
    }
  },

  attachChartDownloadButton(canvasId, filename) {
    const canvas = document.getElementById(canvasId);
    if (!canvas || typeof Chart === 'undefined') return;
    if (!Chart.getChart(canvas)) return;
    const wrapper = canvas.parentElement;
    if (!wrapper) return;

    // Avoid duplicate buttons when charts are re-rendered into the same wrapper
    if (wrapper.querySelector('.chart-download-btn')) return;

    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'btn btn-secondary btn-xs chart-download-btn';
    btn.textContent = '⤓ PNG';
    btn.title = 'Download this chart as a PNG image';
    btn.addEventListener('click', (e) => {
      e.stopPropagation(); // charts sit inside clickable/toggle panels
      this.downloadChartPng(Chart.getChart(canvas), filename);
    });
    wrapper.appendChild(btn);
  },

  csvExportName(tableName) {
    const date = new Date().toISOString().split('T')[0];
    return 'stockease_' + tableName + '_export_' + date + '.csv';
  },

  exportTableToCsv(tableId, filename) {
    const table = document.getElementById(tableId);
    if (!table) {
      this.showToast('No table found to export', 'error');
      return;
    }

    const csvLines = [];
    table.querySelectorAll('tr').forEach(tr => {
      const cells = tr.querySelectorAll('th, td');
      if (!cells.length) return;
      const values = Array.from(cells).map(cell => {
        let text = (cell.innerText || cell.textContent || '').replace(/\s+/g, ' ').trim();
        if (/[",\n\r]/.test(text)) {
          text = '"' + text.replace(/"/g, '""') + '"';
        }
        return text;
      });
      csvLines.push(values.join(','));
    });

    if (csvLines.length === 0) {
      this.showToast('Table is empty - nothing to export', 'error');
      return;
    }

    // BOM helps spreadsheet apps detect UTF-8 (peso signs, en dashes)
    const csv = '\uFEFF' + csvLines.join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.href = url;
    link.download = filename || this.csvExportName(tableId.replace(/^table-/, ''));
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    this.showToast('CSV exported: ' + link.download, 'success');
  },

  exportReportsCSV() {
    if (!this.lastReportData) {
      this.showToast('No report data to export', 'error');
      return;
    }

    const d = this.lastReportData;
    let csv = "StockEase Branch Report\n";
    const range = d.date_range || {};
    const rangeLabel = (range.start_date || range.start || '') + ' to ' + (range.end_date || range.end || '');
    csv += `Store:,"${(d.store && d.store.store_name) || d.store_name || 'Branch'}"\n`;
    csv += `Date Range:,"${rangeLabel}"\n\n`;

    csv += "Summary Metrics\n";
    csv += `Gross Revenue,${d.summary ? d.summary.total_sales : 0}\n`;
    csv += `Total Transactions,${d.summary ? d.summary.transaction_count : 0}\n`;
    csv += `Average Order Value,${d.summary ? d.summary.average_transaction : 0}\n`;
    csv += `Units Sold,${d.summary ? d.summary.products_sold : 0}\n\n`;

    csv += "Top Products\nProduct,Units Sold,Revenue\n";
    if (d.top_products) {
      d.top_products.forEach(p => {
        csv += `"${p.product_name}",${p.total_sold ?? p.quantity_sold ?? 0},${p.total_revenue ?? p.revenue ?? 0}\n`;
      });
    }

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement("a");
    const url = URL.createObjectURL(blob);
    link.setAttribute("href", url);
    link.setAttribute("download", `StockEase_Report_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    this.showToast('Report CSV exported successfully!', 'success');
  },

  async loadAuditLogs() {
    const tbody = document.getElementById('tbody-audit-logs');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: #64748B; padding: 24px;">Loading audit logs...</td></tr>';

    const search = document.getElementById('input-audit-search')?.value.trim() || '';
    const action = document.getElementById('filter-audit-action')?.value || '';
    const perPageSelect = document.getElementById('audit-per-page');
    if (perPageSelect) {
      this.auditPerPage = parseInt(perPageSelect.value, 10) || this.auditPerPage;
    }
    if (!Number.isFinite(this.auditPage) || this.auditPage < 1) this.auditPage = 1;

    const params = new URLSearchParams();
    if (search) params.set('search', search);
    if (action) params.set('action_filter', action);
    params.set('page', String(this.auditPage));
    params.set('per_page', String(this.auditPerPage));

    try {
      const res = await fetch('../stockease_api/get_audit_logs.php?' + params.toString(), { credentials: 'include' });
      const data = await res.json();
      if (!data.success) throw new Error(data.message);

      const logs = data.logs || [];
      const pagination = data.pagination || { page: 1, per_page: this.auditPerPage, total: 0, total_pages: 1 };
      this.auditPagination = pagination;
      this.auditPage = pagination.page || 1;
      this.auditPerPage = pagination.per_page || this.auditPerPage;
      this.renderAuditPagination(pagination);

      if (logs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: #64748B; padding: 20px;">No audit records found on this page.</td></tr>';
        return;
      }

      tbody.innerHTML = logs.map(l => `
        <tr>
          <td style="color: #64748B; white-space: nowrap;">${l.created_at}</td>
          <td style="font-weight: 600;">${this.escape(l.user_name)}</td>
          <td><span class="badge ${this.roleBadgeClass(l.role)}">${this.escape(this.roleLabel(l.role))}</span></td>
          <td style="font-weight: 600; color: #0F172A;">${this.escape(l.action)}</td>
          <td>${this.escape(l.target)}</td>
          <td style="color: #64748B;">${this.escape(l.store_name)}</td>
          <td style="color: #334155; font-size: 12px;">${this.escape(l.details)}</td>
        </tr>
      `).join('');
    } catch (err) {
      this.renderAuditPagination({ page: this.auditPage, per_page: this.auditPerPage, total: 0, total_pages: 1 });
      tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #DC2626; padding: 20px;">Error: ${err.message}</td></tr>`;
    }
  },

  renderAuditPagination(p) {
    const bar = document.getElementById('audit-pagination');
    if (!bar) return;

    const page = Math.max(1, parseInt(p.page, 10) || 1);
    const totalPages = Math.max(1, parseInt(p.total_pages, 10) || 1);
    const perPage = Math.max(1, parseInt(p.per_page, 10) || this.auditPerPage);
    const total = Math.max(0, parseInt(p.total, 10) || 0);

    if (total === 0) {
      bar.style.display = 'flex';
      const range = document.getElementById('audit-range-label');
      if (range) range.textContent = '0 of 0 records';
      const numbers = document.getElementById('audit-page-numbers');
      if (numbers) numbers.innerHTML = '<span class="page-btn" aria-hidden="true">1</span>';
      this.setAuditPagerState(1, 1);
      return;
    }

    const first = (page - 1) * perPage + 1;
    const last = Math.min(total, page * perPage);
    const range = document.getElementById('audit-range-label');
    if (range) range.textContent = `Showing ${first}–${last} of ${total.toLocaleString('en-US')} records`;

    const numbers = document.getElementById('audit-page-numbers');
    if (numbers) {
      const pages = this.auditPageWindow(page, totalPages);
      numbers.innerHTML = pages.map(pg => {
        if (pg === '…') return '<span class="page-ellipsis">&hellip;</span>';
        return `<button type="button" class="page-btn${pg === page ? ' active' : ''}" data-page="${pg}">${pg}</button>`;
      }).join('');
    }

    this.setAuditPagerState(page, totalPages);
  },

  auditPageWindow(page, totalPages) {
    const pages = [];
    const push = (v) => {
      if (v > 0 && v <= totalPages && pages[pages.length - 1] !== v) pages.push(v);
    };
    push(1);
    if (page > 3) push('…');
    for (let p = page - 1; p <= page + 1; p++) push(p);
    if (page < totalPages - 2) push('…');
    push(totalPages);
    return pages;
  },

  setAuditPagerState(page, totalPages) {
    const set = (id, disabled) => {
      const el = document.getElementById(id);
      if (el) el.disabled = !!disabled;
    };
    set('audit-first-page', page <= 1);
    set('audit-prev-page', page <= 1);
    set('audit-next-page', page >= totalPages);
    set('audit-last-page', page >= totalPages);
  },

  // Export every matching audit record (not just the page on screen)
  async exportAuditLogsCsv() {
    const search = document.getElementById('input-audit-search')?.value.trim() || '';
    const action = document.getElementById('filter-audit-action')?.value || '';

    this.showToast('Preparing full audit log export...', 'info');

    const headers = ['Timestamp', 'User', 'Role', 'Action', 'Target', 'Store', 'Details'];
    const rows = [];
    const perPage = 200;
    let page = 1;
    let totalPages = 1;

    try {
      while (page <= totalPages && page <= 100) {
        const params = new URLSearchParams();
        if (search) params.set('search', search);
        if (action) params.set('action_filter', action);
        params.set('page', String(page));
        params.set('per_page', String(perPage));

        const res = await fetch('../stockease_api/get_audit_logs.php?' + params.toString(), { credentials: 'include' });
        const data = await res.json();
        if (!data.success) throw new Error(data.message || 'Export failed');

        const pagination = data.pagination || {};
        totalPages = Math.max(1, parseInt(pagination.total_pages, 10) || 1);

        (data.logs || []).forEach(l => {
          rows.push([l.created_at, l.user_name, this.roleLabel(l.role), l.action, l.target, l.store_name, l.details]);
        });
        page++;
      }

      if (rows.length === 0) {
        this.showToast('No audit records to export', 'error');
        return;
      }

      const csvEscape = v => {
        const s = (v === null || v === undefined) ? '' : String(v);
        return /[",\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
      };
      let csv = headers.join(',') + '\r\n';
      rows.forEach(r => { csv += r.map(csvEscape).join(',') + '\r\n'; });
      this.downloadTextCsv(csv, 'stockease_audit_logs_' + new Date().toISOString().split('T')[0] + '.csv');
    } catch (err) {
      this.showToast('Audit export failed: ' + err.message, 'error');
    }
  },

  openCompareModal() {
    if (!this.currentPerformanceList || this.currentPerformanceList.length < 2) {
      this.showToast('At least 2 stores are required to compare performance.', 'error');
      return;
    }

    const selectA = document.getElementById('compare-select-a');
    const selectB = document.getElementById('compare-select-b');

    const opts = this.currentPerformanceList.map((s, idx) => `<option value="${idx}">${this.escape(s.name)}</option>`).join('');
    if (selectA) selectA.innerHTML = opts;
    if (selectB) {
      selectB.innerHTML = opts;
      selectB.value = "1";
    }

    const updateCompare = () => {
      const idxA = parseInt(selectA.value, 10);
      const idxB = parseInt(selectB.value, 10);
      const storeA = this.currentPerformanceList[idxA];
      const storeB = this.currentPerformanceList[idxB];

      if (!storeA || !storeB) return;

      document.getElementById('compare-header-a').textContent = storeA.name;
      document.getElementById('compare-header-b').textContent = storeB.name;

      document.getElementById('comp-sales-a').textContent = '₱' + storeA.sales.toLocaleString('en-US', { minimumFractionDigits: 2 });
      document.getElementById('comp-sales-b').textContent = '₱' + storeB.sales.toLocaleString('en-US', { minimumFractionDigits: 2 });

      document.getElementById('comp-orders-a').textContent = storeA.orders;
      document.getElementById('comp-orders-b').textContent = storeB.orders;

      document.getElementById('comp-staff-a').textContent = storeA.staffCount;
      document.getElementById('comp-staff-b').textContent = storeB.staffCount;

      document.getElementById('comp-loc-a').textContent = storeA.location;
      document.getElementById('comp-loc-b').textContent = storeB.location;
    };

    if (selectA) selectA.onchange = updateCompare;
    if (selectB) selectB.onchange = updateCompare;

    updateCompare();
    this.openModal('modal-compare-stores');
  },

  // Helpers
  localDateStr(d) {
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return d.getFullYear() + '-' + m + '-' + day;
  },

  reportPeriodLabel() {
    if (this.currentReportPeriod === 'week') return 'This Week';
    if (this.currentReportPeriod === 'month') return 'This Month';
    return 'Today';
  },

  getChartInstance(canvasId) {
    const canvas = document.getElementById(canvasId);
    if (!canvas || typeof Chart === 'undefined' || !Chart.getChart) return null;
    return Chart.getChart(canvas);
  },

  roleLabel(role) {
    const map = {
      super_admin: 'Super Admin',
      store_owner: 'Store Owner',
      manager: 'Manager',
      store_admin: 'Store Admin',
      staff: 'Staff – Cashier',
      inventory_checker: 'Staff – Inventory'
    };
    return map[role] || role || 'Unknown';
  },

  roleBadgeClass(role) {
    const map = {
      super_admin: 'badge-info',
      store_owner: 'badge-primary',
      manager: 'badge-success',
      store_admin: 'badge-warning',
      staff: 'badge-slate',
      inventory_checker: 'badge-slate'
    };
    return map[role] || 'badge-slate';
  },

  populateStoreSelect(selectId) {
    const el = document.getElementById(selectId);
    if (!el || !this.stores) return;
    const currentVal = el.value;
    el.innerHTML = '<option value="">Select store branch...</option>' + 
      this.stores.map(s => `<option value="${s.id}">${this.escape(s.store_name)}</option>`).join('');
    if (currentVal) el.value = currentVal;
  },

  getStoreNameById(id) {
    if (!id) return 'Not Assigned';
    const s = this.stores.find(item => item.id == id);
    return s ? s.store_name : 'Branch ' + id;
  },

  togglePassword(inputId, btn) {
    const input = document.getElementById(inputId);
    if (!input) return;
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    const openIcon = btn.querySelector('.eye-open');
    const closedIcon = btn.querySelector('.eye-closed');
    if (openIcon) openIcon.style.display = show ? 'none' : 'block';
    if (closedIcon) closedIcon.style.display = show ? 'block' : 'none';
    btn.title = show ? 'Hide password' : 'Show password';
    btn.setAttribute('aria-label', btn.title);
    btn.setAttribute('aria-pressed', show ? 'true' : 'false');
  },

  // ---- Password strength (mirrors stockease_api/password_policy.php) ----
  passwordScore(pw) {
    if (!pw) return 0;
    let score = 0;
    if (pw.length >= 8) score++;
    if (pw.length >= 12) score++;
    if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) score++;
    if (/\d/.test(pw)) score++;
    if (/[^A-Za-z0-9]/.test(pw)) score++;
    if (pw.length < 8) score = Math.min(score, 1);
    return Math.max(0, Math.min(4, score));
  },

  passwordLabel(score) {
    return ['Too weak', 'Weak', 'Fair', 'Strong', 'Very strong'][score] || 'Too weak';
  },

  passwordIsAcceptable(pw) {
    return !!pw && pw.length >= 8 && /[A-Za-z]/.test(pw) && /\d/.test(pw);
  },

  passwordRequirementText() {
    return 'Password must be at least 8 characters and include a letter and a number.';
  },

  setupPasswordMeters() {
    this.bindPasswordMeter('admin-password', 'admin-password-meter');
    this.bindPasswordMeter('new-business-owner-password', 'new-business-owner-password-meter');
  },

  bindPasswordMeter(inputId, meterId) {
    const input = document.getElementById(inputId);
    const meter = document.getElementById(meterId);
    if (!input || !meter) return;
    const bar = meter.querySelector('.pw-meter-bar');
    const label = meter.querySelector('.pw-meter-label');
    const colors = ['#DC2626', '#EF4444', '#F59E0B', '#10B981', '#059669'];
    const update = () => {
      const score = this.passwordScore(input.value);
      if (bar) {
        bar.style.width = (score * 25) + '%';
        bar.style.backgroundColor = colors[score];
      }
      if (label) {
        label.textContent = input.value
          ? `Password strength: ${this.passwordLabel(score)}`
          : `Password strength: — (${this.passwordRequirementText()})`;
        label.style.color = input.value ? colors[score] : '#64748B';
      }
    };
    input.addEventListener('input', update);
    update();
  },

  escape(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }
};

// Auto-run on document ready
document.addEventListener('DOMContentLoaded', () => {
  app.init();
});