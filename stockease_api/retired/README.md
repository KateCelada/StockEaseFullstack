# Retired scripts — kept for history only

These files are **not part of the running system**. They live here so the API
folder stays clean while the history remains in git.

| File(s) | What they were |
|---|---|
| `apply_*.php` | Former PostgreSQL migration scripts. Retired when the project standardized on MySQL/XAMPP — they print a notice pointing at `stockease_api/schema_mysql.sql` and **no longer touch any database**. |
| `inspect_users_constraints.php` | Former PostgreSQL constraint diagnostic — same retirement treatment. |
| `migrate_profile_fields.sql` | One-off SQL for profile columns (already included in `schema_mysql.sql`). |
| `reset_system_clean.sql` | One-off reset SQL, kept as reference. |

The current database lives in `../schema_mysql.sql` (structure) and
`../seed_mysql.sql` (data).
