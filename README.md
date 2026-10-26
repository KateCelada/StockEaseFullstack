# StockEase

Multi-branch **POS, inventory and stock-transfer system** — Android app, PHP API
and a web admin panel, all in this one repository.

- **Live panel:** https://stockeasefullstack.onrender.com/
- **Live API health check:** https://stockeasefullstack.onrender.com/stockease_api/health.php
- **App source:** [`StockEase/`](./StockEase) · **API source:** [`stockease_api/`](./stockease_api)

## What is in this repo

| Path | What it is |
|---|---|
| `StockEase/` | Android app (Java, Gradle) — POS, inventory, transfers, reports |
| `stockease_api/` | PHP API — endpoints, MySQL schema + seed, password policy |
| `stockease_admin/` | Web admin panel (PHP + vanilla JS, served at `/stockease_admin/`) |
| `router.php` | Router for PHP's built-in server (panel at `/`, API at `/stockease_api/`) |
| `Dockerfile`, `.dockerignore`, `tidb-ca.pem` | Render deployment (PHP 8.5 in Docker, TLS CA for the database) |
| `DEVICE_CHECKLIST.md` | Device verification checklist, incl. all panelist-feedback checks |

## Stack

- **Database: MySQL only.** Local development uses XAMPP MariaDB; the live site
  uses TiDB Serverless (MySQL-compatible, Singapore) over TLS. The old
  PostgreSQL migration scripts are retired — see `stockease_api/retired/`.
- **API:** PHP 8.5, PDO, file-based sessions. `stockease_api/db.php` reads
  `DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASS / DB_SSL_CA` from environment
  variables (Render) and falls back to the XAMPP defaults when they are absent —
  the same code runs in both places.
- **App:** one switch for the server address — `ApiConfig.BASE_URL`.

## Run it locally (XAMPP)

```bat
C:\xampp\mysql\bin\mysql.exe -u root stockease < stockease_api\schema_mysql.sql
C:\xampp\mysql\bin\mysql.exe -u root stockease < stockease_api\seed_mysql.sql
```

- Panel: `http://localhost/stockease_api/StockEaseFullstack/stockease_admin/`
- API for the Android **emulator**: `http://10.0.2.2/stockease_api/StockEaseFullstack/stockease_api/`
  (set in `ApiConfig.java`)

## Deployed version (already set up)

- **Render** (Docker): start command `php -S 0.0.0.0:$PORT router.php`,
  environment variables pointing at TiDB (`DB_*`, `DB_SSL_CA=tidb-ca.pem`).
- The free instance sleeps after ~15 minutes idle — the first visit then takes
  ~20–40 s (cold start).

## Seed logins

| Username | Role | Password |
|---|---|---|
| `superadmin` | super admin | `admin123` — **change after the demo** |
| `katecelada` | store owner | *(as seeded)* |
| `laurence` | manager | *(as seeded)* |
| `john` | store admin | *(as seeded)* |
| `neil` | staff (cashier) | *(as seeded)* |
| `xander` | inventory checker | *(as seeded)* |

## Panelist feedback — all 7 addressed

1. **MySQL/XAMPP only** — PostgreSQL dropped; retired scripts kept in `stockease_api/retired/`.
2. **Sidebar visible on every screen** — shared `SidebarNav` across 15 screens.
3. **POS category creation** — add a category inside POS; category filtering.
4. **Other sales report** — manual (non-POS) sales with `source`, web + app entry,
   voided/refunded included.
5. **Password strength** — same rules enforced in app, web and API (`password_policy.php`).
6. **No null values** for store-admin views.
7. **Transfer status logging** — every status change is stored in `transfer_events`
   and shown to the requesting store admin.
