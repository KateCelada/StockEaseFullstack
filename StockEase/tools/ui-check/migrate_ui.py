#!/usr/bin/env python3
"""One-shot StockEase UI codemod: apply ui-check-driven mechanical fixes.

Fixes STOCK-001 (hex -> @color token), STOCK-002 (strip icon glyphs),
STOCK-003 (add textAppearance), STOCK-004 (Button -> MaterialButton),
STOCK-005 (contentDescription), STOCK-007 (framework color -> token),
STOCK-008 (snap spacing to 4dp grid).

STOCK-006 (hardcoded android:text) is intentionally left alone: extracting
~500 strings needs human review per screen.

Usage (from StockEase/ project root):
    py tools/ui-check/migrate_ui.py --dry-run        # preview only
    py tools/ui-check/migrate_ui.py --apply          # backup + rewrite

Backups go to tools/ui-check/migration-backup/<timestamp>/.
"""

from __future__ import annotations

import argparse
import datetime
import re
import shutil
import sys
from pathlib import Path

# -- STOCK-001: hex (lowercased) -> token. Context split for #0F172A. --------
BG_ATTRS = {"background", "backgroundTint", "cardBackgroundColor", "boxBackgroundColor"}
COLOR_MAP = {
    "#ffffff": "@color/white",
    "#f8fafc": "@color/app_background",
    "#eaeff2": "@color/app_background",
    "#f5f6fa": "@color/app_background",
    "#efeff6": "@color/app_background",
    "#1b1c30": "@color/sidebar_bg",
    "#1e1e36": "@color/sidebar_bg",
    "#1e1e2d": "@color/sidebar_bg",
    "#2a2a4a": "@color/sidebar_bg",
    "#1e293b": "@color/sidebar_item_active",
    "#2563eb": "@color/primary",
    "#3b82f6": "@color/primary",
    "#1e40af": "@color/primary_ink",
    "#eff6ff": "@color/primary_light",
    "#64748b": "@color/text_secondary",
    "#6b7280": "@color/text_secondary",
    "#6e7191": "@color/text_secondary",
    "#808080": "@color/text_secondary",
    "#757575": "@color/text_secondary",
    "#94a3b8": "@color/text_muted",
    "#9ca3af": "@color/text_muted",
    "#a0a0b0": "@color/text_muted",
    "#334155": "@color/slate_700",
    "#475569": "@color/slate_600",
    "#555555": "@color/slate_600",
    "#212121": "@color/text_primary",
    "#000000": "@color/text_primary",
    "#e2e8f0": "@color/card_border",
    "#cbd5e1": "@color/input_border",
    "#f1f5f9": "@color/card_subtle_bg",
    "#f3f4f6": "@color/card_subtle_bg",
    "#10b981": "@color/success",
    "#059669": "@color/success_strong",
    "#16a34a": "@color/profit",
    "#2e7d32": "@color/success_text",
    "#166534": "@color/success_text",
    "#047857": "@color/success_text",
    "#ef4444": "@color/danger",
    "#dc2626": "@color/danger_strong",
    "#c62828": "@color/danger_strong",
    "#b91c1c": "@color/danger_text",
    "#f59e0b": "@color/warning",
    "#fef3c7": "@color/badge_amber_bg",
    "#fffbeb": "@color/warning_bg",
    "#b45309": "@color/warning_text",
    "#8b5cf6": "@color/purple",
    "#7c4dff": "@color/brand_accent",
    "#6200ee": "@color/brand_accent",
    "#6d28d9": "@color/purple_text",
    "#f5f3ff": "@color/purple_bg",
    "#ecfdf5": "@color/success_bg",
    "#fef2f2": "@color/danger_bg",
    "#ede7f6": "@color/purple_bg",
    "#4a148c": "@color/purple_text",
    "#efefef": "@color/card_subtle_bg",
    "#121212": "@color/text_primary",
    "#111111": "@color/text_primary",
    "#1e1e1e": "@color/text_primary",
    "#4ade80": "@color/success",
    "#2d2d3f": "@color/sidebar_bg",
    "#d1c4e9": "@color/purple_bg",
    "#c8e6c9": "@color/success_bg",
    "#1d4ed8": "@color/primary_dark",
    "#065f46": "@color/success_text",
    "#f0fdf4": "@color/success_bg",
    "#f1f3f4": "@color/card_subtle_bg",
    "#b0b0b0": "@color/text_muted",
    "#4caf50": "@color/success",
    "#ffebee": "@color/danger_bg",
    "#f8f9fc": "@color/app_background",
    "#dcfce7": "@color/success_bg",
    "#fee2e2": "@color/danger_bg",
}
CONTEXT_SPLIT = {"#0f172a": ("@color/sidebar_bg", "@color/text_primary")}

COLOR_ATTR_RE = re.compile(
    r"((?:android|app):(?:background|textColor|textColorHint|backgroundTint|"
    r"strokeColor|hintTextColor|iconTint|cardBackgroundColor|boxStrokeColor|"
    r"boxBackgroundColor))\s*=\s*\"(#[0-9A-Fa-f]{3,8})\""
)

# -- STOCK-007 ---------------------------------------------------------------
FRAMEWORK_MAP = {
    "@android:color/black": "@color/text_primary",
    "@android:color/white": "@color/white",
    "@android:color/darker_gray": "@color/text_secondary",
    "@android:color/secondary_text_dark": "@color/text_secondary",
    "@android:color/secondary_text_light": "@color/text_muted",
    "@android:color/transparent": "@color/transparent",
    "@android:color/background_light": "@color/app_background",
}
FRAMEWORK_RE = re.compile(r"\"(@android:color/[A-Za-z0-9_]+)\"")

# -- STOCK-002: strip icon glyphs --------------------------------------------
_ICON_RANGES = (
    (0x2190, 0x21FF), (0x25A0, 0x25FF), (0x2600, 0x27BF),
    (0x2B00, 0x2BFF), (0x1F000, 0x1FAFF),
)
_VS16 = 0xFE0F


def strip_glyphs(text: str) -> str:
    kept = []
    for ch in text:
        code = ord(ch)
        if code == _VS16:
            continue
        if any(low <= code <= high for low, high in _ICON_RANGES):
            continue
        kept.append(ch)
    cleaned = re.sub(r"[ \t]{2,}", " ", "".join(kept)).strip(" \t-–—•●")
    return cleaned.strip()


VISIBLE_STR_RE = re.compile(r"(android:(?:text|hint|contentDescription)\s*=\s*\")([^\"]*)(\")")

# -- STOCK-008: snap spacing --------------------------------------------------
SPACING_RE = re.compile(
    r"((?:android:)?(?:layout_margin\w*|padding\w*)\s*=\s*\")(-?\d+(?:\.\d+)?)(dp|dip)(\")"
)


def snap_grid(value: float, grid: int = 4) -> int:
    return int((value + grid / 2) // grid * grid)


# -- STOCK-003/004/005: tag-level pass ----------------------------------------
TEXT_HOSTS = {"TextView", "EditText", "Button", "AutoCompleteTextView", "CheckBox", "RadioButton"}
TAG_RE = re.compile(
    r"<(TextView|EditText|Button|AutoCompleteTextView|CheckBox|RadioButton|ImageView|ImageButton)\b([^<>]*?)>",
    re.DOTALL,
)
TEXTSIZE_RE = re.compile(r"android:textSize\s*=\s*\"(\d+(?:\.\d+)?)sp\"")
PRIMARY_HINT_RE = re.compile(r"primary|2563EB|3B82F6|059669|emerald|success|accent|7C4DFF", re.IGNORECASE)


def appearance_for(sp: float) -> str:
    if sp >= 24:
        return "@style/TextAppearance.StockEase.Display"
    if sp >= 18:
        return "@style/TextAppearance.StockEase.Title"
    if sp >= 15:
        return "@style/TextAppearance.StockEase.Section"
    if sp >= 13:
        return "@style/TextAppearance.StockEase.Body"
    if sp >= 11:
        return "@style/TextAppearance.StockEase.Label"
    return "@style/TextAppearance.StockEase.Caption"


def transform_tag(match: re.Match) -> tuple[str, dict[str, int]]:
    orig_tag, attrs = match.group(1), match.group(2)
    tag = orig_tag
    counts = {"STOCK-003": 0, "STOCK-004": 0, "STOCK-005": 0}

    stripped = attrs.rstrip()
    self_close = stripped.endswith("/")
    if self_close:
        attrs = stripped[:-1]

    if tag in TEXT_HOSTS:
        m = TEXTSIZE_RE.search(attrs)
        style_m = re.search(r"\bstyle\s*=\s*\"([^\"]*)\"", attrs)
        styled = bool(style_m and ("TextAppearance" in style_m.group(1)
                                   or "StockEase" in style_m.group(1)))
        if m and "android:textAppearance" not in attrs and not styled:
            attrs += f' android:textAppearance="{appearance_for(float(m.group(1)))}"'
            counts["STOCK-003"] += 1

    if tag == "Button" and "android:backgroundTint" in attrs:
        tint_m = re.search(r"android:backgroundTint\s*=\s*\"([^\"]*)\"", attrs)
        tint = tint_m.group(1) if tint_m else ""
        style = ("@style/Widget.StockEase.Button.Primary"
                 if PRIMARY_HINT_RE.search(tint) else "@style/Widget.StockEase.Button.Secondary")
        attrs = re.sub(r"\s*android:backgroundTint\s*=\s*\"[^\"]*\"", "", attrs)
        if "style=" not in attrs:
            attrs += f' style="{style}"'
        tag = "com.google.android.material.button.MaterialButton"
        counts["STOCK-004"] += 1

    if tag in ("ImageView", "ImageButton") and "android:contentDescription" not in attrs:
        attrs += ' android:contentDescription="@string/cd_nav_icon"'
        counts["STOCK-005"] += 1

    closer = " /" if self_close else ""
    if not any(counts.values()) and tag == orig_tag:
        return match.group(0), counts
    return f"<{tag}{attrs}{closer}>", counts


def migrate_text(text: str) -> tuple[str, dict[str, int]]:
    counts: dict[str, int] = {f"STOCK-{i:03d}": 0 for i in (1, 2, 3, 4, 5, 7, 8)}

    def color_sub(m: re.Match) -> str:
        attr, value = m.group(1), m.group(2)
        key = value.lower()
        if key in CONTEXT_SPLIT:
            bg, fg = CONTEXT_SPLIT[key]
            local = attr.split(":")[-1]
            replacement = bg if local in BG_ATTRS else fg
        else:
            replacement = COLOR_MAP.get(key)
        if replacement is None:
            return m.group(0)
        counts["STOCK-001"] += 1
        return f"{attr}=\"{replacement}\""

    text = COLOR_ATTR_RE.sub(color_sub, text)

    def fw_sub(m: re.Match) -> str:
        replacement = FRAMEWORK_MAP.get(m.group(1))
        if replacement is None:
            return m.group(0)
        counts["STOCK-007"] += 1
        return f'"{replacement}"'

    text = FRAMEWORK_RE.sub(fw_sub, text)

    def glyph_sub(m: re.Match) -> str:
        original = m.group(2)
        if not any(
            ord(ch) == _VS16 or any(low <= ord(ch) <= high for low, high in _ICON_RANGES)
            for ch in original
        ):
            return m.group(0)
        cleaned = strip_glyphs(original)
        if cleaned != original:
            counts["STOCK-002"] += 1
            return m.group(1) + cleaned + m.group(3)
        return m.group(0)

    text = VISIBLE_STR_RE.sub(glyph_sub, text)

    def spacing_sub(m: re.Match) -> str:
        number = float(m.group(2))
        if number % 4 == 0:
            return m.group(0)
        counts["STOCK-008"] += 1
        return f"{m.group(1)}{snap_grid(number)}{m.group(3)}{m.group(4)}"

    text = SPACING_RE.sub(spacing_sub, text)

    def tag_sub(m: re.Match) -> str:
        new_tag, delta = transform_tag(m)
        for k, v in delta.items():
            counts[k] += v
        return new_tag

    text = TAG_RE.sub(tag_sub, text)
    # Closing tags for converted buttons.
    text, n_close = re.subn(
        r"</Button>",
        "</com.google.android.material.button.MaterialButton>", text)
    counts["STOCK-004"] += 0  # closers follow openers; counted above.
    return text, counts


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="StockEase UI codemod (one-shot).")
    parser.add_argument("--res", default="app/src/main/res")
    parser.add_argument("--apply", action="store_true",
                        help="Rewrite files (default is --dry-run).")
    parser.add_argument("--backup-dir", default="tools/ui-check/migration-backup")
    args = parser.parse_args(argv)

    res = Path(args.res)
    layouts = sorted((res / "layout").glob("*.xml"))
    if not layouts:
        print(f"migrate: no layouts in {res}", file=sys.stderr)
        return 2

    changed: dict[str, dict[str, int]] = {}
    for path in layouts:
        original = path.read_text(encoding="utf-8")
        migrated, counts = migrate_text(original)
        if migrated != original:
            changed[str(path)] = counts

    total = sum(sum(c.values()) for c in changed.values())
    print(f"migrate: {len(changed)}/{len(layouts)} files would change, {total} mechanical fixes")
    for name in sorted(changed):
        detail = ", ".join(f"{k}={v}" for k, v in sorted(changed[name].items()) if v)
        print(f"  {Path(name).name}: {detail}")

    if not args.apply:
        print("migrate: dry-run only, pass --apply to rewrite.")
        return 0

    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    backup_root = Path(args.backup_dir) / stamp
    backup_root.mkdir(parents=True, exist_ok=True)
    for name in changed:
        src = Path(name)
        dst = backup_root / src.name
        shutil.copy2(src, dst)
        src.write_text(migrate_text(src.read_text(encoding="utf-8"))[0], encoding="utf-8")
    print(f"migrate: applied, backups in {backup_root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
