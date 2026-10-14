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
  currentPerformanceList: [],
  currentInsightsPeriod: 7,
  currentReportPeriod: 'today',
  currentReportStoreId: null,

  // Initialization
  init() {
    this.setupDateDisplay();
    this.setupNavigation();
    this.setupModalEvents();
    this.setupFormSubmissions();
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
    if (auditRefresh) auditRefresh.addEventListener('click', () => this.loadAuditLogs());
    if (auditSearch) auditSearch.addEventListener('keyup', (e) => { if (e.key === 'Enter') this.loadAuditLogs(); });
    if (auditAction) auditAction.addEventListener('change', () => this.loadAuditLogs());

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
  },

  showTab(tabId) {
    this.activeTab = tabId;

    // Update navigation
    document.querySelectorAll('.sidebar-nav .nav-item').forEach(item => {
      item.classList.toggle('active', item.getAttribute('data-tab') === tabId);
    });

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
      stores: 'Retail Store Branches',
      transfers: 'Inter-Store Stock Transfers',
      users: 'User Accounts & Roles',
      admins: 'Branch Store Administrators',
      insights: 'StockEase Smart Inventory Insights',
      reports: 'Branch Sales & Performance Reports',
      audit: 'Security & Operational Audit Logs',
      subscription: 'Subscription & Freemium Management',
      settings: 'System Technical Architecture'
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
      case 'stores':
        this.loadStores();
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
    const btnAddStore = document.getElementById('btn-modal-add-store');
    if (btnAddStore) {
      btnAddStore.addEventListener('click', async () => {
        if (!this.users || this.users.length === 0) {
          await this.loadUsers();
        }
        this.populateOwnerSelect('new-store-owner');
        this.populateManagerSelect('new-store-manager');
        const f1 = document.getElementById('new-owner-fields');
        const f2 = document.getElementById('new-manager-fields');
        if (f1) f1.style.display = 'none';
        if (f2) f2.style.display = 'none';
        this.openModal('modal-add-store');
      });
    }

    const btnAddAdmin = document.getElementById('btn-modal-add-admin');
    if (btnAddAdmin) {
      btnAddAdmin.addEventListener('click', () => {
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

    // Role selection change updates store dropdown visibility
    const roleSelect = document.getElementById('edit-user-role');
    const storeGroup = document.getElementById('group-edit-store');
    if (roleSelect && storeGroup) {
      roleSelect.addEventListener('change', () => {
        storeGroup.style.display = (roleSelect.value === 'super_admin') ? 'none' : 'block';
      });
    }
  },

  populateOwnerSelect(selectId) {
    const select = document.getElementById(selectId);
    if (!select) return;
    const currentVal = select.value;
    select.innerHTML = `
      <option value="">-- Select Existing Store Owner --</option>
      <option value="__new__">+ Create New Store Owner Account</option>
    `;
    if (this.users && this.users.length > 0) {
      this.users.forEach(u => {
        select.innerHTML += `<option value="${u.id}">${this.escape(u.full_name)} (@${this.escape(u.username)}) [${u.role}]</option>`;
      });
    }
    if (currentVal) select.value = currentVal;
  },

  populateManagerSelect(selectId) {
    const select = document.getElementById(selectId);
    if (!select) return;
    const currentVal = select.value;
    select.innerHTML = `
      <option value="">-- No Separate Manager (Same as Owner) --</option>
      <option value="__new__">+ Create New Store Manager Account</option>
    `;
    if (this.users && this.users.length > 0) {
      this.users.forEach(u => {
        select.innerHTML += `<option value="${u.id}">${this.escape(u.full_name)} (@${this.escape(u.username)}) [${u.role}]</option>`;
      });
    }
    if (currentVal) select.value = currentVal;
  },

  toggleNewOwnerFields() {
    const sel = document.getElementById('new-store-owner');
    const f = document.getElementById('new-owner-fields');
    if (f) f.style.display = (sel && sel.value === '__new__') ? 'block' : 'none';
  },

  toggleNewManagerFields() {
    const sel = document.getElementById('new-store-manager');
    const f = document.getElementById('new-manager-fields');
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
    // Add Store Form
    const formAddStore = document.getElementById('form-add-store');
    if (formAddStore) {
      formAddStore.addEventListener('submit', async (e) => {
        e.preventDefault();
        const storeName = document.getElementById('new-store-name').value.trim();
        const location = document.getElementById('new-store-location').value.trim();
        const description = document.getElementById('new-store-description')?.value.trim() || '';
        const ownerSelect = document.getElementById('new-store-owner')?.value || '';
        const managerSelect = document.getElementById('new-store-manager')?.value || '';

        const payload = {
          store_name: storeName,
          location: location,
          description: description
        };

        if (ownerSelect === '__new__') {
          payload.new_owner_name = document.getElementById('new-owner-fullname')?.value.trim();
          payload.new_owner_username = document.getElementById('new-owner-username')?.value.trim();
          payload.new_owner_email = document.getElementById('new-owner-email')?.value.trim();
          payload.new_owner_password = document.getElementById('new-owner-password')?.value.trim();
        } else if (ownerSelect) {
          payload.owner_id = parseInt(ownerSelect, 10);
        }

        if (managerSelect === '__new__') {
          payload.new_manager_name = document.getElementById('new-manager-fullname')?.value.trim();
          payload.new_manager_username = document.getElementById('new-manager-username')?.value.trim();
          payload.new_manager_email = document.getElementById('new-manager-email')?.value.trim();
          payload.new_manager_password = document.getElementById('new-manager-password')?.value.trim();
        } else if (managerSelect) {
          payload.manager_id = parseInt(managerSelect, 10);
        }

        try {
          const res = await fetch('../stockease_api/manage_stores.php?action=add', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify(payload)
          });
          const data = await res.json();
          if (data.success) {
            this.showToast('Store created successfully!', 'success');
            this.closeModals();
            formAddStore.reset();
            const f1 = document.getElementById('new-owner-fields');
            const f2 = document.getElementById('new-manager-fields');
            if (f1) f1.style.display = 'none';
            if (f2) f2.style.display = 'none';
            this.loadStores();
            this.loadDashboardData();
            this.loadStoreOwnersCards();
          } else {
            this.showToast(data.message || 'Failed to create store', 'error');
          }
        } catch (err) {
          this.showToast('Network error creating store', 'error');
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
            this.loadStoreOwnersCards();
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

    // Update Role Form
    const formUpdateRole = document.getElementById('form-update-role');
    if (formUpdateRole) {
      formUpdateRole.addEventListener('submit', async (e) => {
        e.preventDefault();
        const userId = parseInt(document.getElementById('edit-user-id').value, 10);
        const role = document.getElementById('edit-user-role').value;
        const storeId = document.getElementById('edit-user-store').value;

        try {
          const res = await fetch('../stockease_api/update_role.php', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ user_id: userId, role, store_id: storeId ? parseInt(storeId, 10) : null })
          });
          const data = await res.json();
          if (data.success) {
            this.showToast('User role updated successfully!', 'success');
            this.closeModals();
            this.loadUsers();
          } else {
            this.showToast(data.message || 'Failed to update user role', 'error');
          }
        } catch (err) {
          this.showToast('Network error updating user role', 'error');
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

        // Update header badge
        const badge = document.getElementById('live-plan-badge');
        if (badge) {
          badge.textContent = (sub.display_plan || 'StockEase Free') + ' (' + (sub.status || 'ACTIVE').toUpperCase() + ')';
          badge.className = 'header-pill plan-pill' + (isPrem ? '' : ' badge-slate');
        }

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
          return;
        }

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

      // Load Owner Cards
      this.loadStoreOwnersCards();

    } catch (err) {
      console.error(err);
      if (tbody) tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #DC2626; padding: 20px;">Error loading store metrics: ${err.message}</td></tr>`;
    }
  },

  async loadStoreOwnersCards() {
    const container = document.getElementById('container-store-owners-cards');
    if (!container) return;
    try {
      const res = await fetch('../stockease_api/get_store_owners.php', { credentials: 'include' });
      const data = await res.json();
      if (!data.success || !Array.isArray(data.owners) || data.owners.length === 0) {
        container.innerHTML = '<div style="color: #64748B; font-size: 13px; padding: 16px; grid-column: 1 / -1; text-align: center;">No stores found. Click "+ Add New Store" to register a store with owner and manager.</div>';
        return;
      }

      this.cachedPortfolios = data.owners;

      container.innerHTML = data.owners.map((owner, idx) => {
        const primaryStoreId = owner.primary_store_id || (owner.branches && owner.branches[0] ? owner.branches[0].id : 0);
        
        // Build contribution rows
        const contribRows = (owner.branches || []).map(b => `
          <div style="display: flex; justify-content: space-between; align-items: center; padding: 6px 0; border-bottom: 1px solid #E2E8F0; font-size: 12px;">
            <div style="font-weight: 600; color: #0F172A;">${this.escape(b.store_name)}</div>
            <div style="text-align: right;">
              <span style="font-weight: 700; color: #2563EB;">${b.contribution_pct}%</span>
              <span style="color: #64748B; font-size: 11px; margin-left: 6px;">(₱${(b.total_sales || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })})</span>
            </div>
          </div>
        `).join('');

        // Build designated branch cards
        const branchCardsHtml = (owner.branches || []).map(b => {
          // Top products list
          const topProdRows = (b.top_products || []).map(tp => `
            <tr>
              <td style="font-weight: 600;">${this.escape(tp.product_name)}</td>
              <td><span class="badge badge-info">${this.escape(tp.category_name)}</span></td>
              <td style="text-align: right; font-weight: 700;">${tp.quantity_sold}</td>
              <td style="text-align: right; color: #059669; font-weight: 700;">₱${(tp.gross_earnings || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
              <td style="text-align: right; color: #2563EB; font-weight: 700;">₱${(tp.net_profit || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
              <td style="text-align: center;"><span class="badge ${tp.stock_on_hand <= 5 ? 'badge-danger' : 'badge-slate'}">${tp.stock_on_hand}</span></td>
            </tr>
          `).join('');

          // Branch users
          const branchUserPills = (b.branch_users || []).map(u => `
            <div class="user-pill-card" style="background: #FFFFFF; border: 1px solid #CBD5E1; border-radius: 8px; padding: 8px 12px; cursor: pointer; display: flex; align-items: center; justify-content: space-between; margin-top: 6px;"
                 onclick='app.showUserDetail(${JSON.stringify(u).replace(/'/g, "&#39;")})'
                 title="Click to view full user information">
              <div style="display: flex; align-items: center; gap: 8px;">
                <div style="width: 28px; height: 28px; border-radius: 50%; background: #2563EB; color: #FFF; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700;">
                  ${this.escape((u.full_name || 'U').substring(0, 2).toUpperCase())}
                </div>
                <div>
                  <div style="font-size: 12px; font-weight: 700; color: #0F172A;">${this.escape(u.full_name)}</div>
                  <div style="font-size: 10px; color: #64748B;">@${this.escape(u.username)}</div>
                </div>
              </div>
              <span class="badge ${u.role === 'store_admin' ? 'badge-primary' : (u.role === 'manager' ? 'badge-info' : 'badge-success')}" style="font-size: 10px;">${this.escape(u.role_display || u.role)}</span>
            </div>
          `).join('');

          return `
            <div style="background: #F8FAFC; border: 1px solid #CBD5E1; border-radius: 10px; padding: 12px; margin-top: 10px;">
              <!-- Branch header (clickable) -->
              <div style="display: flex; justify-content: space-between; align-items: flex-start; cursor: pointer;"
                   onclick="app.toggleBranchDetail(${b.id})"
                   title="Click to expand real-time branch progress">
                <div>
                  <div style="font-size: 14px; font-weight: 700; color: #0F172A;">📍 ${this.escape(b.store_name)}</div>
                  <div style="font-size: 11px; color: #64748B; margin-top: 2px;">${this.escape(b.location || 'Retail Location')}</div>
                </div>
                <div style="display: flex; align-items: center; gap: 6px;">
                  <span class="badge ${b.status === 'Active' ? 'badge-success' : 'badge-danger'}" style="font-size: 10px;">${this.escape(b.status)}</span>
                  <span style="font-size: 11px; color: #2563EB; font-weight: 600;">▼ Details</span>
                </div>
              </div>

              <!-- Quick stats row -->
              <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-top: 8px; font-size: 11px; background: #FFFFFF; padding: 6px 10px; border-radius: 6px; border: 1px solid #EEF2F6;">
                <div>
                  <span style="color: #64748B;">Today:</span>
                  <strong style="color: #059669; display: block;">₱${(b.today_sales || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}</strong>
                </div>
                <div>
                  <span style="color: #64748B;">Total Sales:</span>
                  <strong style="color: #2563EB; display: block;">₱${(b.total_sales || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}</strong>
                </div>
                <div>
                  <span style="color: #64748B;">Products:</span>
                  <strong style="color: #0F172A; display: block;">${b.total_products || 0} items</strong>
                </div>
              </div>

              <!-- Collapsible Branch Detail Panel -->
              <div id="branch-detail-${b.id}" style="display: none; margin-top: 12px; padding-top: 12px; border-top: 1px dashed #CBD5E1;">
                <!-- Real-time Progress Line Chart -->
                <div style="background: #FFFFFF; border: 1px solid #E2E8F0; border-radius: 8px; padding: 10px; margin-bottom: 12px; cursor: pointer;"
                     onclick="app.toggleBranchProducts(${b.id})"
                     title="Click line chart to show top purchased products">
                  <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
                    <div style="font-size: 11px; font-weight: 700; color: #475569;">📈 Real-time Branch Sales Trajectory</div>
                    <span style="font-size: 10px; color: #2563EB;">(Click chart for top products)</span>
                  </div>
                  <div style="height: 100px; position: relative;">
                    <canvas id="chart-branch-${b.id}"></canvas>
                  </div>
                </div>

                <!-- Top Purchased Products (Collapsible or Revealed) -->
                <div id="branch-prods-${b.id}" style="margin-bottom: 12px;">
                  <div style="font-size: 11px; font-weight: 700; color: #334155; margin-bottom: 6px;">🏆 Top Purchased Products &amp; Profitability:</div>
                  ${topProdRows ? `
                    <div class="table-responsive" style="max-height: 180px; overflow-y: auto; background: #FFFFFF; border: 1px solid #E2E8F0; border-radius: 6px;">
                      <table style="font-size: 11px; margin: 0;">
                        <thead>
                          <tr>
                            <th>Product</th>
                            <th>Category</th>
                            <th style="text-align: right;">Sold</th>
                            <th style="text-align: right;">Gross Earned</th>
                            <th style="text-align: right;">Net Profit</th>
                            <th style="text-align: center;">On-Hand</th>
                          </tr>
                        </thead>
                        <tbody>${topProdRows}</tbody>
                      </table>
                    </div>
                  ` : '<div style="font-size: 11px; color: #94A3B8; padding: 6px;">No purchase transactions recorded yet.</div>'}
                </div>

                <!-- Branch Users & Roles Card -->
                <div style="background: #FFFFFF; border: 1px solid #E2E8F0; border-radius: 8px; padding: 10px;">
                  <div style="font-size: 11px; font-weight: 700; color: #334155; margin-bottom: 4px;">👥 Assigned Branch Users &amp; Roles:</div>
                  <div style="font-size: 10px; color: #64748B; margin-bottom: 6px;">Click any user to view full profile, roles, and duty status.</div>
                  ${branchUserPills || '<div style="font-size: 11px; color: #94A3B8;">No staff assigned to this branch.</div>'}
                </div>
              </div>
            </div>
          `;
        }).join('');

        return `
          <div style="background: #FFFFFF; border: 1px solid #CBD5E1; border-radius: 14px; padding: 20px; box-shadow: 0 2px 6px rgba(0,0,0,0.06); display: flex; flex-direction: column; justify-content: space-between;">
            <div>
              <!-- Top Part: Big Store Name & Store Owner -->
              <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 10px;">
                <div>
                  <h3 style="margin: 0; font-size: 20px; font-weight: 800; color: #0F172A; letter-spacing: -0.3px;">
                    ${this.escape(owner.store_name || owner.owner_name + ' Store Group')}
                  </h3>
                  <div style="font-size: 13px; color: #64748B; margin-top: 4px; font-weight: 500;">
                    👤 Store Owner: <strong style="color: #1E293B;">${this.escape(owner.owner_name)}</strong>
                    ${owner.manager_name ? `<span style="margin-left: 8px; color: #059669;">• Manager: <strong>${this.escape(owner.manager_name)}</strong></span>` : ''}
                  </div>
                </div>

                <!-- Option Button for Store Description -->
                <div>
                  <button class="btn btn-secondary btn-sm" 
                          onclick="app.openEditDescription(${primaryStoreId}, '${this.escape(owner.store_name)}', '${this.escape(owner.description || '')}')"
                          title="Click to view or edit store description"
                          style="font-size: 11px; padding: 4px 8px;">
                    ⚙️ Store Description
                  </button>
                </div>
              </div>

              <!-- Store Description Display -->
              ${owner.description ? `
                <div style="background: #F8FAFC; border-left: 3px solid #2563EB; padding: 6px 12px; font-size: 12px; color: #334155; margin-bottom: 12px; border-radius: 0 6px 6px 0;">
                  "${this.escape(owner.description)}"
                </div>
              ` : `
                <div style="font-size: 11px; color: #94A3B8; font-style: italic; margin-bottom: 12px;">
                  No store description added yet. Click "⚙️ Store Description" to add.
                </div>
              `}

              <!-- Aggregate Business Metrics -->
              <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; background: #F1F5F9; padding: 12px; border-radius: 10px; margin-bottom: 14px;">
                <div>
                  <span style="font-size: 11px; color: #475569;">Overall Business Revenue</span>
                  <div style="font-size: 18px; font-weight: 800; color: #0F172A;">₱${(owner.overall_sales || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
                </div>
                <div>
                  <span style="font-size: 11px; color: #475569;">Today's Progress</span>
                  <div style="font-size: 18px; font-weight: 800; color: #059669;">₱${(owner.overall_today_sales || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</div>
                </div>
                <div>
                  <span style="font-size: 11px; color: #475569;">Active Store Branches</span>
                  <div style="font-size: 14px; font-weight: 700; color: #2563EB;">${(owner.branches || []).length} Branches</div>
                </div>
                <div>
                  <span style="font-size: 11px; color: #475569;">Total Transactions</span>
                  <div style="font-size: 14px; font-weight: 700; color: #0F172A;">${owner.overall_transactions || 0} orders</div>
                </div>
              </div>

              <!-- Combined Business Progress Line Chart -->
              <div style="background: #FFFFFF; border: 1px solid #E2E8F0; border-radius: 10px; padding: 12px; margin-bottom: 10px; cursor: pointer;"
                   onclick="app.toggleBranchContribution('${idx}')"
                   title="Click line chart to see percentage contribution of each branch">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                  <strong style="font-size: 12px; color: #0F172A;">📈 Combined Business Progress Line Chart</strong>
                  <span style="font-size: 11px; color: #2563EB; font-weight: 600;">(Click chart for contribution %)</span>
                </div>
                <div style="height: 120px; position: relative;">
                  <canvas id="chart-portfolio-${idx}"></canvas>
                </div>
              </div>

              <!-- Branch Contribution Percentage Breakdown (Revealed on click) -->
              <div id="contrib-panel-${idx}" style="display: none; background: #F8FAFC; border: 1px solid #CBD5E1; border-radius: 8px; padding: 10px; margin-bottom: 12px;">
                <div style="font-size: 11px; font-weight: 700; color: #334155; margin-bottom: 4px;">Branch Sales Contribution Breakdown:</div>
                ${contribRows || '<div style="font-size: 11px; color: #94A3B8;">No branch sales data available yet.</div>'}
              </div>

              <!-- Designated Branch Cards Container -->
              <div style="margin-top: 14px;">
                <div style="font-size: 12px; font-weight: 700; color: #334155; margin-bottom: 4px;">Designated Branch Stores:</div>
                <div style="max-height: 380px; overflow-y: auto;">
                  ${branchCardsHtml || '<div style="font-size: 12px; color: #94A3B8; padding: 8px;">No branches assigned yet.</div>'}
                </div>
              </div>
            </div>

            <div style="margin-top: 16px; padding-top: 10px; border-top: 1px solid #E2E8F0; display: flex; justify-content: space-between; align-items: center;">
              <span style="font-size: 11px; color: #64748B;">✉ ${this.escape(owner.owner_email || 'No email')}</span>
              <button class="btn btn-outline-primary btn-sm" onclick="app.showTab('stores')" style="font-size: 11px;">Manage Stores &rarr;</button>
            </div>
          </div>
        `;
      }).join('');

      // Initialize all portfolio line charts
      setTimeout(() => {
        data.owners.forEach((owner, idx) => {
          this.initPortfolioChart(`chart-portfolio-${idx}`, owner.sales_progress || []);
          (owner.branches || []).forEach(b => {
            this.initBranchChart(`chart-branch-${b.id}`, b.sales_progress || []);
          });
        });
      }, 50);

    } catch (e) {
      console.error(e);
      container.innerHTML = '<div style="color: #DC2626; font-size: 13px; padding: 16px; grid-column: 1 / -1; text-align: center;">Error loading store portfolio cards.</div>';
    }
  },

  initPortfolioChart(canvasId, trendData) {
    const canvas = document.getElementById(canvasId);
    if (!canvas || !window.Chart) return;
    const labels = trendData.map(d => d.label || d.date);
    const sales = trendData.map(d => d.sales || 0);

    new Chart(canvas, {
      type: 'line',
      data: {
        labels: labels.length ? labels : ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
        datasets: [{
          label: 'Sales (₱)',
          data: sales.length ? sales : [0, 0, 0, 0, 0, 0, 0],
          borderColor: '#2563EB',
          backgroundColor: 'rgba(37, 99, 235, 0.1)',
          fill: true,
          tension: 0.35,
          borderWidth: 2,
          pointRadius: 3,
          pointBackgroundColor: '#2563EB'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          y: { beginAtZero: true, grid: { color: '#F1F5F9' }, ticks: { font: { size: 10 } } },
          x: { grid: { display: false }, ticks: { font: { size: 10 } } }
        }
      }
    });
  },

  initBranchChart(canvasId, trendData) {
    const canvas = document.getElementById(canvasId);
    if (!canvas || !window.Chart) return;
    const labels = trendData.map(d => d.label || d.date);
    const sales = trendData.map(d => d.sales || 0);

    new Chart(canvas, {
      type: 'line',
      data: {
        labels: labels.length ? labels : ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
        datasets: [{
          label: 'Branch Sales (₱)',
          data: sales.length ? sales : [0, 0, 0, 0, 0, 0, 0],
          borderColor: '#059669',
          backgroundColor: 'rgba(5, 150, 105, 0.08)',
          fill: true,
          tension: 0.35,
          borderWidth: 2,
          pointRadius: 2.5,
          pointBackgroundColor: '#059669'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          y: { beginAtZero: true, grid: { color: '#F8FAFC' }, ticks: { font: { size: 9 } } },
          x: { grid: { display: false }, ticks: { font: { size: 9 } } }
        }
      }
    });
  },

  toggleBranchContribution(idx) {
    const p = document.getElementById(`contrib-panel-${idx}`);
    if (p) {
      p.style.display = (p.style.display === 'none' || !p.style.display) ? 'block' : 'none';
    }
  },

  toggleBranchDetail(branchId) {
    const p = document.getElementById(`branch-detail-${branchId}`);
    if (p) {
      p.style.display = (p.style.display === 'none' || !p.style.display) ? 'block' : 'none';
    }
  },

  toggleBranchProducts(branchId) {
    const p = document.getElementById(`branch-prods-${branchId}`);
    if (p) {
      p.style.display = (p.style.display === 'none' || !p.style.display) ? 'block' : 'none';
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

  showUserDetail(u) {
    if (!u) return;
    const avatar = document.getElementById('detail-user-avatar');
    const fn = document.getElementById('detail-user-fullname');
    const un = document.getElementById('detail-user-username');
    const rl = document.getElementById('detail-user-role');
    const em = document.getElementById('detail-user-email');
    const st = document.getElementById('detail-user-status');
    const duty = document.getElementById('detail-user-duty');

    if (avatar) avatar.textContent = (u.full_name || 'U').substring(0, 2).toUpperCase();
    if (fn) fn.textContent = u.full_name || 'User';
    if (un) un.textContent = '@' + (u.username || 'username');
    if (rl) rl.textContent = u.role_display || u.role;
    if (em) em.textContent = u.email || 'No email provided';
    if (st) st.textContent = u.is_active ? 'Active' : 'Inactive';
    if (duty) duty.textContent = u.last_transaction_at ? `Last transaction on duty: ${u.last_transaction_at}` : 'No transactions recorded on duty yet';

    this.openModal('modal-user-detail');
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
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: #64748B; padding: 20px;">No stores found. Click "+ Add New Store" to create one.</td></tr>';
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
            <button class="btn btn-outline-secondary btn-sm" style="font-size: 10px; padding: 1px 6px; margin-left: 6px;" onclick="app.promptAssignOwner(${s.id}, ${s.owner_id || 0}, '${this.escape(s.store_name)}')">✎ Assign</button>
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

  async promptAssignOwner(storeId, currentOwnerId, storeName) {
    if (!this.users || this.users.length === 0) {
      await this.loadUsers();
    }
    const userOptions = this.users
      .map(u => `ID ${u.id}: ${u.full_name} (@${u.username}) [${u.role}]`)
      .join('\n');
    const input = prompt(
      `Assign Business Owner for "${storeName}" (Store #${storeId}):\n\nEnter the User ID of the business owner (or 0 for independent):\n\nAvailable Users:\n${userOptions}`,
      currentOwnerId || ""
    );
    if (input === null) return;
    const newOwnerId = parseInt(input.trim(), 10);
    try {
      const res = await fetch('../stockease_api/manage_stores.php?action=assign_owner', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          store_id: storeId,
          owner_id: (isNaN(newOwnerId) || newOwnerId <= 0) ? null : newOwnerId
        })
      });
      const data = await res.json();
      if (data.success) {
        this.showToast(data.message || 'Store owner updated!', 'success');
        this.loadStores();
      } else {
        this.showToast(data.message || 'Failed to update store owner', 'error');
      }
    } catch (e) {
      this.showToast('Network error updating store owner', 'error');
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
      let roleBadge = 'badge-slate';
      if (u.role === 'super_admin') roleBadge = 'badge-info';
      else if (u.role === 'store_admin') roleBadge = 'badge-warning';
      else if (u.role === 'staff') roleBadge = 'badge-success';

      const storeName = this.getStoreNameById(u.store_id);

      return `
        <tr>
          <td style="font-weight: 600; color: #64748B;">#${u.id}</td>
          <td style="font-weight: 600;">${this.escape(u.full_name || '—')}</td>
          <td>@${this.escape(u.username)}</td>
          <td style="color: #64748B;">${this.escape(u.email || '—')}</td>
          <td><span class="badge ${roleBadge}">${this.escape(u.role)}</span></td>
          <td>${this.escape(storeName)}</td>
          <td style="text-align: center;">
            <span class="badge ${u.is_active ? 'badge-success' : 'badge-danger'}">${u.is_active ? 'Active' : 'Inactive'}</span>
          </td>
          <td style="text-align: right;">
            <button class="btn btn-secondary btn-sm" onclick="app.openEditRoleModal(${u.id})">Edit Scope</button>
          </td>
        </tr>
      `;
    }).join('');
  },

  openEditRoleModal(userId) {
    const user = this.users.find(u => u.id === userId);
    if (!user) return;

    document.getElementById('edit-user-id').value = user.id;
    document.getElementById('edit-user-display').textContent = `${user.full_name} (@${user.username})`;
    document.getElementById('edit-user-role').value = user.role;

    this.populateStoreSelect('edit-user-store');
    document.getElementById('edit-user-store').value = user.store_id || '';

    const storeGroup = document.getElementById('group-edit-store');
    if (storeGroup) {
      storeGroup.style.display = (user.role === 'super_admin') ? 'none' : 'block';
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
          <td style="font-weight: 600;">${this.escape(a.store_name || 'Branch ' + a.store_id)}</td>
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

  async loadReports() {
    let storeId = this.currentReportStoreId;
    if (!storeId && this.stores.length > 0) {
      storeId = this.stores[0].id;
      this.currentReportStoreId = storeId;
      const select = document.getElementById('reports-store-select');
      if (select) select.value = storeId;
    }

    if (!storeId) return;

    // Dates based on period
    const now = new Date();
    let startDate = now.toISOString().split('T')[0];
    let endDate = startDate;

    if (this.currentReportPeriod === 'week') {
      const past = new Date();
      past.setDate(now.getDate() - 7);
      startDate = past.toISOString().split('T')[0];
    } else if (this.currentReportPeriod === 'month') {
      const past = new Date();
      past.setDate(now.getDate() - 30);
      startDate = past.toISOString().split('T')[0];
    }

    try {
      const url = `../stockease_api/get_store_reports.php?store_id=${storeId}&start_date=${startDate}&end_date=${endDate}`;
      const res = await fetch(url, { credentials: 'include' });
      const data = await res.json();
      if (!data.success) throw new Error(data.message);

      const s = data.summary || {};
      document.getElementById('rep-total-sales').textContent = '₱' + (s.total_sales || 0).toLocaleString('en-US', { minimumFractionDigits: 2 });
      document.getElementById('rep-total-tx').textContent = s.total_transactions || 0;
      document.getElementById('rep-avg-order').textContent = '₱' + (s.average_transaction || 0).toLocaleString('en-US', { minimumFractionDigits: 2 });
      document.getElementById('rep-units-sold').textContent = s.total_items_sold || 0;

      // Payment Methods
      const payContainer = document.getElementById('container-payment-methods');
      const payMethods = data.payment_methods || [];
      if (payContainer) {
        if (payMethods.length === 0) {
          payContainer.innerHTML = '<div style="color: #64748B;">No transactions in this period.</div>';
        } else {
          payContainer.innerHTML = payMethods.map(pm => `
            <div style="display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #F1F5F9;">
              <span style="font-weight: 600;">${this.escape(pm.method)} (${pm.count} orders)</span>
              <span style="font-weight: 700;">₱${parseFloat(pm.total || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}</span>
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
                <span style="color: #64748B; font-size: 11px; margin-left: 6px;">(${tp.quantity_sold} sold)</span>
              </div>
              <span style="font-weight: 700;">₱${parseFloat(tp.revenue || 0).toLocaleString('en-US', { minimumFractionDigits: 2 })}</span>
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

      // Fallback if no daily breakdown
      if (labels.length === 0) {
        labels.push('Today');
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
    }
  },

  exportReportsCSV() {
    if (!this.lastReportData) {
      this.showToast('No report data to export', 'error');
      return;
    }

    const d = this.lastReportData;
    let csv = "StockEase Branch Report\n";
    csv += `Store:,"${d.store ? d.store.store_name : 'Branch'}"\n`;
    csv += `Date Range:,"${d.date_range ? d.date_range.start : ''} to ${d.date_range ? d.date_range.end : ''}"\n\n`;

    csv += "Summary Metrics\n";
    csv += `Gross Revenue,${d.summary ? d.summary.total_sales : 0}\n`;
    csv += `Total Transactions,${d.summary ? d.summary.total_transactions : 0}\n`;
    csv += `Average Order Value,${d.summary ? d.summary.average_transaction : 0}\n`;
    csv += `Units Sold,${d.summary ? d.summary.total_items_sold : 0}\n\n`;

    csv += "Top Products\nProduct,Units Sold,Revenue\n";
    if (d.top_products) {
      d.top_products.forEach(p => {
        csv += `"${p.product_name}",${p.quantity_sold},${p.revenue}\n`;
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

    let url = '../stockease_api/get_audit_logs.php?';
    if (search) url += 'search=' + encodeURIComponent(search) + '&';
    if (action) url += 'action_filter=' + encodeURIComponent(action) + '&';

    try {
      const res = await fetch(url, { credentials: 'include' });
      const data = await res.json();
      if (!data.success) throw new Error(data.message);

      const logs = data.logs || [];
      if (logs.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: #64748B; padding: 20px;">No audit records found.</td></tr>';
        return;
      }

      tbody.innerHTML = logs.map(l => `
        <tr>
          <td style="color: #64748B; white-space: nowrap;">${l.created_at}</td>
          <td style="font-weight: 600;">${this.escape(l.user_name)}</td>
          <td><span class="badge badge-slate">${this.escape(l.role)}</span></td>
          <td style="font-weight: 600; color: #0F172A;">${this.escape(l.action)}</td>
          <td>${this.escape(l.target)}</td>
          <td style="color: #64748B;">${this.escape(l.store_name)}</td>
          <td style="color: #334155; font-size: 12px;">${this.escape(l.details)}</td>
        </tr>
      `).join('');
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #DC2626; padding: 20px;">Error: ${err.message}</td></tr>`;
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
