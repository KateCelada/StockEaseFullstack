package com.example.stockease;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

public class ViewFeedbackUtil {

    /**
     * Attaches tactile press-and-release animation to a single view
     */
    public static void attach(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.85f).setDuration(60).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(100).start();
                    break;
            }
            return false;
        });
    }

    /**
     * Recursively traverses a layout tree and attaches tactile feedback to all clickable views, buttons, and cards
     */
    public static void attachAll(View root) {
        if (root == null) return;
        if (root.isClickable() || root instanceof android.widget.Button || root instanceof androidx.cardview.widget.CardView) {
            attach(root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                attachAll(group.getChildAt(i));
            }
        }
    }
}
