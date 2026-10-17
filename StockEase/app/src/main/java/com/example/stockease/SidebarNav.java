package com.example.stockease;

import android.content.Intent;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Wires the shared sidebar (res/layout/view_sidebar.xml).
 *
 * Every main screen includes view_sidebar.xml and calls
 * {@link #attach(AppCompatActivity, String)} in onCreate. The helper:
 *
 * <ul>
 *     <li>copies the identity extras (user_id, store_id, role, ...) from the
 *         host activity's intent into every launched intent,</li>
 *     <li>hides nav items the signed-in role may not open,</li>
 *     <li>highlights the item of the current screen,</li>
 *     <li>expands the accordion section that owns the current screen and
 *         collapses the rest (same behaviour as the dashboard sidebars).</li>
 * </ul>
 *
 * This replaces the old situation where the sidebar existed only inside the
 * seven dashboard-style layouts, so navigating to POS, Reports, Staff, ...
 * stranded the user on a screen with no navigation.
 */
public final class SidebarNav {

    /** Sections per screen key so the owning accordion opens by default. */
    private static final Set<String> OVERVIEW_SCREENS = new HashSet<>(Arrays.asList("dashboard"));
    private static final Set<String> ANALYTICS_SCREENS = new HashSet<>(Arrays.asList("insights", "business_progress"));
    private static final Set<String> SYSTEM_SCREENS = new HashSet<>(Arrays.asList("profile", "subscription", "settings"));

    private SidebarNav() {
    }

    /**
     * Attaches and configures the shared sidebar.
     *
     * @param activity host activity (must include view_sidebar.xml)
     * @param current  key of the current screen, e.g. "pos", "reports",
     *                 "transfers", "dashboard" (null falls back to "dashboard")
     */
    public static void attach(AppCompatActivity activity, String current) {
        if (activity == null) {
            return;
        }
        String screen = (current == null || current.isEmpty()) ? "dashboard" : current;
        String role = activity.getIntent() != null ? activity.getIntent().getStringExtra("role") : null;
        role = role == null ? "" : role;

        // Accordion: the section owning the current screen starts expanded
        SidebarSection.attach(activity, R.id.navHeaderOverview, R.id.navArrowOverview,
                R.id.navSectionOverviewItems, OVERVIEW_SCREENS.contains(screen));
        SidebarSection.attach(activity, R.id.navHeaderManagement, R.id.navArrowManagement,
                R.id.navSectionManagementItems, !OVERVIEW_SCREENS.contains(screen)
                        && !ANALYTICS_SCREENS.contains(screen) && !SYSTEM_SCREENS.contains(screen));
        SidebarSection.attach(activity, R.id.navHeaderAnalytics, R.id.navArrowAnalytics,
                R.id.navSectionAnalyticsItems, ANALYTICS_SCREENS.contains(screen));
        SidebarSection.attach(activity, R.id.navHeaderSystem, R.id.navArrowSystem,
                R.id.navSectionSystemItems, SYSTEM_SCREENS.contains(screen));

        // Dashboard target mirrors LoginActivity's post-login routing
        Class<?> dashboardTarget = dashboardTargetFor(role);
        bind(activity, R.id.btnNavDashboard, screen, "dashboard", dashboardTarget);
        bind(activity, R.id.btnNavPos, screen, "pos", POSActivity.class);
        bind(activity, R.id.btnNavInventory, screen, "inventory", InventoryActivity.class);
        bind(activity, R.id.btnNavProducts, screen, "products", ProductsActivity.class);
        bind(activity, R.id.btnNavTransactions, screen, "transactions", TransactionHistoryActivity.class);
        bind(activity, R.id.btnNavReports, screen, "reports", ReportsActivity.class);
        bind(activity, R.id.btnNavTransfers, screen, "transfers", StockTransfersActivity.class);
        bind(activity, R.id.btnNavStaff, screen, "staff", StaffActivity.class);
        bind(activity, R.id.btnNavStoreAdmins, screen, "store_admins", StoreAdminsActivity.class);
        bind(activity, R.id.btnNavStores, screen, "stores", StoresActivity.class);
        bind(activity, R.id.btnNavInsights, screen, "insights", StockEaseInsightsActivity.class);
        bind(activity, R.id.btnNavBusinessProgress, screen, "business_progress", BusinessProgressActivity.class);
        bind(activity, R.id.btnNavProfile, screen, "profile", ProfileActivity.class);
        bind(activity, R.id.btnNavSubscription, screen, "subscription", SubscriptionActivity.class);
        bind(activity, R.id.btnNavSettings, screen, "settings", SettingsActivity.class);

        View logout = activity.findViewById(R.id.btnNavLogout);
        if (logout != null) {
            logout.setOnClickListener(v -> ApiSession.logout(activity));
        }

        applyRoleVisibility(activity, role);
        highlight(activity, screen);
    }

    /** Same role → dashboard mapping as LoginActivity's login routing. */
    private static Class<?> dashboardTargetFor(String role) {
        switch (role) {
            case "super_admin":
            case "store_owner":
                return SuperAdminDashboardActivity.class;
            case "inventory_checker":
                return InventoryDashboardActivity.class;
            case "staff":
                return CashierDashboardActivity.class;
            case "manager":
            case "store_admin":
            default:
                return StoreAdminDashboardActivity.class;
        }
    }

    private static void bind(AppCompatActivity activity, int viewId, final String screen,
                             final String key, final Class<?> target) {
        View view = activity.findViewById(viewId);
        if (view == null) {
            return;
        }
        view.setOnClickListener(v -> {
            if (key.equals(screen) || activity.getClass().equals(target)) {
                return; // already on this screen
            }
            Intent intent = new Intent(activity, target);
            if (activity.getIntent() != null && activity.getIntent().getExtras() != null) {
                intent.putExtras(activity.getIntent().getExtras());
            }
            ScreenTransitions.open(activity, intent);
        });
    }

    /** Hides the items the signed-in role may not open (the API re-checks anyway). */
    private static void applyRoleVisibility(AppCompatActivity activity, String role) {
        boolean isStaff = "staff".equals(role);
        boolean isInventory = "inventory_checker".equals(role);
        boolean isCashierOrInventory = isStaff || isInventory;
        boolean isOwnerOrSuper = "super_admin".equals(role) || "store_owner".equals(role);
        boolean isManagerLevel = "manager".equals(role) || isOwnerOrSuper;

        setVisible(activity, R.id.btnNavPos, !isInventory && !"store_owner".equals(role));
        setVisible(activity, R.id.btnNavProducts, !isStaff);
        setVisible(activity, R.id.btnNavTransactions, !"store_owner".equals(role));
        setVisible(activity, R.id.btnNavReports, !isCashierOrInventory);
        setVisible(activity, R.id.btnNavTransfers, !isStaff && !"store_owner".equals(role));
        setVisible(activity, R.id.btnNavStaff, isManagerLevel);
        setVisible(activity, R.id.btnNavStoreAdmins, isManagerLevel);
        setVisible(activity, R.id.btnNavStores, isManagerLevel);
        setVisible(activity, R.id.btnNavInsights, !isCashierOrInventory);
        setVisible(activity, R.id.btnNavBusinessProgress, !isCashierOrInventory);
        setVisible(activity, R.id.btnNavSubscription, !isCashierOrInventory);
    }

    private static void setVisible(AppCompatActivity activity, int viewId, boolean visible) {
        View view = activity.findViewById(viewId);
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /** Marks the current screen's item: bold white text on the active tint. */
    private static void highlight(AppCompatActivity activity, String screen) {
        int active = R.id.btnNavDashboard;
        switch (screen) {
            case "pos": active = R.id.btnNavPos; break;
            case "inventory": active = R.id.btnNavInventory; break;
            case "products": active = R.id.btnNavProducts; break;
            case "transactions": active = R.id.btnNavTransactions; break;
            case "reports": active = R.id.btnNavReports; break;
            case "transfers": active = R.id.btnNavTransfers; break;
            case "staff": active = R.id.btnNavStaff; break;
            case "store_admins": active = R.id.btnNavStoreAdmins; break;
            case "stores": active = R.id.btnNavStores; break;
            case "insights": active = R.id.btnNavInsights; break;
            case "business_progress": active = R.id.btnNavBusinessProgress; break;
            case "profile": active = R.id.btnNavProfile; break;
            case "subscription": active = R.id.btnNavSubscription; break;
            case "settings": active = R.id.btnNavSettings; break;
            default: break;
        }

        View view = activity.findViewById(active);
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
            tv.setTextColor(ContextCompat.getColor(activity, R.color.white));
            tv.setBackgroundColor(ContextCompat.getColor(activity, R.color.sidebar_item_active));
        }
    }
}
