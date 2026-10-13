package com.example.stockease;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class BusinessProgressActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode"; // "business" or "branch"
    public static final String EXTRA_OWNER_ID = "owner_id";
    public static final String EXTRA_STORE_ID = "store_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_SUBTITLE = "subtitle";

    private String mode = "business";
    private int ownerId = -1;
    private int storeId = -1;
    private String title = "";
    private String subtitle = "";
    private int userId = -1;
    private String role = "";

    private ImageButton btnProgressBack;
    private TextView tvProgressTitle, tvProgressSubtitle;
    private Button btnProgressRefresh;
    private Button btnFilterToday, btnFilterWeek, btnFilterMonth, btnFilterCustom;
    private TextView tvMetricTotalSales, tvMetricTransactions, tvMetricProductsSold, tvMetricNetProfit;
    private TextView tvChartHint, tvChartDateRange;
    private SimpleLineChartView progressLineChart;
    private LinearLayout cardLineChartContainer;
    private TextView tvSectionOneTitle, tvSectionOneBadge;
    private LinearLayout llSectionOneRows, containerSectionTwo, llSectionTwoRows;
    private TextView tvAccountsCountBadge;

    private String selectedPeriod = "this_week";
    private String customStartDate = "";
    private String customEndDate = "";

    private JSONArray cachedBranches = null;
    private JSONArray cachedRankedProducts = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_progress);

        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null || mode.isEmpty()) mode = "business";
        ownerId = getIntent().getIntExtra(EXTRA_OWNER_ID, -1);
        storeId = getIntent().getIntExtra(EXTRA_STORE_ID, -1);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        subtitle = getIntent().getStringExtra(EXTRA_SUBTITLE);
        userId = getIntent().getIntExtra("user_id", -1);
        role = getIntent().getStringExtra("role");

        initViews();
        setupListeners();
        updatePeriodTabsUI();
        loadProgressData();

        if ("branch".equals(mode) && storeId > 0) {
            loadStoreAccounts();
        }

        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));
    }

    private void initViews() {
        btnProgressBack = findViewById(R.id.btnProgressBack);
        tvProgressTitle = findViewById(R.id.tvProgressTitle);
        tvProgressSubtitle = findViewById(R.id.tvProgressSubtitle);
        btnProgressRefresh = findViewById(R.id.btnProgressRefresh);

        btnFilterToday = findViewById(R.id.btnFilterToday);
        btnFilterWeek = findViewById(R.id.btnFilterWeek);
        btnFilterMonth = findViewById(R.id.btnFilterMonth);
        btnFilterCustom = findViewById(R.id.btnFilterCustom);

        tvMetricTotalSales = findViewById(R.id.tvMetricTotalSales);
        tvMetricTransactions = findViewById(R.id.tvMetricTransactions);
        tvMetricProductsSold = findViewById(R.id.tvMetricProductsSold);
        tvMetricNetProfit = findViewById(R.id.tvMetricNetProfit);

        tvChartHint = findViewById(R.id.tvChartHint);
        tvChartDateRange = findViewById(R.id.tvChartDateRange);
        progressLineChart = findViewById(R.id.progressLineChart);
        cardLineChartContainer = findViewById(R.id.cardLineChartContainer);

        tvSectionOneTitle = findViewById(R.id.tvSectionOneTitle);
        tvSectionOneBadge = findViewById(R.id.tvSectionOneBadge);
        llSectionOneRows = findViewById(R.id.llSectionOneRows);

        containerSectionTwo = findViewById(R.id.containerSectionTwo);
        llSectionTwoRows = findViewById(R.id.llSectionTwoRows);
        tvAccountsCountBadge = findViewById(R.id.tvAccountsCountBadge);

        if (title != null && !title.isEmpty()) {
            tvProgressTitle.setText(title);
        } else {
            tvProgressTitle.setText("business".equals(mode) ? "Business Progress" : "Branch Progress");
        }

        if (subtitle != null && !subtitle.isEmpty()) {
            tvProgressSubtitle.setText(subtitle);
        }

        if ("business".equals(mode)) {
            tvChartHint.setText("Tap chart to inspect branch contribution breakdown");
            tvSectionOneTitle.setText("Branch Contribution Breakdown");
            containerSectionTwo.setVisibility(View.GONE);
        } else {
            tvChartHint.setText("Tap chart to inspect top-selling products");
            tvSectionOneTitle.setText("Top-Selling Products Sold");
            containerSectionTwo.setVisibility(View.VISIBLE);
        }
    }

    private void setupListeners() {
        if (btnProgressBack != null) {
            btnProgressBack.setOnClickListener(v -> finish());
        }

        if (btnProgressRefresh != null) {
            btnProgressRefresh.setOnClickListener(v -> {
                loadProgressData();
                if ("branch".equals(mode) && storeId > 0) loadStoreAccounts();
            });
        }

        if (btnFilterToday != null) {
            btnFilterToday.setOnClickListener(v -> selectPeriod("today"));
        }
        if (btnFilterWeek != null) {
            btnFilterWeek.setOnClickListener(v -> selectPeriod("this_week"));
        }
        if (btnFilterMonth != null) {
            btnFilterMonth.setOnClickListener(v -> selectPeriod("this_month"));
        }
        if (btnFilterCustom != null) {
            btnFilterCustom.setOnClickListener(v -> showCustomDatePicker());
        }

        View.OnClickListener chartClickListener = v -> {
            if ("business".equals(mode)) {
                showBranchContributionModal();
            } else {
                showTopProductsModal();
            }
        };

        if (progressLineChart != null) progressLineChart.setOnClickListener(chartClickListener);
        if (cardLineChartContainer != null) cardLineChartContainer.setOnClickListener(chartClickListener);
    }

    private void selectPeriod(String period) {
        selectedPeriod = period;
        customStartDate = "";
        customEndDate = "";
        updatePeriodTabsUI();
        loadProgressData();
    }

    private void updatePeriodTabsUI() {
        int activeBg = 0xFF2563EB;
        int activeText = 0xFFFFFFFF;
        int inactiveBg = 0xFFE2E8F0;
        int inactiveText = 0xFF475569;

        setButtonTab(btnFilterToday, "today".equals(selectedPeriod), activeBg, activeText, inactiveBg, inactiveText);
        setButtonTab(btnFilterWeek, "this_week".equals(selectedPeriod), activeBg, activeText, inactiveBg, inactiveText);
        setButtonTab(btnFilterMonth, "this_month".equals(selectedPeriod), activeBg, activeText, inactiveBg, inactiveText);

        boolean isCustom = "custom".equals(selectedPeriod);
        setButtonTab(btnFilterCustom, isCustom, activeBg, activeText, inactiveBg, inactiveText);
        if (isCustom && !customStartDate.isEmpty()) {
            btnFilterCustom.setText(customStartDate + " - " + customEndDate);
            tvChartDateRange.setText(customStartDate + " to " + customEndDate);
        } else {
            btnFilterCustom.setText("Custom 📅");
            if ("today".equals(selectedPeriod)) tvChartDateRange.setText("Today");
            else if ("this_month".equals(selectedPeriod)) tvChartDateRange.setText("This Month");
            else tvChartDateRange.setText("This Week");
        }
    }

    private void setButtonTab(Button btn, boolean active, int activeBg, int activeText, int inactiveBg, int inactiveText) {
        if (btn == null) return;
        btn.setBackgroundTintList(ColorStateList.valueOf(active ? activeBg : inactiveBg));
        btn.setTextColor(active ? activeText : inactiveText);
    }

    private void showCustomDatePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog startDialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String startStr = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            DatePickerDialog endDialog = new DatePickerDialog(this, (view2, year2, month2, dayOfMonth2) -> {
                String endStr = String.format(Locale.US, "%04d-%02d-%02d", year2, month2 + 1, dayOfMonth2);
                selectedPeriod = "custom";
                customStartDate = startStr;
                customEndDate = endStr;
                updatePeriodTabsUI();
                loadProgressData();
            }, year, month, dayOfMonth);
            endDialog.setTitle("Select End Date");
            endDialog.show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        startDialog.setTitle("Select Start Date");
        startDialog.show();
    }

    private void loadProgressData() {
        StringBuilder urlBuilder = new StringBuilder(ApiConfig.getUrl("get_business_progress.php"));
        urlBuilder.append("?period=").append(selectedPeriod);

        if ("custom".equals(selectedPeriod) && !customStartDate.isEmpty() && !customEndDate.isEmpty()) {
            try {
                urlBuilder.append("&start_date=").append(URLEncoder.encode(customStartDate, "UTF-8"));
                urlBuilder.append("&end_date=").append(URLEncoder.encode(customEndDate, "UTF-8"));
            } catch (Exception ignored) {}
        }

        if ("branch".equals(mode) && storeId > 0) {
            urlBuilder.append("&store_id=").append(storeId);
        } else if (ownerId > 0) {
            urlBuilder.append("&owner_id=").append(ownerId);
        }

        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                urlBuilder.toString(),
                null,
                response -> {
                    try {
                        if (response.optBoolean("success", false)) {
                            renderProgress(response);
                        } else {
                            Toast.makeText(this, response.optString("message", "Unable to load data"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to load progress data", Toast.LENGTH_SHORT).show();
                }
        );
        queue.add(req);
    }

    private void renderProgress(JSONObject data) {
        // 1. Summary Metrics
        JSONObject summary = data.optJSONObject("summary");
        if (summary != null) {
            double totalSales = summary.optDouble("total_sales", 0.0);
            int txCount = summary.optInt("transaction_count", 0);
            int productsSold = summary.optInt("products_sold", 0);
            double netProfit = summary.optDouble("net_profit", 0.0);

            tvMetricTotalSales.setText(String.format(Locale.US, "₱%.2f", totalSales));
            tvMetricTransactions.setText(String.valueOf(txCount));
            tvMetricProductsSold.setText(String.valueOf(productsSold));
            tvMetricNetProfit.setText(String.format(Locale.US, "₱%.2f", netProfit));
        }

        // 2. Line Chart
        JSONArray lineChart = data.optJSONArray("line_chart");
        List<Float> pts = new ArrayList<>();
        List<String> lbls = new ArrayList<>();
        if (lineChart != null && lineChart.length() > 0) {
            for (int i = 0; i < lineChart.length(); i++) {
                JSONObject pt = lineChart.optJSONObject(i);
                if (pt != null) {
                    pts.add((float) pt.optDouble("sales", 0.0));
                    lbls.add(pt.optString("label", ""));
                }
            }
        } else {
            pts.add(0f); pts.add(0f); pts.add(0f);
            lbls.add("Mon"); lbls.add("Wed"); lbls.add("Fri");
        }
        progressLineChart.setData(pts, lbls);

        // 3. Cache Data
        cachedBranches = data.optJSONArray("branches");
        cachedRankedProducts = data.optJSONArray("ranked_products");

        // 4. Render Section 1
        llSectionOneRows.removeAllViews();
        if ("business".equals(mode)) {
            renderBranchContributions(cachedBranches);
        } else {
            renderTopProducts(cachedRankedProducts);
        }
    }

    private void renderBranchContributions(JSONArray branches) {
        if (branches == null || branches.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No branches found for this business.");
            empty.setTextColor(0xFF64748B);
            empty.setPadding(0, 16, 0, 16);
            llSectionOneRows.addView(empty);
            return;
        }

        for (int i = 0; i < branches.length(); i++) {
            JSONObject b = branches.optJSONObject(i);
            if (b == null) continue;

            int bId = b.optInt("store_id", 0);
            String bName = b.optString("store_name", "Branch");
            String bLoc = b.optString("location", "—");
            double bSales = b.optDouble("total_sales", 0.0);
            double bPct = b.optDouble("contribution_pct", 0.0);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(16, 14, 16, 14);
            row.setBackgroundResource(R.drawable.bg_input_field);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 10);
            row.setLayoutParams(lp);

            LinearLayout colInfo = new LinearLayout(this);
            colInfo.setOrientation(LinearLayout.VERTICAL);
            colInfo.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));

            TextView tvName = new TextView(this);
            tvName.setText("📍 " + bName);
            tvName.setTextColor(0xFF0F172A);
            tvName.setTextSize(14f);
            tvName.setTypeface(null, Typeface.BOLD);

            TextView tvLoc = new TextView(this);
            tvLoc.setText(bLoc);
            tvLoc.setTextColor(0xFF64748B);
            tvLoc.setTextSize(11f);

            colInfo.addView(tvName);
            colInfo.addView(tvLoc);

            LinearLayout colMetrics = new LinearLayout(this);
            colMetrics.setOrientation(LinearLayout.VERTICAL);
            colMetrics.setGravity(Gravity.END);
            colMetrics.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvSales = new TextView(this);
            tvSales.setText(String.format(Locale.US, "₱%.2f", bSales));
            tvSales.setTextColor(0xFF0F172A);
            tvSales.setTextSize(14f);
            tvSales.setTypeface(null, Typeface.BOLD);

            TextView tvPct = new TextView(this);
            tvPct.setText(bPct + "% of total");
            tvPct.setTextColor(0xFF2563EB);
            tvPct.setTextSize(11f);
            tvPct.setTypeface(null, Typeface.BOLD);

            colMetrics.addView(tvSales);
            colMetrics.addView(tvPct);

            row.addView(colInfo);
            row.addView(colMetrics);

            // Clicking a branch drills into its branch-specific performance!
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                Intent drillIntent = new Intent(this, BusinessProgressActivity.class);
                drillIntent.putExtra(EXTRA_MODE, "branch");
                drillIntent.putExtra(EXTRA_STORE_ID, bId);
                drillIntent.putExtra(EXTRA_TITLE, bName);
                drillIntent.putExtra(EXTRA_SUBTITLE, "Location: " + bLoc);
                drillIntent.putExtra("user_id", userId);
                drillIntent.putExtra("role", role);
                startActivity(drillIntent);
            });

            llSectionOneRows.addView(row);
        }
    }

    private void renderTopProducts(JSONArray products) {
        if (products == null || products.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No products sold during this period.");
            empty.setTextColor(0xFF64748B);
            empty.setPadding(0, 16, 0, 16);
            llSectionOneRows.addView(empty);
            return;
        }

        for (int i = 0; i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (p == null) continue;

            String pName = p.optString("product_name", "Product");
            String cat = p.optString("category_name", "General");
            int soldQty = p.optInt("total_sold_qty", 0);
            double revenue = p.optDouble("total_revenue", 0.0);
            double profit = p.optDouble("total_net_profit", 0.0);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(16, 12, 16, 12);
            row.setBackgroundResource(R.drawable.bg_input_field);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 8);
            row.setLayoutParams(lp);

            TextView tvRank = new TextView(this);
            tvRank.setText("#" + (i + 1));
            tvRank.setTextColor(0xFF2563EB);
            tvRank.setTextSize(13f);
            tvRank.setTypeface(null, Typeface.BOLD);
            tvRank.setLayoutParams(new LinearLayout.LayoutParams(42, LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout colP = new LinearLayout(this);
            colP.setOrientation(LinearLayout.VERTICAL);
            colP.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));

            TextView tvName = new TextView(this);
            tvName.setText(pName);
            tvName.setTextColor(0xFF0F172A);
            tvName.setTextSize(13f);
            tvName.setTypeface(null, Typeface.BOLD);

            TextView tvCat = new TextView(this);
            tvCat.setText("Category: " + cat);
            tvCat.setTextColor(0xFF64748B);
            tvCat.setTextSize(11f);

            colP.addView(tvName);
            colP.addView(tvCat);

            LinearLayout colS = new LinearLayout(this);
            colS.setOrientation(LinearLayout.VERTICAL);
            colS.setGravity(Gravity.END);
            colS.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvQty = new TextView(this);
            tvQty.setText(soldQty + " Sold (₱" + String.format(Locale.US, "%.2f", revenue) + ")");
            tvQty.setTextColor(0xFF0F172A);
            tvQty.setTextSize(12f);
            tvQty.setTypeface(null, Typeface.BOLD);

            TextView tvProfit = new TextView(this);
            tvProfit.setText("Profit: ₱" + String.format(Locale.US, "%.2f", profit));
            tvProfit.setTextColor(0xFF10B981);
            tvProfit.setTextSize(11f);

            colS.addView(tvQty);
            colS.addView(tvProfit);

            row.addView(tvRank);
            row.addView(colP);
            row.addView(colS);

            llSectionOneRows.addView(row);
        }
    }

    private void loadStoreAccounts() {
        String url = ApiConfig.getUrl("get_staff.php?store_id=" + storeId);
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    try {
                        if (response.optBoolean("success", false)) {
                            JSONArray staff = response.optJSONArray("staff");
                            renderStoreAccounts(staff);
                        }
                    } catch (Exception ignored) {}
                },
                error -> {}
        );
        queue.add(req);
    }

    private void renderStoreAccounts(JSONArray staff) {
        if (llSectionTwoRows == null) return;
        llSectionTwoRows.removeAllViews();

        java.util.List<JSONObject> visibleStaff = new java.util.ArrayList<>();
        if (staff != null) {
            for (int i = 0; i < staff.length(); i++) {
                JSONObject u = staff.optJSONObject(i);
                if (u == null) continue;
                String userRole = u.optString("role", "staff");
                if ("super_admin".equalsIgnoreCase(userRole) && !"super_admin".equalsIgnoreCase(role)) {
                    continue; // System Admin visible ONLY to System Admin
                }
                visibleStaff.add(u);
            }
        }

        if (visibleStaff.isEmpty()) {
            tvAccountsCountBadge.setText("0 Accounts");
            TextView empty = new TextView(this);
            empty.setText("No staff or admin accounts registered for this store branch.");
            empty.setTextColor(0xFF64748B);
            empty.setPadding(0, 12, 0, 12);
            llSectionTwoRows.addView(empty);
            return;
        }

        tvAccountsCountBadge.setText(visibleStaff.size() + " Accounts");

        for (int i = 0; i < visibleStaff.size(); i++) {
            JSONObject u = visibleStaff.get(i);
            if (u == null) continue;

            String name = u.optString("full_name", "Staff Member");
            String username = u.optString("username", "user");
            String userRole = u.optString("role", "staff");

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(16, 12, 16, 12);
            row.setBackgroundResource(R.drawable.bg_input_field);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 8);
            row.setLayoutParams(lp);

            LinearLayout colU = new LinearLayout(this);
            colU.setOrientation(LinearLayout.VERTICAL);
            colU.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvN = new TextView(this);
            tvN.setText(name);
            tvN.setTextColor(0xFF0F172A);
            tvN.setTextSize(13f);
            tvN.setTypeface(null, Typeface.BOLD);

            TextView tvUn = new TextView(this);
            tvUn.setText("@" + username);
            tvUn.setTextColor(0xFF64748B);
            tvUn.setTextSize(11f);

            colU.addView(tvN);
            colU.addView(tvUn);

            TextView tvR = new TextView(this);
            String displayRole = "Cashier (Staff)";
            int badgeColor = 0xFF1E40AF;
            int badgeBg = R.drawable.bg_badge_blue;

            if ("store_admin".equalsIgnoreCase(userRole)) {
                displayRole = "Store Admin";
                badgeColor = 0xFF059669;
                badgeBg = R.drawable.bg_badge_emerald;
            } else if ("inventory_checker".equalsIgnoreCase(userRole)) {
                displayRole = "Inventory Checker";
                badgeColor = 0xFF7C3AED;
                badgeBg = R.drawable.bg_badge_gray;
            } else if ("manager".equalsIgnoreCase(userRole)) {
                displayRole = "Store Manager";
                badgeColor = 0xFFD97706;
                badgeBg = R.drawable.bg_badge_gray;
            }

            tvR.setText(displayRole);
            tvR.setTextColor(badgeColor);
            tvR.setTextSize(11f);
            tvR.setTypeface(null, Typeface.BOLD);
            tvR.setBackgroundResource(badgeBg);
            tvR.setPadding(12, 4, 12, 4);

            row.addView(colU);
            row.addView(tvR);

            llSectionTwoRows.addView(row);
        }
    }

    private void showBranchContributionModal() {
        if (cachedBranches == null || cachedBranches.length() == 0) {
            Toast.makeText(this, "No branch contribution data available.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cachedBranches.length(); i++) {
            JSONObject b = cachedBranches.optJSONObject(i);
            if (b == null) continue;
            String name = b.optString("store_name", "Branch");
            double sales = b.optDouble("total_sales", 0.0);
            double pct = b.optDouble("contribution_pct", 0.0);
            sb.append("• ").append(name).append("\n")
              .append("   Contribution: ").append(pct).append("%  |  Sales: ₱").append(String.format(Locale.US, "%.2f", sales)).append("\n\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("Branch Contribution Breakdown")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }

    private void showTopProductsModal() {
        if (cachedRankedProducts == null || cachedRankedProducts.length() == 0) {
            Toast.makeText(this, "No product sales data recorded for this period.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        int max = Math.min(cachedRankedProducts.length(), 15);
        for (int i = 0; i < max; i++) {
            JSONObject p = cachedRankedProducts.optJSONObject(i);
            if (p == null) continue;
            String name = p.optString("product_name", "Item");
            int qty = p.optInt("total_sold_qty", 0);
            double rev = p.optDouble("total_revenue", 0.0);
            sb.append("#").append(i + 1).append(" ").append(name).append("\n")
              .append("   Sold: ").append(qty).append(" units  |  Revenue: ₱").append(String.format(Locale.US, "%.2f", rev)).append("\n\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("Top Selling Products")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }
}
