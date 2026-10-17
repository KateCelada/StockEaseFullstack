package com.example.stockease;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock dashboard screen. Sidebar nav items show toasts until they are wired
 * to real flows; Profile and Log out are live.
 */
public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Hide action bar for a clean UI matching the image
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        View btnNavDashboard = findViewById(R.id.btnNavDashboard);
        View btnNavPos = findViewById(R.id.btnNavPos);
        View btnNavInventory = findViewById(R.id.btnNavInventory);
        View btnNavTransactions = findViewById(R.id.btnNavTransactions);
        View btnNavSettings = findViewById(R.id.btnNavSettings);
        View btnNavLogout = findViewById(R.id.btnNavLogout);
        View btnNavProfile = findViewById(R.id.btnNavProfile);

        if (btnNavDashboard != null) {
            btnNavDashboard.setOnClickListener(v ->
                    Toast.makeText(this, "Navigating to Dashboard", Toast.LENGTH_SHORT).show());
        }
        if (btnNavPos != null) {
            btnNavPos.setOnClickListener(v ->
                    Toast.makeText(this, "Navigating to POS/Sales", Toast.LENGTH_SHORT).show());
        }
        if (btnNavInventory != null) {
            btnNavInventory.setOnClickListener(v ->
                    Toast.makeText(this, "Navigating to Inventory", Toast.LENGTH_SHORT).show());
        }
        if (btnNavTransactions != null) {
            btnNavTransactions.setOnClickListener(v ->
                    Toast.makeText(this, "Navigating to Transactions", Toast.LENGTH_SHORT).show());
        }
        if (btnNavSettings != null) {
            btnNavSettings.setOnClickListener(v ->
                    Toast.makeText(this, "Navigating to Settings", Toast.LENGTH_SHORT).show());
        }
        if (btnNavLogout != null) {
            btnNavLogout.setOnClickListener(v -> ApiSession.logout(this));
        }
        if (btnNavProfile != null) {
            btnNavProfile.setOnClickListener(v -> {
                Intent intent = new Intent(DashboardActivity.this, ProfileActivity.class);
                if (getIntent().getExtras() != null) {
                    intent.putExtras(getIntent().getExtras());
                }
                ScreenTransitions.open(DashboardActivity.this, intent);
            });
        }

        // Every metric and content card opens a sheet with its own numbers
        bindCard(R.id.cardMetricSales, "Sales");
        bindCard(R.id.cardMetricTxns, "Transactions");
        bindCard(R.id.cardMetricItems, "Items Sold");
        bindCard(R.id.cardMetricTime, "Current Time");
        bindCard(R.id.cardSalesSummary, "Sales Summary");
        bindCard(R.id.cardTopSellers, "Top Sellers");
        bindCard(R.id.cardRecentTxns, "Recent Transactions");

        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));

        // Collapsible sidebar sections (accordion): OVERVIEW opens by default
        SidebarSection.attach(this, R.id.navHeaderOverview, R.id.navArrowOverview, R.id.navSectionOverviewItems, true);
        SidebarSection.attach(this, R.id.navHeaderManagement, R.id.navArrowManagement, R.id.navSectionManagementItems, false);
        SidebarSection.attach(this, R.id.navHeaderAnalytics, R.id.navArrowAnalytics, R.id.navSectionAnalyticsItems, false);
        SidebarSection.attach(this, R.id.navHeaderSystem, R.id.navArrowSystem, R.id.navSectionSystemItems, false);
    }

    /** Makes one card open a detail sheet showing the text it displays. */
    private void bindCard(int viewId, String title) {
        View card = findViewById(viewId);
        if (card != null) {
            card.setOnClickListener(v -> showCardDetails(card, title));
        }
    }

    private void showCardDetails(View card, String title) {
        List<String> rows = new ArrayList<>();
        collectTexts(card, rows);
        CardDetailDialog.show(this, title, rows.toArray(new String[0]));
    }

    private void collectTexts(View view, List<String> out) {
        if (view instanceof TextView) {
            String text = ((TextView) view).getText().toString();
            if (!TextUtils.isEmpty(text)) {
                out.add(text);
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTexts(group.getChildAt(i), out);
            }
        }
    }
}
