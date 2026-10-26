# StockEase — Device Verification Checklist

The four redesign phases changed ~200 things. The API side is smoke-tested
end-to-end (locally **and on the live Render deployment**), but the UI redesigns
still need a real-device pass — that remains the biggest risk. Walk this list
once on an emulator or device before calling the work done.

**Before you start:** StockEase runs on **MySQL/MariaDB (XAMPP) only** — the old
PostgreSQL migration scripts (`apply_*.php`) are retired and now print a notice
without touching the database. Schema and seed live in:

```
stockease_api\schema_mysql.sql   (import first)
stockease_api\seed_mysql.sql     (import second)
```

```
C:\xampp\mysql\bin\mysql.exe -u root -e "CREATE DATABASE IF NOT EXISTS stockease CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
C:\xampp\mysql\bin\mysql.exe -u root stockease < stockease_api\schema_mysql.sql
C:\xampp\mysql\bin\mysql.exe -u root stockease < stockease_api\seed_mysql.sql
```

**Two environments — same code, same schema:**

| | Local (XAMPP) | Live (cloud) |
|---|---|---|
| Panel | `http://localhost/stockease_api/StockEaseFullstack/stockease_admin/` | `https://stockeasefullstack.onrender.com` → `/stockease_admin/` |
| API (Android emulator) | `http://10.0.2.2/stockease_api/StockEaseFullstack/stockease_api/` | `https://stockeasefullstack.onrender.com/stockease_api/` |
| Database | XAMPP MariaDB `stockease` (port 3306) | TiDB Serverless MySQL `stockease` (Singapore, TLS via `tidb-ca.pem`) |
| Runtime | Apache/PHP from XAMPP | Render (Docker, PHP 8.5, `router.php`) |

- Live seed login: `superadmin / admin123` — **change it after the demo**.
- Render free tier sleeps after ~15 min idle → the first live load takes ~20–40 s
  (cold start), then it is fast. That is not an outage.
- `stockease_api/db.php` picks the database from environment variables (Render)
  with the XAMPP values as local fallback — no code difference between the two.

**Test matrix:** walk steps 1–10 once in **light**, then repeat the marked steps in
**dark** (step 11). Everything else looks identical in both modes by design.

---

## 1. Launch and login

- [ ] App opens, no crash, no `Resources$NotFoundException` (would mean a deleted
      token is still referenced somewhere).
- [ ] Login screen renders with Inter, not the system font.

## 2. Sidebar accordion — every screen

Own-dashboard sidebars: `DashboardActivity`, `CashierDashboardActivity`,
`StoreAdminDashboardActivity`, `SuperAdminDashboardActivity`, `ProfileActivity`,
`AuditLogsActivity`.

Shared sidebar (`view_sidebar.xml` + `SidebarNav.attach(...)`): `POSActivity`,
`InventoryActivity`, `LowStockActivity`, `ProductsActivity`,
`TransactionHistoryActivity`, `ReportsActivity`, `StockTransfersActivity`,
`StaffActivity`, `StoreAdminsActivity`, `StoresActivity`, `UsersActivity`,
`StockEaseInsightsActivity`, `BusinessProgressActivity`, `SubscriptionActivity`,
`SettingsActivity`.

- [ ] Open each; the sidebar renders with **dark slate background** and light text.
- [ ] **Navigate via the sidebar to any screen and back — the sidebar is still
      there on every screen** (panelist request: it used to exist on only 7 layouts).
- [ ] Tapping a group expands/collapses it — chevron flips `▸` → `▾`.
- [ ] Exactly **one** group open at a time (accordion, not independent toggles).
- [ ] Current screen's item is visually marked active.
- [ ] Role gating: staff don't see Reports/Transfers/Staff/Branches; inventory
      checkers don't see POS; managers/store owners see Staff/Store Admins/Branches.
- [ ] **Dark mode:** sidebar still reads correctly (this was the `sidebar_bg` ≡
      `text_primary` swap — a no-op in light, the actual fix in dark).

## 3. Profile — load and edit

- [ ] Open Profile: fields populate from `get_user_profile.php`, no crash.
- [ ] Edit phone / address / occupation / sex; Save.
- [ ] **Reopen the app and re-open Profile** — values persisted (proves the write
      path through `update_profile.php` → MySQL, not just local state).
- [ ] If save fails, the error is a readable message, not a raw SQL error.

## 4. Skeleton → content transition (7 list screens)

`InventoryActivity`, `ProductsActivity`, `StaffActivity`, `StoreAdminsActivity`,
`StoresActivity`, `TransactionHistoryActivity`, `UsersActivity`.

For each:
- [ ] On open, **skeleton bars are visible immediately** (they start `visible` in XML).
- [ ] When data arrives, skeleton **fades out over ~150ms** — not an instant snap.
- [ ] Real rows render; no fabricated defaults (totals should show `₱0.00`, not invented numbers).

## 5. Empty state (force one on any list screen)

- [ ] With no data, the empty state **fades in** with title + message + Retry.
- [ ] Retry actually re-fetches.
- [ ] Low-stock empty state shows its own copy (not the shared generic one).

## 6. Interactive cards

- [ ] Dashboard stat/plan cards respond to tap (ripple + action), not dead.
- [ ] POS product rows tap correctly and the row background reads right.
- [ ] Subscription plan cards show real pricing: **₱699/mo**, **₱6,990/yr**, savings **₱1,398**.

## 7. Navigation transitions

- [ ] Move between screens — transitions are smooth, ~150–250ms.
- [ ] Back navigation does not double-animate or flash.

## 8. Buttons (16 converted to MaterialButton)

- [ ] Primary buttons: blue fill, white label.
- [ ] Secondary buttons: outlined/tonal, not filled.
- [ ] Any success action (e.g. yearly plan): green fill.
- [ ] 3 deliberate exceptions still look *distinctly* different: `btnVoidTx`
      (soft danger) and 2 pale badge buttons.
- [ ] **No button label is invisible** — if white-on-white appears, the explicit
      `android:textColor` was lost in conversion.

## 9. Typography

- [ ] Screen titles are large/heavy; body text is regular — clear hierarchy.
- [ ] **No double-bold** anywhere (a title that looks smeared/too heavy).
- [ ] No text is clipped or truncated in table cells.

## 10. Glyphs and data  ⚠️ *easy to get wrong per-locale*

- [ ] Currency renders **₱** everywhere, not `P` or a tofu box.
- [ ] Chevrons render **▸ ▾**, star **★**, arrow **→**.
- [ ] **No emoji anywhere** on any screen.
- [ ] Any number that is genuinely zero shows `₱0.00` — that is honest, not fabricated.

## 11. Dark mode — full pass

Switch to dark and re-check:
- [ ] **Surfaces**: cards/lists use `card_background`. If any surface is **pure white**,
      it was left on `@color/white` (which has no night override).
- [ ] **Text**: every label legible. Blue text in dark = a token missing its night value.
- [ ] **Sidebar** (step 2) and **buttons** (step 8) again.
- [ ] **Dialogs** below.

## 12. Dialogs

- [ ] Add Staff, Compare Stores, and any add/edit dialogs open with correct theming.
- [ ] Dialog buttons follow the Primary/Secondary styles.
- [ ] Compare Stores: accent + success colours read correctly (**not** the old purple).

## 13. Roles

- [ ] `super_admin` / `store_admin` / `manager` / `store_owner` reach admin screens.
- [ ] `staff` (displayed as Cashier) and `inventory_checker` are scoped to staff
      surfaces and **cannot** reach Settings (bounces to login).

---

## 14. Panelist feedback — final verification

Item 1 (MySQL only) is the setup block above; item 2 (sidebar everywhere) is step 2.
The remaining five:

**Item 3 — POS category creation**
- [ ] POS → add-category control: create a category **inside POS**; it appears in
      the category filter without restarting the app.
- [ ] Picking a category filters the product grid; clearing it shows all products again.

**Item 4 — Other sales report (manual sales)**
- [ ] Web panel: create a manual sale (non-POS). It is stored with `source = manual`.
- [ ] Reports: it appears under **Other sales**, separate from POS sales.
- [ ] Voided/refunded manual sales are still listed there, with their status shown.
- [ ] App: manual sale entry works and the list shows it after refresh.

**Item 5 — Password strength**
- [ ] Register / change password with something weak (e.g. `abc`) → blocked with a
      readable message (min 8 characters, needs a letter **and** a number).
- [ ] A strong password passes; web and app enforce the **same** rules.

**Item 6 — Store-admin null values**
- [ ] Log in as `john` (store_admin): dashboards, reports and lists show **no**
      `null`, `undefined` or `-1` anywhere.

**Item 7 — Transfer status logging**
- [ ] Create a transfer, then change its status (approve → dispatch → receive, or
      decline/cancel) → **every change adds a timeline entry** (`transfer_events`).
- [ ] The **requesting store admin** sees the full history on their account
      (app timeline + web transfer view).

---

## If something fails

Everything is version-controlled: local `git` + GitHub
(`KateCelada/StockEaseFullstack`, branch `main`, pushed). To undo a bad change:
`git log --oneline` → `git revert <commit>` → push. Extra restore points: the SQL
dump `C:\xampp\htdocs\stockease_api\stockease_backup_*.sql` and the full repo
bundle `C:\xampp\htdocs\stockease_api\StockEase-full-backup.bundle`.
Report the exact screen + symptom rather than editing further.

Known and deliberately left as-is (do **not** file these as bugs):
- 29 off-scale Java-built view sizes (13/10/15/18/26 sp) — consistent per role,
  left alone to avoid table-cell truncation.
- "Smart Recommendation" badge wording.
- `activity_main.xml` / `activity_transactions.xml` are **dead files** — no activity
  references them. Harmless; not deleted (git history covers them either way).
