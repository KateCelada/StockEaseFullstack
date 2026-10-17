package com.example.stockease;

import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

/**
 * Shared screen states so every screen loads and empties the same way.
 *
 * <p>Usage:
 * <pre>
 *   // loading
 *   View skeleton = findViewById(R.id.loadingHost);   // an &lt;include&gt; of view_loading_state
 *   StateViews.pulse(skeleton);
 *   ...
 *   StateViews.stopPulse(skeleton);
 *
 *   // empty
 *   View empty = findViewById(R.id.emptyHost);        // an &lt;include&gt; of view_empty_state
 *   StateViews.bindEmpty(empty, "No sales today",
 *       "Transactions you record today will appear here.", null, null);
 *
 *   // error (a failed load -- never reuse the empty state for this)
 *   View error = findViewById(R.id.errorHost);        // an &lt;include&gt; of view_error_state
 *   StateViews.bindError(error, null, null, v -> retry());
 *   StateViews.setErrorVisible(error, true);
 * </pre>
 */
public final class StateViews {

    /** Shared motion length for state swaps. Inside the 150–250ms band. */
    private static final int STATE_FADE_MS = 150;

    private StateViews() {
    }

    /** Starts the breathing pulse on an included view_loading_state. Idempotent. */
    public static void pulse(View loadingRoot) {
        if (loadingRoot == null) return;
        if (loadingRoot.getAnimation() != null) return;
        Animation pulse = AnimationUtils.loadAnimation(loadingRoot.getContext(), R.anim.skeleton_pulse);
        loadingRoot.startAnimation(pulse);
    }

    /** Shows an included view_loading_state and starts its pulse. */
    public static void showLoading(View loadingRoot) {
        if (loadingRoot == null) return;
        loadingRoot.animate().cancel();
        loadingRoot.setAlpha(1f);
        loadingRoot.setVisibility(View.VISIBLE);
        pulse(loadingRoot);
    }

    /**
     * Fades an included view_loading_state out, then hides it.
     *
     * <p>The breathing pulse is deliberately left running while the view's
     * property alpha falls: the two alphas multiply, so the skeleton breathes
     * its way out instead of popping. If the end callback were ever skipped
     * the view would simply rest at alpha 0 -- transparent rather than
     * stuck -- so the failure mode stays visually harmless.
     */
    public static void hideLoading(View loadingRoot) {
        if (loadingRoot == null) return;
        if (loadingRoot.getVisibility() == View.GONE) return;
        if (!animationsEnabled(loadingRoot)) {
            stopPulse(loadingRoot);
            loadingRoot.setAlpha(1f);
            loadingRoot.setVisibility(View.GONE);
            return;
        }
        loadingRoot.animate().cancel();
        loadingRoot.animate().alpha(0f).setDuration(STATE_FADE_MS)
                .withEndAction(() -> {
                    stopPulse(loadingRoot);
                    loadingRoot.setVisibility(View.GONE);
                    loadingRoot.setAlpha(1f);
                })
                .start();
    }

    /**
     * Fades an empty state in (or hides it at once).
     *
     * <p>Uses the transient {@code Animation} pipeline rather than the alpha
     * property: if the animation never ran the view would still render at
     * full opacity, so this can never leave an empty state invisible.
     */
    public static void setEmptyVisible(View emptyRoot, boolean visible) {
        if (emptyRoot == null) return;
        if (!visible) {
            emptyRoot.clearAnimation();
            emptyRoot.setVisibility(View.GONE);
            return;
        }
        emptyRoot.clearAnimation();
        emptyRoot.setAlpha(1f);
        emptyRoot.setVisibility(View.VISIBLE);
        if (animationsEnabled(emptyRoot)) {
            emptyRoot.startAnimation(
                    AnimationUtils.loadAnimation(emptyRoot.getContext(), R.anim.fade_in));
        }
    }

    /**
     * Fades an error state in (or hides it at once).
     *
     * <p>Deliberately separate from {@link #setEmptyVisible}: a failed load
     * must not be able to render the empty state, which would tell the user
     * their data is gone when it was never fetched.
     */
    public static void setErrorVisible(View errorRoot, boolean visible) {
        if (errorRoot == null) return;
        if (!visible) {
            errorRoot.clearAnimation();
            errorRoot.setVisibility(View.GONE);
            return;
        }
        errorRoot.clearAnimation();
        errorRoot.setAlpha(1f);
        errorRoot.setVisibility(View.VISIBLE);
        if (animationsEnabled(errorRoot)) {
            errorRoot.startAnimation(
                    AnimationUtils.loadAnimation(errorRoot.getContext(), R.anim.fade_in));
        }
    }

    /**
     * Fills an included view_error_state with a real sentence for the screen.
     *
     * <p>Passing null for any text keeps that view's XML default, so a screen
     * can show the generic error without restating it.
     */
    public static void bindError(View errorRoot, String title, String message,
                                 View.OnClickListener retryListener) {
        if (errorRoot == null) return;

        TextView tvTitle = errorRoot.findViewById(R.id.tvErrorTitle);
        TextView tvMessage = errorRoot.findViewById(R.id.tvErrorMessage);
        MaterialButton btnRetry = errorRoot.findViewById(R.id.btnErrorRetry);

        if (tvTitle != null && title != null) {
            tvTitle.setText(title);
        }
        if (tvMessage != null && message != null) {
            tvMessage.setText(message);
        }
        if (btnRetry != null && retryListener != null) {
            btnRetry.setOnClickListener(retryListener);
        }
    }

    /** Respects the system animator duration scale, same rule as ScreenTransitions. */
    private static boolean animationsEnabled(View view) {        try {
            float scale = Settings.Global.getFloat(
                    view.getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale > 0f;
        } catch (Exception e) {
            return true;
        }
    }

    /** Stops the pulse (call when real content replaces the skeleton). */
    public static void stopPulse(View loadingRoot) {
        if (loadingRoot == null) return;
        loadingRoot.clearAnimation();
    }

    /**
     * Fills an included view_empty_state with a real sentence for the screen.
     *
     * @param actionLabel label for the optional retry/clear button, or null to hide it
     */
    public static void bindEmpty(View emptyRoot, String title, String message,
                                 String actionLabel, View.OnClickListener actionListener) {
        if (emptyRoot == null) return;

        TextView tvTitle = emptyRoot.findViewById(R.id.tvEmptyTitle);
        TextView tvMessage = emptyRoot.findViewById(R.id.tvEmptyMessage);
        MaterialButton btnAction = emptyRoot.findViewById(R.id.btnEmptyAction);

        if (tvTitle != null && title != null) {
            tvTitle.setText(title);
        }
        if (tvMessage != null && message != null) {
            tvMessage.setText(message);
        }
        if (btnAction != null) {
            if (actionLabel == null) {
                btnAction.setVisibility(View.GONE);
            } else {
                btnAction.setVisibility(View.VISIBLE);
                btnAction.setText(actionLabel);
                btnAction.setOnClickListener(actionListener);
            }
        }
    }
}
