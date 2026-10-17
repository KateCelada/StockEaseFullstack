#!/usr/bin/env python3
"""StockEase UI design audit (STOCK-001 .. STOCK-010).

Scans Android ``res/layout`` + ``res/drawable*`` XML for the tells that make
an app look AI-generated: hardcoded hex colors, emoji-as-icons, ad-hoc
text sizes, legacy Buttons, missing contentDescriptions, hardcoded strings,
framework colors, off-grid spacing, and flat 1dp white cards.

Usage (from the StockEase/ project root):
    py tools/ui-check/check_ui.py --res app/src/main/res
    py tools/ui-check/check_ui.py --res app/src/main/res \
        --json-out tools/ui-check/baseline-report.json \
        --md-out tools/ui-check/baseline-report.md

Exit code is 0 when no finding matches the configured ``failOn`` severities
(default: ["error"]), 1 otherwise. Warnings never break the build unless
``--fail-on warning`` is passed.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_NS = "http://schemas.android.com/apk/res/android"
APP_NS = "http://schemas.android.com/apk/res-auto"
TOOLS_NS = "http://schemas.android.com/tools"

HEX_COLOR_RE = re.compile(r"^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{4}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
DIMEN_DP_RE = re.compile(r"^(-?\d+(?:\.\d+)?)(dp|dip)$")
FRAMEWORK_COLOR_PREFIX = "@android:color/"

COLOR_ATTRS = {
    (ANDROID_NS, "background"),
    (ANDROID_NS, "textColor"),
    (ANDROID_NS, "textColorHint"),
    (ANDROID_NS, "backgroundTint"),
    (ANDROID_NS, "strokeColor"),
    (ANDROID_NS, "hintTextColor"),
    (ANDROID_NS, "iconTint"),
    (APP_NS, "cardBackgroundColor"),
    (APP_NS, "strokeColor"),
    (APP_NS, "boxStrokeColor"),
    (APP_NS, "boxBackgroundColor"),
    (APP_NS, "iconTint"),
    (APP_NS, "backgroundTint"),
}

SPACING_ATTRS = {
    "padding", "paddingHorizontal", "paddingVertical",
    "paddingStart", "paddingEnd", "paddingTop", "paddingBottom",
    "paddingLeft", "paddingRight",
    "layout_margin", "layout_marginStart", "layout_marginEnd",
    "layout_marginTop", "layout_marginBottom",
    "layout_marginLeft", "layout_marginRight",
}

TEXT_SIZE_HOSTS = {
    "TextView", "EditText", "Button", "AutoCompleteTextView",
    "MaterialButton", "MaterialTextView", "CheckBox", "RadioButton",
}

DRAWABLE_COLOR_ATTRS = {"color", "startColor", "endColor", "centerColor"}

# Code points commonly abused as icons in generated layouts.
_ICON_RANGES = (
    (0x2190, 0x21FF),   # arrows (↪ …)
    (0x25A0, 0x25FF),   # geometric shapes (● ■ …)
    (0x2600, 0x27BF),   # misc symbols + dingbats (🛒 📦 🔔 …)
    (0x2B00, 0x2BFF),   # misc symbols and arrows
    (0x1F000, 0x1FAFF), # emoticons, pictographs, etc.
)
_VARIATION_SELECTOR = 0xFE0F


def contains_icon_glyph(text: str) -> bool:
    """Return True if *text* contains an emoji / symbol used as an icon."""
    for ch in text:
        code = ord(ch)
        if code == _VARIATION_SELECTOR:
            return True
        for low, high in _ICON_RANGES:
            if low <= code <= high:
                return True
    return False


def localname(tag: str) -> str:
    return tag.rsplit("}", 1)[-1] if "}" in tag else tag


class LineLocator:
    """Distribute repeated needles across successive occurrences.

    ElementTree drops line numbers, so several <TextView>s with the same
    ``textColor="#FFFFFF"`` would otherwise all report the first matching
    line. The locator remembers a cursor per needle and returns the next
    occurrence each time.
    """

    def __init__(self, lines: list[str]) -> None:
        self.lines = lines
        self.cursors: dict[str, int] = {}

    def locate(self, needle: str) -> int | None:
        if not needle:
            return None
        start = self.cursors.get(needle, 1)
        for idx in range(start - 1, len(self.lines)):
            if needle in self.lines[idx]:
                self.cursors[needle] = idx + 2
                return idx + 1
        for idx, line in enumerate(self.lines):
            if needle in line:
                self.cursors[needle] = idx + 2
                return idx + 1
        return None


class Auditor:
    def __init__(self, config: dict) -> None:
        self.rules_cfg: dict = config.get("rules", {})
        self.grid_dp: int = int(config.get("gridDp", 4))
        self.allowlist: dict = config.get("allowlist", {})
        self.fail_on: list[str] = list(config.get("failOn", ["error"]))
        self.findings: list[dict] = []

    def enabled(self, rule: str) -> bool:
        return bool(self.rules_cfg.get(rule, {}).get("enabled", True))

    def severity(self, rule: str, default: str) -> str:
        return str(self.rules_cfg.get(rule, {}).get("severity", default))

    def allowed(self, rule: str, value: str) -> bool:
        return value in self.allowlist.get(rule, [])

    def add(self, *, rule: str, severity: str, path: Path, res_root: Path,
            line: int | None, message: str, attribute: str = "",
            value: str = "", suggestion: str = "") -> None:
        if not self.enabled(rule):
            return
        try:
            rel = str(path.relative_to(res_root.parent))
        except ValueError:
            rel = str(path)
        self.findings.append({
            "rule": rule,
            "severity": self.severity(rule, severity),
            "file": rel.replace("\\", "/"),
            "line": line,
            "message": message,
            "attribute": attribute,
            "value": value,
            "suggestion": suggestion,
        })

    # -- per-file audits -------------------------------------------------
    def audit_layout(self, path: Path, res_root: Path) -> None:
        try:
            text = path.read_text(encoding="utf-8")
            lines = text.splitlines()
            root = ET.fromstring(text)
        except (OSError, ET.ParseError) as exc:
            self.add(rule="STOCK-001", severity="error", path=path, res_root=res_root,
                     line=None, message=f"Unparseable XML: {exc}", suggestion="Fix XML syntax.")
            return

        locator = LineLocator(lines)
        for elem in root.iter():
            tag = localname(elem.tag)

            # STOCK-001: hardcoded hex colors in attributes.
            if self.enabled("STOCK-001"):
                for raw_key, raw_val in elem.attrib.items():
                    if not raw_key.startswith("{"):
                        continue
                    uri, ln = raw_key[1:].split("}", 1)
                    if (uri, ln) in COLOR_ATTRS and raw_val and HEX_COLOR_RE.match(raw_val):
                        line = locator.locate( f'{ln}="{raw_val}"')
                        self.add(rule="STOCK-001", severity="error", path=path,
                                 res_root=res_root, line=line,
                                 message=f"Hardcoded color {raw_val} on <{tag}>.",
                                 attribute=ln, value=raw_val,
                                 suggestion="Use @color/* token or ?attr/* (see colors.xml).")

            # STOCK-007: framework colors.
            if self.enabled("STOCK-007"):
                for raw_key, raw_val in elem.attrib.items():
                    if raw_val and raw_val.startswith(FRAMEWORK_COLOR_PREFIX):
                        ln = raw_key.rsplit("}", 1)[-1]
                        line = locator.locate( raw_val)
                        self.add(rule="STOCK-007", severity="warning", path=path,
                                 res_root=res_root, line=line,
                                 message=f"Framework color {raw_val} on <{tag}>.",
                                 attribute=ln, value=raw_val,
                                 suggestion="Map to a StockEase @color token (text_primary/secondary, card_border…).")

            # STOCK-002: emoji / glyph icons in user-visible strings.
            if self.enabled("STOCK-002"):
                for attr_name in ("text", "hint", "contentDescription"):
                    val = elem.get(f"{{{ANDROID_NS}}}{attr_name}")
                    if val and contains_icon_glyph(val):
                        line = locator.locate( val[:24])
                        self.add(rule="STOCK-002", severity="error", path=path,
                                 res_root=res_root, line=line,
                                 message=f"Icon glyph in android:{attr_name}={val!r} on <{tag}>.",
                                 attribute=attr_name, value=val,
                                 suggestion="Use an ic_* vector drawable + TextView compound drawable instead.")

            # STOCK-003: hardcoded textSize without textAppearance.
            if self.enabled("STOCK-003") and tag in TEXT_SIZE_HOSTS:
                size = elem.get(f"{{{ANDROID_NS}}}textSize")
                appearance = elem.get(f"{{{ANDROID_NS}}}textAppearance")
                style = elem.get(f"{{{ANDROID_NS}}}style", "") or elem.get("style", "")
                if size and not appearance and "TextAppearance" not in style and "StockEase" not in style:
                    line = locator.locate( f'textSize="{size}"')
                    self.add(rule="STOCK-003", severity="warning", path=path,
                             res_root=res_root, line=line,
                             message=f"Hardcoded textSize {size} on <{tag}> without textAppearance.",
                             attribute="textSize", value=size,
                             suggestion="Use ?attr/textAppearanceTitleLarge etc. or Widget.StockEase.Text.* style.")

            # STOCK-004: legacy Button + backgroundTint.
            if self.enabled("STOCK-004") and tag == "Button":
                tint = elem.get(f"{{{ANDROID_NS}}}backgroundTint")
                if tint:
                    line = locator.locate( f'backgroundTint="{tint}"')
                    self.add(rule="STOCK-004", severity="warning", path=path,
                             res_root=res_root, line=line,
                             message=f"Legacy <Button> with backgroundTint {tint}.",
                             attribute="backgroundTint", value=tint,
                             suggestion="Use <com.google.android.material.button.MaterialButton> with Widget.StockEase.Button.*.")

            # STOCK-005: images without contentDescription.
            if self.enabled("STOCK-005") and tag in ("ImageView", "ImageButton"):
                desc = elem.get(f"{{{ANDROID_NS}}}contentDescription")
                if not (desc or "").strip():
                    line = locator.locate( f"<{tag}")
                    self.add(rule="STOCK-005", severity="warning", path=path,
                             res_root=res_root, line=line,
                             message=f"<{tag}> missing android:contentDescription.",
                             attribute="contentDescription", value="",
                             suggestion="Add android:contentDescription=\"@string/...\" (or tools:ignore for decorative).")

            # STOCK-006: hardcoded android:text literals.
            if self.enabled("STOCK-006"):
                val = elem.get(f"{{{ANDROID_NS}}}text")
                if val and not val.startswith("@") and not val.startswith("?") and not self.allowed("STOCK-006", val):
                    line = locator.locate( f'text="{val[:32]}')
                    self.add(rule="STOCK-006", severity="warning", path=path,
                             res_root=res_root, line=line,
                             message=f"Hardcoded android:text={val!r} on <{tag}>.",
                             attribute="text", value=val,
                             suggestion="Extract to @string/* for i18n and review.")

            # STOCK-008: off-grid spacing.
            if self.enabled("STOCK-008"):
                for raw_key, raw_val in elem.attrib.items():
                    ln = raw_key.rsplit("}", 1)[-1]
                    if ln not in SPACING_ATTRS or not raw_val:
                        continue
                    m = DIMEN_DP_RE.match(raw_val.strip())
                    if not m:
                        continue
                    number = float(m.group(1))
                    if number % self.grid_dp != 0 and raw_val not in self.allowlist.get("STOCK-008", []):
                        line = locator.locate( f'{ln}="{raw_val}"')
                        self.add(rule="STOCK-008", severity="warning", path=path,
                                 res_root=res_root, line=line,
                                 message=f"Off-grid spacing {ln}={raw_val} (grid is {self.grid_dp}dp).",
                                 attribute=ln, value=raw_val,
                                 suggestion=f"Snap to the {self.grid_dp}dp grid (4/8/12/16/20/24dp…).")

            # STOCK-009: flat low-elevation white cards.
            if self.enabled("STOCK-009") and (tag in ("CardView", "MaterialCardView") or elem.tag.endswith("CardView")):
                elev = elem.get(f"{{{APP_NS}}}cardElevation", "")
                bg = elem.get(f"{{{APP_NS}}}cardBackgroundColor", "")
                if elev.strip() in ("0dp", "1dp", "0dip", "1dip"):
                    line = locator.locate( f'cardElevation="{elev}"')
                    self.add(rule="STOCK-009", severity="warning", path=path,
                             res_root=res_root, line=line,
                             message=f"Flat card (elevation {elev}, bg {bg or 'default white'}).",
                             attribute="cardElevation", value=elev,
                             suggestion="Use 12–16dp radius, 1dp @color/card_border stroke + 2dp elevation, or the StockEase AppCard style.")

    def audit_drawable(self, path: Path, res_root: Path) -> None:
        try:
            text = path.read_text(encoding="utf-8")
            lines = text.splitlines()
            root = ET.fromstring(text)
        except (OSError, ET.ParseError):
            return
        if not self.enabled("STOCK-010"):
            return
        locator = LineLocator(lines)
        for elem in root.iter():
            tag = localname(elem.tag)
            for raw_key, raw_val in elem.attrib.items():
                ln = raw_key.rsplit("}", 1)[-1]
                if ln in DRAWABLE_COLOR_ATTRS and raw_val and HEX_COLOR_RE.match(raw_val):
                    line = locator.locate( f'{ln}="{raw_val}"')
                    self.add(rule="STOCK-010", severity="error", path=path,
                             res_root=res_root, line=line,
                             message=f"Hardcoded color {raw_val} in drawable <{tag}>.",
                             attribute=ln, value=raw_val,
                             suggestion="Reference @color/* so dark theme and rebrand stay consistent.")


def write_markdown(report_path: Path, summary: dict, findings: list[dict]) -> None:
    by_rule: dict[str, list[dict]] = {}
    for finding in findings:
        by_rule.setdefault(finding["rule"], []).append(finding)
    by_file: dict[str, int] = {}
    for finding in findings:
        by_file[finding["file"]] = by_file.get(finding["file"], 0) + 1
    top_files = sorted(by_file.items(), key=lambda kv: kv[1], reverse=True)[:15]

    out = []
    out.append("# StockEase UI audit — baseline report")
    out.append("")
    out.append(f"Files scanned: **{summary['files']}** · "
               f"findings: **{summary['total']}** "
               f"(errors **{summary['errors']}**, warnings **{summary['warnings']}**).")
    out.append("")
    out.append("Fail gate: `failOn: [error]` — Gradle `checkStockEaseUi` fails only on errors; "
               "warnings are advisory for the redesign.")
    out.append("")
    out.append("## Findings by rule")
    out.append("")
    out.append("| Rule | Severity | Count | Meaning |")
    out.append("|---|---|---|---|")
    rule_help = {
        "STOCK-001": "Hardcoded hex color",
        "STOCK-002": "Emoji / glyph as icon",
        "STOCK-003": "textSize without textAppearance",
        "STOCK-004": "Legacy Button + backgroundTint",
        "STOCK-005": "Image missing contentDescription",
        "STOCK-006": "Hardcoded android:text",
        "STOCK-007": "@android:color framework color",
        "STOCK-008": "Off-grid spacing",
        "STOCK-009": "Flat 1dp white card",
        "STOCK-010": "Hardcoded color in drawable",
    }
    for rule in sorted(by_rule):
        sev = by_rule[rule][0]["severity"]
        out.append(f"| {rule} | {sev} | {len(by_rule[rule])} | {rule_help.get(rule, '')} |")
    out.append("")
    out.append("## Top files by finding count")
    out.append("")
    for name, count in top_files:
        out.append(f"- `{name}` — {count}")
    out.append("")
    out.append("## All findings")
    out.append("")
    for finding in sorted(findings, key=lambda f: (f["file"], f["line"] or 0)):
        loc = f"`{finding['file']}`" + (f":{finding['line']}" if finding["line"] else "")
        out.append(f"- **{finding['rule']}** ({finding['severity']}) {loc} — {finding['message']}")
        if finding.get("suggestion"):
            out.append(f"  - Fix: {finding['suggestion']}")
    out.append("")
    report_path.write_text("\n".join(out), encoding="utf-8")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="StockEase UI design audit.")
    parser.add_argument("--res", default="app/src/main/res",
                        help="Path to Android res directory (default: app/src/main/res).")
    parser.add_argument("--config", default="tools/ui-check/ui-check-config.json",
                        help="Path to ui-check-config.json.")
    parser.add_argument("--json-out", default="",
                        help="Write machine-readable JSON report to this path.")
    parser.add_argument("--md-out", default="",
                        help="Write human-readable Markdown report to this path.")
    parser.add_argument("--fail-on", choices=["error", "warning", "none"], default="",
                        help="Override config failOn gate.")
    parser.add_argument("--quiet", action="store_true", help="Only print the summary line.")
    args = parser.parse_args(argv)

    res_root = Path(args.res)
    if not res_root.is_dir():
        print(f"ui-check: res directory not found: {res_root}", file=sys.stderr)
        return 2
    try:
        config = json.loads(Path(args.config).read_text(encoding="utf-8")) if Path(args.config).is_file() \
            else {"failOn": ["error"]}
    except (OSError, json.JSONDecodeError) as exc:
        print(f"ui-check: bad config {args.config}: {exc}", file=sys.stderr)
        return 2

    auditor = Auditor(config)
    if args.fail_on:
        auditor.fail_on = [] if args.fail_on == "none" else (
            ["error", "warning"] if args.fail_on == "warning" else ["error"])

    layout_files = sorted((res_root / "layout").glob("*.xml")) if (res_root / "layout").is_dir() else []
    drawable_files: list[Path] = []
    for pattern in ("drawable", "drawable-*"):
        for directory in res_root.glob(pattern):
            if directory.is_dir():
                drawable_files.extend(sorted(directory.glob("*.xml")))
    for path in layout_files:
        auditor.audit_layout(path, res_root)
    for path in drawable_files:
        auditor.audit_drawable(path, res_root)

    errors = sum(1 for f in auditor.findings if f["severity"] == "error")
    warnings = sum(1 for f in auditor.findings if f["severity"] == "warning")
    summary = {"files": len(layout_files) + len(drawable_files),
               "layouts": len(layout_files), "drawables": len(drawable_files),
               "total": len(auditor.findings), "errors": errors, "warnings": warnings,
               "failOn": auditor.fail_on}
    ordered = sorted(auditor.findings, key=lambda f: (f["severity"] != "error", f["file"], f["line"] or 0))

    if args.json_out:
        out_path = Path(args.json_out)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(json.dumps({"summary": summary, "findings": ordered}, indent=2),
                            encoding="utf-8")
    if args.md_out:
        md_path = Path(args.md_out)
        md_path.parent.mkdir(parents=True, exist_ok=True)
        write_markdown(md_path, summary, ordered)

    if not args.quiet:
        print(f"ui-check: {summary['files']} files, "
              f"{summary['total']} findings ({errors} errors, {warnings} warnings)")
        shown = 0
        for finding in ordered:
            if shown >= 30 and len(ordered) > 30:
                print(f"  … and {len(ordered) - shown} more (see JSON/MD report)")
                break
            loc = finding["file"] + (f":{finding['line']}" if finding["line"] else "")
            print(f"  [{finding['severity']}] {finding['rule']} {loc} — {finding['message']}")
            shown += 1

    failing = sum(1 for f in ordered if f["severity"] in auditor.fail_on)
    if failing:
        print(f"ui-check: FAIL — {failing} finding(s) match failOn={auditor.fail_on}", file=sys.stderr)
        return 1
    print("ui-check: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
