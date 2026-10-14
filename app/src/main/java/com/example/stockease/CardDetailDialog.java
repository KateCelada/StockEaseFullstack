package com.example.stockease;

import android.content.Context;
import android.text.TextUtils;

import androidx.appcompat.app.AlertDialog;

/**
 * Read-only detail sheet shown when a dashboard card has no destination
 * screen of its own. Rows are plain "Label: value" lines taken from the card.
 */
public final class CardDetailDialog {

    private CardDetailDialog() {
    }

    public static void show(Context context, String title, String... rows) {
        StringBuilder body = new StringBuilder();
        if (rows != null) {
            for (String row : rows) {
                if (TextUtils.isEmpty(row)) continue;
                if (body.length() > 0) {
                    body.append('\n');
                }
                body.append(row);
            }
        }

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(body.toString())
                .setPositiveButton("Close", null)
                .show();
    }
}
