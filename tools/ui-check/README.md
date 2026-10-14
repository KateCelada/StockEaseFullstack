# StockEase UI check (`tools/ui-check`)

Deterministic, dependency-free audit for the tells that make an Android UI
look AI-generated. No network, no new app dependencies — just Python 3 + Gradle.

## Run

From the `StockEase/` project root:

```bash
# Windows
py tools/ui-check/check_ui.py --res app/src/main/res
# macOS / Linux
python3 tools/ui-check/check_ui.py --res app/src/main/res

# Full baseline reports
py tools/ui-check/check_ui.py --res app/src/main/res \
  --json-out tools/ui-check/baseline-report.json \
  --md-out tools/ui-check/baseline-report.md

# Via Gradle (same script, cross-platform python detection)
./gradlew :app:checkStockEaseUi
```

Exit code `0` = pass (no `error`-severity findings by default).
Exit code `1` = errors found. Warnings are advisory unless `--fail-on warning`.

## Rules

| Rule | Severity | What it catches |
|---|---|---|
| STOCK-001 | error | Hardcoded `#RRGGBB` in layout attrs — use `@color/*` |
| STOCK-002 | error | Emoji / geometric glyph (`🛒 ● ↪`) in `text`/`hint` — use vector |
| STOCK-003 | warning | Hardcoded `textSize` without `textAppearance`/`StockEase` style |
| STOCK-004 | warning | Legacy `<Button backgroundTint>` — use `MaterialButton` style |
| STOCK-005 | warning | `ImageView`/`ImageButton` missing `contentDescription` |
| STOCK-006 | warning | Hardcoded `android:text` literal — extract to `@string` |
| STOCK-007 | warning | `@android:color/*` framework color — use StockEase token |
| STOCK-008 | warning | Off-grid spacing (grid = `4dp`, see config) |
| STOCK-009 | warning | Flat `0/1dp` white `CardView` — use AppCard style |
| STOCK-010 | error | Hardcoded `#...` inside `drawable/*.xml` — use `@color` |

Tune in `ui-check-config.json` (`enabled`, `severity`, `allowlist`, `failOn`, `gridDp`).

## Tests

```bash
py -m unittest discover -s tools/ui-check -p "test_*.py"
```
