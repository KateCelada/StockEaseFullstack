package com.example.stockease;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * One place for screen-to-screen motion so navigation feels identical
 * across the app: 200ms push, 180ms pop, 150ms fade — all fast-out-slow-in.
 *
 * <p>Every call is a no-op when the device has animations disabled
 * (animator duration scale = 0), so accessibility settings are respected.
 */
public final class ScreenTransitions {

    /** Forward push: new screen slides in from the right, caller shifts a third left. */
    public static void open(Context context, Intent intent) {
        if (context == null || intent == null) return;
        context.startActivity(intent);
        if (context instanceof Activity && animationsEnabled((Activity) context)) {
            ((Activity) context).overridePendingTransition(
                    R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }

    /** Forward push by class. */
    public static void open(Context context, Class<?> cls) {
        if (context == null || cls == null) return;
        open(context, new Intent(context, cls));
    }

    /** Back pop: previous screen slides in from the left, caller exits right. */
    public static void finish(Activity activity) {
        if (activity == null) return;
        activity.finish();
        if (animationsEnabled(activity)) {
            activity.overridePendingTransition(
                    R.anim.slide_in_left, R.anim.slide_out_right);
        }
    }

    /** Quiet fade for dialogs and light launches (150ms). */
    public static void fade(Activity activity) {
        if (activity == null || !animationsEnabled(activity)) return;
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    private static boolean animationsEnabled(Activity activity) {
        try {
            float scale = Settings.Global.getFloat(
                    activity.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale > 0f;
        } catch (Exception e) {
            return true;
        }
    }
}
