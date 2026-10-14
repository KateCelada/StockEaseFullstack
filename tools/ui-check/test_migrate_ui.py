"""Tests for tools/ui-check/migrate_ui.py (stdlib only)."""

import unittest

import migrate_ui


class MigrateTest(unittest.TestCase):
    def test_color_map(self):
        out, counts = migrate_ui.migrate_text(
            '<TextView xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:textColor="#64748B" />')
        self.assertIn("@color/text_secondary", out)
        self.assertEqual(counts["STOCK-001"], 1)

    def test_context_split(self):
        out, _ = migrate_ui.migrate_text(
            '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:background="#0F172A" />')
        self.assertIn("@color/sidebar_bg", out)
        out2, _ = migrate_ui.migrate_text(
            '<TextView xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:textColor="#0F172A" />')
        self.assertIn("@color/text_primary", out2)

    def test_glyph_strip(self):
        out, counts = migrate_ui.migrate_text(
            '<TextView xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:text="🛒 POS/Sales" />')
        self.assertIn('android:text="POS/Sales"', out)
        self.assertEqual(counts["STOCK-002"], 1)

    def test_text_appearance_added(self):
        out, counts = migrate_ui.migrate_text(
            '<TextView xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:textSize="22sp" />')
        self.assertIn("TextAppearance.StockEase.Title", out)
        self.assertTrue(out.rstrip().endswith("/>"))
        self.assertEqual(counts["STOCK-003"], 1)

    def test_text_appearance_despite_brand_text(self):
        # The word "StockEase" in android:text must not suppress the fix.
        out, counts = migrate_ui.migrate_text(
            '<TextView xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:text="StockEase" android:textSize="28sp" />')
        self.assertIn("android:textAppearance", out)
        self.assertEqual(counts["STOCK-003"], 1)

    def test_text_appearance_skipped_when_styled(self):
        out, counts = migrate_ui.migrate_text(
            '<TextView xmlns:android="http://schemas.android.com/apk/res/android" '
            'style="@style/Widget.StockEase.Button.Primary" android:textSize="12sp" />')
        self.assertNotIn("android:textAppearance", out)
        self.assertEqual(counts["STOCK-003"], 0)

    def test_button_conversion(self):
        out, counts = migrate_ui.migrate_text(
            '<Button xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:backgroundTint="#2563EB" android:text="Go" />')
        self.assertIn("<com.google.android.material.button.MaterialButton", out)
        self.assertNotIn("backgroundTint", out)
        self.assertIn("Widget.StockEase.Button.Primary", out)
        self.assertEqual(counts["STOCK-004"], 1)

    def test_spacing_snap(self):
        out, counts = migrate_ui.migrate_text(
            '<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:padding="10dp" />')
        self.assertIn('android:padding="12dp"', out)
        self.assertEqual(counts["STOCK-008"], 1)

    def test_framework_color(self):
        out, counts = migrate_ui.migrate_text(
            '<TextView xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:textColor="@android:color/darker_gray" />')
        self.assertIn("@color/text_secondary", out)
        self.assertEqual(counts["STOCK-007"], 1)

    def test_image_content_description(self):
        out, counts = migrate_ui.migrate_text(
            '<ImageView xmlns:android="http://schemas.android.com/apk/res/android" '
            'android:src="@drawable/x" />')
        self.assertIn('android:contentDescription="@string/cd_nav_icon"', out)
        self.assertEqual(counts["STOCK-005"], 1)


if __name__ == "__main__":
    unittest.main()
