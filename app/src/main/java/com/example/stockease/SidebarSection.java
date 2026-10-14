package com.example.stockease;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Accordion-style collapsible sidebar sections.
 *
 * Every sidebar screen attaches its four sections (OVERVIEW, MANAGEMENT,
 * ANALYTICS, SYSTEM) with {@link #attach(AppCompatActivity, int, int, int, boolean)}.
 * Each section is made of three views:
 *
 * <ul>
 *     <li>a clickable header row (the section label),</li>
 *     <li>an arrow indicator that flips between &#9656; and &#9662;,</li>
 *     <li>a container LinearLayout holding the section's nav items.</li>
 * </ul>
 *
 * Behaviour: tapping an open header collapses it (everything ends up closed);
 * tapping a closed header collapses every other section first, then expands
 * the tapped one (accordion). Expand/collapse animates the container height
 * plus a short alpha fade over ~190ms.
 *
 * The initial expanded state is supplied by the calling activity, so the
 * section owning the current screen opens by default and state survives
 * rotation for free (onCreate re-attaches with the same flags).
 */
public final class SidebarSection {

    private static final long ANIMATION_DURATION_MS = 190L;

    private static final String ARROW_COLLAPSED = "\u25B8"; // ▸
    private static final String ARROW_EXPANDED = "\u25BE"; // ▾

    /** Sections attached per activity; the activity is removed on destroy. */
    private static final Map<AppCompatActivity, List<Section>> REGISTRY = new WeakHashMap<>();

    private SidebarSection() {
    }

    /**
     * Attaches one collapsible section to the activity's sidebar.
     *
     * @param activity    host activity (sections are scoped per activity)
     * @param headerId    clickable header row id
     * @param arrowId     arrow TextView id inside the header
     * @param containerId LinearLayout id holding the section's items
     * @param expanded    whether this section starts expanded (one section per screen)
     */
    public static void attach(AppCompatActivity activity, int headerId, int arrowId, int containerId, boolean expanded) {
        if (activity == null) {
            return;
        }

        View header = activity.findViewById(headerId);
        View arrow = activity.findViewById(arrowId);
        View container = activity.findViewById(containerId);
        if (header == null || container == null) {
            return;
        }

        List<Section> sections = REGISTRY.get(activity);
        if (sections == null) {
            sections = new ArrayList<>();
            REGISTRY.put(activity, sections);
            activity.getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    REGISTRY.remove(activity);
                }
            });
        }

        // Re-attach (same header) replaces the previous registration.
        for (Iterator<Section> it = sections.iterator(); it.hasNext(); ) {
            if (it.next().headerId == headerId) {
                it.remove();
            }
        }

        Section section = new Section(activity, header, arrow, container, expanded);
        sections.add(section);
        section.applyInitialState();
        header.setOnClickListener(v -> onHeaderTapped(activity, section));
    }

    private static void onHeaderTapped(AppCompatActivity activity, Section tapped) {
        List<Section> sections = REGISTRY.get(activity);
        if (sections == null) {
            return;
        }

        boolean wasOpen = tapped.expanded;

        // Accordion: close everything else first.
        for (Section section : sections) {
            if (section != tapped && section.expanded) {
                section.collapse();
            }
        }

        if (wasOpen) {
            tapped.collapse();
        } else {
            tapped.expand();
        }
    }

    private static final class Section {
        private final AppCompatActivity activity;
        private final int headerId;
        private final View header;
        private final View arrow;
        private final View container;
        private boolean expanded;
        private ValueAnimator animator;

        Section(AppCompatActivity activity, View header, View arrow, View container, boolean expanded) {
            this.activity = activity;
            this.headerId = header.getId();
            this.header = header;
            this.arrow = arrow;
            this.container = container;
            this.expanded = expanded;
        }

        /** Applies the initial state without animating (called from attach). */
        void applyInitialState() {
            cancelAnimator();
            setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            container.setVisibility(expanded ? View.VISIBLE : View.GONE);
            container.setAlpha(expanded ? 1f : 0f);
            updateArrow();
        }

        void expand() {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            expanded = true;
            updateArrow();
            cancelAnimator();

            container.setVisibility(View.VISIBLE);
            final int startHeight = container.getHeight();
            final int endHeight = measureHeight();
            if (startHeight == endHeight) {
                setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
                container.setAlpha(1f);
                return;
            }
            runAnimation(startHeight, endHeight, container.getAlpha(), 1f, false);
        }

        void collapse() {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            expanded = false;
            updateArrow();
            cancelAnimator();

            final int startHeight = container.getHeight();
            if (startHeight <= 0) {
                finishCollapse();
                return;
            }
            runAnimation(startHeight, 0, container.getAlpha(), 0f, true);
        }

        private void runAnimation(final int startHeight, final int endHeight,
                                  final float startAlpha, final float endAlpha,
                                  final boolean collapsing) {
            final ValueAnimator valueAnimator = ValueAnimator.ofInt(startHeight, endHeight);
            valueAnimator.setDuration(ANIMATION_DURATION_MS);
            valueAnimator.addUpdateListener(animation -> {
                setHeight((int) animation.getAnimatedValue());
                float fraction = animation.getAnimatedFraction();
                container.setAlpha(startAlpha + (endAlpha - startAlpha) * fraction);
            });
            valueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (collapsing) {
                        finishCollapse();
                    } else {
                        setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
                        container.setAlpha(1f);
                    }
                    if (animator == valueAnimator) {
                        animator = null;
                    }
                }
            });
            animator = valueAnimator;
            valueAnimator.start();
        }

        private void finishCollapse() {
            setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
            container.setAlpha(0f);
            container.setVisibility(View.GONE);
        }

        private void cancelAnimator() {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        }

        private int measureHeight() {
            int width = 0;
            if (container.getParent() instanceof View) {
                View parent = (View) container.getParent();
                width = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
            }
            if (width <= 0) {
                width = header.getWidth();
            }
            if (width <= 0) {
                return container.getHeight();
            }
            container.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            return container.getMeasuredHeight();
        }

        private void setHeight(int height) {
            ViewGroup.LayoutParams lp = container.getLayoutParams();
            if (lp == null) {
                return;
            }
            lp.height = height;
            container.setLayoutParams(lp);
        }

        private void updateArrow() {
            if (arrow instanceof TextView) {
                ((TextView) arrow).setText(expanded ? ARROW_EXPANDED : ARROW_COLLAPSED);
            }
        }
    }
}
