# StockEase — Device Verification Checklist

The four redesign phases changed ~200 things and **none of it has ever rendered on a
device**. This is the single biggest remaining risk. Walk this list once on an
emulator or device before calling the work done.

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

## 3. Profile — load and edit  ⚠️ *depends on the migration*

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
- [ ] `cashier` is scoped to staff surfaces and **cannot** reach Settings (bounces to login).

---

## If something fails

There is **no version control** on this working tree — nothing can be rolled back.
Report the exact screen + symptom rather than editing further.

Known and deliberately left as-is (do **not** file these as bugs):
- 29 off-scale Java-built view sizes (13/10/15/18/26 sp) — consistent per role,
  left alone to avoid table-cell truncation.
- "Smart Recommendation" badge wording.
- `activity_main.xml` / `activity_transactions.xml` are **dead files** — no activity
  references them. Harmless; not deleted (no VCS to restore from).
