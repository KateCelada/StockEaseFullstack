"""Regression tests for tools/ui-check/check_ui.py (stdlib only)."""

import tempfile
import unittest
from pathlib import Path

import check_ui


def _auditor(**overrides):
    cfg = {
        "rules": {f"STOCK-{i:03d}": {"severity": "error", "enabled": True} for i in range(1, 11)},
        "gridDp": 4,
        "allowlist": {"STOCK-006": [], "STOCK-008": []},
        "failOn": ["error"],
    }
    cfg.update(overrides)
    return check_ui.Auditor(cfg)


def _write(tmp: Path, name: str, body: str) -> Path:
    p = tmp / name
    p.write_text(body, encoding="utf-8")
    return p


class CheckUiTest(unittest.TestCase):
    def test_hardcoded_color_flagged(self):
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            res = tmp / "res"
            (res / "layout").mkdir(parents=True)
            path = _write(res / "layout", "a.xml",
                          '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" '
                          'android:background="#1E1E36" />')
            a = _auditor()
            a.audit_layout(path, res)
            self.assertTrue(any(f["rule"] == "STOCK-001" and f["value"] == "#1E1E36"
                                for f in a.findings))

    def test_color_reference_passes(self):
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            res = tmp / "res"
            (res / "layout").mkdir(parents=True)
            path = _write(res / "layout", "a.xml",
                          '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" '
                          'android:background="@color/sidebar_bg" />')
            a = _auditor()
            a.audit_layout(path, res)
            self.assertFalse(any(f["rule"] == "STOCK-001" for f in a.findings))

    def test_emoji_flagged(self):
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            res = tmp / "res"
            (res / "layout").mkdir(parents=True)
            path = _write(res / "layout", "a.xml",
                          '<TextView xmlns:android="http://schemas.android.com/apk/res/android" '
                          'android:text="🛒 POS/Sales" />')
            a = _auditor()
            a.audit_layout(path, res)
            self.assertTrue(any(f["rule"] == "STOCK-002" for f in a.findings))

    def test_textsize_without_appearance_warns(self):
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            res = tmp / "res"
            (res / "layout").mkdir(parents=True)
            path = _write(res / "layout", "a.xml",
                          '<TextView xmlns:android="http://schemas.android.com/apk/res/android" '
                          'android:textSize="22sp" android:text="Hi" />')
            a = _auditor()
            a.audit_layout(path, res)
            self.assertTrue(any(f["rule"] == "STOCK-003" for f in a.findings))

    def test_off_grid_spacing(self):
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            res = tmp / "res"
            (res / "layout").mkdir(parents=True)
            path = _write(res / "layout", "a.xml",
                          '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" '
                          'android:padding="5dp" />')
            a = _auditor()
            a.audit_layout(path, res)
            self.assertTrue(any(f["rule"] == "STOCK-008" for f in a.findings))

    def test_drawable_hardcoded_color(self):
        with tempfile.TemporaryDirectory() as td:
            tmp = Path(td)
            res = tmp / "res"
            (res / "drawable").mkdir(parents=True)
            path = _write(res / "drawable", "d.xml",
                          '<shape xmlns:android="http://schemas.android.com/apk/res/android">'
                          '<solid android:color="#FFFFFF" /></shape>')
            a = _auditor()
            a.audit_drawable(path, res)
            self.assertTrue(any(f["rule"] == "STOCK-010" for f in a.findings))

    def test_contains_icon_glyph(self):
        self.assertTrue(check_ui.contains_icon_glyph("📜 View"))
        self.assertTrue(check_ui.contains_icon_glyph("● StockEase"))
        self.assertFalse(check_ui.contains_icon_glyph("Point of Sale"))


if __name__ == "__main__":
    unittest.main()
