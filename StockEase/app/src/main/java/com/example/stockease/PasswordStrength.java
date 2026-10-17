package com.example.stockease;

import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Password strength meter and policy.
 *
 * Mirrors stockease_api/password_policy.php exactly so the verdict shown in
 * the app can never drift from what the API accepts: same levels
 * (Too weak / Weak / Fair / Strong / Very strong) and same minimum
 * (8+ characters with at least one letter and one number).
 */
public final class PasswordStrength {

    public static final int MIN_LENGTH = 8;

    private static final int[] COLORS = {0xFFDC2626, 0xFFEF4444, 0xFFF59E0B, 0xFF10B981, 0xFF059669};
    private static final String[] LABELS = {"Too weak", "Weak", "Fair", "Strong", "Very strong"};

    private PasswordStrength() {
    }

    /** Scores a password from 0 (too weak) to 4 (very strong). */
    public static int score(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }

        int score = 0;
        if (password.length() >= MIN_LENGTH) {
            score++;
        }
        if (password.length() >= 12) {
            score++;
        }
        if (password.matches(".*[a-z].*") && password.matches(".*[A-Z].*")) {
            score++;
        }
        if (password.matches(".*\\d.*")) {
            score++;
        }
        if (password.matches(".*[^A-Za-z0-9].*")) {
            score++;
        }
        // A very short password can never look strong
        if (password.length() < MIN_LENGTH) {
            score = Math.min(score, 1);
        }

        return Math.max(0, Math.min(4, score));
    }

    /** Human readable label for a score (0-4). */
    public static String label(int score) {
        return LABELS[Math.max(0, Math.min(4, score))];
    }

    /** True when the password satisfies the same minimum the API enforces. */
    public static boolean isAcceptable(String password) {
        return password != null
                && password.length() >= MIN_LENGTH
                && password.matches(".*[A-Za-z].*")
                && password.matches(".*\\d.*");
    }

    /** Friendly requirement sentence for inline hints and toasts. */
    public static String requirementText() {
        return "Password must be at least " + MIN_LENGTH
                + " characters and include a letter and a number.";
    }

    /**
     * Wires a view_password_strength include to a password field for a live
     * meter. The meter stays hidden until the user starts typing.
     */
    public static void bind(View meterRoot, final EditText input) {
        if (meterRoot == null || input == null) {
            return;
        }

        final ProgressBar bar = meterRoot.findViewById(R.id.pwStrengthProgress);
        final TextView labelView = meterRoot.findViewById(R.id.pwStrengthLabel);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                update(meterRoot, bar, labelView, e != null ? e.toString() : "");
            }
        });

        CharSequence current = input.getText();
        update(meterRoot, bar, labelView, current != null ? current.toString() : "");
    }

    private static void update(View meterRoot, ProgressBar bar, TextView labelView, String password) {
        if (password.isEmpty()) {
            meterRoot.setVisibility(View.GONE);
            return;
        }

        int score = score(password);
        meterRoot.setVisibility(View.VISIBLE);

        if (bar != null) {
            bar.setProgress((score + 1) * 20);
            bar.setProgressTintList(ColorStateList.valueOf(COLORS[score]));
        }
        if (labelView != null) {
            labelView.setText("Password strength: " + label(score));
            labelView.setTextColor(COLORS[score]);
        }
    }
}
