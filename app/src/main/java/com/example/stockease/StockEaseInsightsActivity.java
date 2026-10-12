package com.example.stockease;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class StockEaseInsightsActivity extends AppCompatActivity {

    private static final String INSIGHTS_URL = ApiConfig.getUrl("get_insights.php");

    private int userId = -1;
    private int storeId = -1;
    private String role = "";
    private String fullName = "";

    private String activeRange = "30";

    private Button btnRange7d, btnRange30d, btnRangeMonth;
    private TextView tvInsightsLoadingStatus;
    private TextView tvInsightsTotalSales, tvInsightsTransactions, tvInsightsAvgOrder, tvInsightsUnitsSold;
    private LinearLayout llTransferOpportunities, llStockoutRisks, llFastMoving, llStoreComparisonRows;
    private View cardStoreComparison;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stockease_insights);

        if (getIntent() != null) {
            userId = getIntent().getIntExtra("user_id", -1);
            storeId = getIntent().getIntExtra("store_id", -1);
            role = getIntent().getStringExtra("role");
            fullName = getIntent().getStringExtra("full_name");
        }

        if (userId <= 0 || (!"super_admin".equals(role) && !"store_admin".equals(role) && !"manager".equals(role) && !"store_owner".equals(role))) {
            returnToLogin("Insights are available to Super Admin, Manager, and Store Admin accounts.");
            return;
        }

        ImageButton btnInsightsBack = findViewById(R.id.btnInsightsBack);
        if (btnInsightsBack != null) {
            btnInsightsBack.setOnClickListener(v -> finish());
        }

        btnRange7d = findViewById(R.id.btnRange7d);
        btnRange30d = findViewById(R.id.btnRange30d);
        btnRangeMonth = findViewById(R.id.btnRangeMonth);
        tvInsightsLoadingStatus = findViewById(R.id.tvInsightsLoadingStatus);

        tvInsightsTotalSales = findViewById(R.id.tvInsightsTotalSales);
        tvInsightsTransactions = findViewById(R.id.tvInsightsTransactions);
        tvInsightsAvgOrder = findViewById(R.id.tvInsightsAvgOrder);
        tvInsightsUnitsSold = findViewById(R.id.tvInsightsUnitsSold);

        llTransferOpportunities = findViewById(R.id.llTransferOpportunities);
        llStockoutRisks = findViewById(R.id.llStockoutRisks);
        llFastMoving = findViewById(R.id.llFastMoving);
        llStoreComparisonRows = findViewById(R.id.llStoreComparisonRows);
        cardStoreComparison = findViewById(R.id.cardStoreComparison);

        if (!"super_admin".equals(role) && cardStoreComparison != null) {
            cardStoreComparison.setVisibility(View.GONE);
        }

        setupRangeFilters();
        loadInsights();

        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));
    }

    private void setupRangeFilters() {
        if (btnRange7d != null) {
            btnRange7d.setOnClickListener(v -> {
                activeRange = "7";
                updateFilterUI();
                loadInsights();
            });
        }
        if (btnRange30d != null) {
            btnRange30d.setOnClickListener(v -> {
                activeRange = "30";
                updateFilterUI();
                loadInsights();
            });
        }
        if (btnRangeMonth != null) {
            btnRangeMonth.setOnClickListener(v -> {
                activeRange = "this_month";
                updateFilterUI();
                loadInsights();
            });
        }
    }

    private void updateFilterUI() {
        int activeBg = 0xFF2563EB;
        int activeText = 0xFFFFFFFF;
        int inactiveText = 0xFF94A3B8;

        btnRange7d.setBackgroundColor("7".equals(activeRange) ? activeBg : 0x00000000);
        btnRange7d.setTextColor("7".equals(activeRange) ? activeText : inactiveText);

        btnRange30d.setBackgroundColor("30".equals(activeRange) ? activeBg : 0x00000000);
        btnRange30d.setTextColor("30".equals(activeRange) ? activeText : inactiveText);

        btnRangeMonth.setBackgroundColor("this_month".equals(activeRange) ? activeBg : 0x00000000);
        btnRangeMonth.setTextColor("this_month".equals(activeRange) ? activeText : inactiveText);
    }

    private void loadInsights() {
        if (tvInsightsLoadingStatus != null) {
            tvInsightsLoadingStatus.setVisibility(View.VISIBLE);
            tvInsightsLoadingStatus.setText("Calculating inventory velocity and store trends...");
        }

        RequestQueue queue = ApiSession.getRequestQueue(this);

        String url = INSIGHTS_URL + "?range=" + activeRange;
        if (storeId > 0 && "store_admin".equals(role)) {
            url += "&store_id=" + storeId;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            if (tvInsightsLoadingStatus != null) {
                                tvInsightsLoadingStatus.setVisibility(View.GONE);
                            }
                            renderOverview(response.optJSONObject("overview"));
                            renderTransferOpportunities(response.optJSONArray("transfer_recommendations"));
                            renderStockoutRisks(response.optJSONArray("stockout_risks"));
                            renderFastMoving(response.optJSONArray("fast_moving"));
                            renderStoreComparison(response.optJSONArray("store_comparison"));
                        } else {
                            showStatusMessage(response.optString("message", "Unable to load insights"));
                        }
                    } catch (JSONException e) {
                        showStatusMessage("Malformed insights response");
                    }
                },
                error -> {
                    if (ApiSession.isPremiumRequired(error)) {
                        showStatusMessage("StockEase Insights is a Premium feature.\nUpgrade your plan to unlock smart velocity analytics and recommendations.");
                        ApiSession.showPremiumUpgradeDialog(
                                this,
                                "StockEase Insights",
                                "StockEase Insights is available with StockEase Premium. Access smart inventory velocity, transfer recommendations, and multi-store analytics."
                        );
                        return;
                    }
                    if (ApiSession.handleAuthError(this, error)) return;
                    showStatusMessage("Network error calculating insights");
                }
        );

        queue.add(request);
    }

    private void renderOverview(JSONObject ov) {
        if (ov == null) return;
        double totalSales = ov.optDouble("total_sales", 0.0);
        int tx = ov.optInt("total_transactions", 0);
        double avg = ov.optDouble("average_transaction_value", 0.0);
        int units = ov.optInt("total_units_sold", 0);

        if (tvInsightsTotalSales != null) tvInsightsTotalSales.setText(String.format(Locale.US, "₱%.2f", totalSales));
        if (tvInsightsTransactions != null) tvInsightsTransactions.setText(String.valueOf(tx));
        if (tvInsightsAvgOrder != null) tvInsightsAvgOrder.setText(String.format(Locale.US, "₱%.2f", avg));
        if (tvInsightsUnitsSold != null) tvInsightsUnitsSold.setText(String.valueOf(units));
    }

    private void renderTransferOpportunities(JSONArray arr) {
        if (llTransferOpportunities == null) return;
        llTransferOpportunities.removeAllViews();

        float density = getResources().getDisplayMetrics().density;

        if (arr == null || arr.length() == 0) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No critical inventory imbalances detected across stores for this period.");
            emptyTv.setTextColor(0xFF64748B);
            emptyTv.setTextSize(12f);
            emptyTv.setPadding(0, (int) (12 * density), 0, (int) (12 * density));
            llTransferOpportunities.addView(emptyTv);
            return;
        }

        for (int i = 0; i < arr.length(); i++) {
            JSONObject rec = arr.optJSONObject(i);
            if (rec == null) continue;

            String prodName = rec.optString("product_name", "Product");
            int srcId = rec.optInt("source_store_id", 0);
            String srcName = rec.optString("source_store_name", "Source Store");
            int srcStock = rec.optInt("source_current_stock", 0);
            int destId = rec.optInt("destination_store_id", 0);
            String destName = rec.optString("destination_store_name", "Dest Store");
            int destStock = rec.optInt("destination_current_stock", 0);
            int recQty = rec.optInt("recommended_quantity", 0);
            int srcProdId = rec.optInt("source_product_id", 0);
            String reason = rec.optString("reason", "");

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_input_field);
            card.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));
            LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpCard.bottomMargin = (int) (10 * density);
            card.setLayoutParams(lpCard);

            // Row 1: Product & Recommended Qty
            LinearLayout row1 = new LinearLayout(this);
            row1.setOrientation(LinearLayout.HORIZONTAL);
            row1.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvTitle = new TextView(this);
            tvTitle.setText(prodName);
            tvTitle.setTextSize(14f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            tvTitle.setTextColor(0xFF0F172A);
            tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row1.addView(tvTitle);

            TextView tvBadge = new TextView(this);
            tvBadge.setText("Transfer " + recQty + " units");
            tvBadge.setTextSize(11f);
            tvBadge.setTypeface(null, Typeface.BOLD);
            tvBadge.setTextColor(0xFF2563EB);
            tvBadge.setBackgroundResource(R.drawable.bg_badge_blue);
            tvBadge.setPadding((int) (8 * density), (int) (3 * density), (int) (8 * density), (int) (3 * density));
            row1.addView(tvBadge);

            card.addView(row1);

            // Row 2: Route
            TextView tvRoute = new TextView(this);
            tvRoute.setText(srcName + " (" + srcStock + " in stock)  →  " + destName + " (" + destStock + " in stock)");
            tvRoute.setTextSize(12f);
            tvRoute.setTypeface(null, Typeface.BOLD);
            tvRoute.setTextColor(0xFF334155);
            tvRoute.setPadding(0, (int) (4 * density), 0, (int) (4 * density));
            card.addView(tvRoute);

            // Row 3: Reason explanation
            TextView tvReason = new TextView(this);
            tvReason.setText(reason);
            tvReason.setTextSize(12f);
            tvReason.setTextColor(0xFF64748B);
            tvReason.setPadding(0, 0, 0, (int) (10 * density));
            card.addView(tvReason);

            // Row 4: Create Transfer Button (Prefills transfer form)
            Button btnCreate = new Button(this);
            btnCreate.setText("Create Transfer (Prefilled)");
            btnCreate.setTextSize(11f);
            btnCreate.setTextColor(0xFFFFFFFF);
            btnCreate.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2563EB));
            btnCreate.setPadding((int) (14 * density), 0, (int) (14 * density), 0);
            LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (32 * density));
            btnCreate.setLayoutParams(lpBtn);

            btnCreate.setOnClickListener(v -> {
                Intent intent = new Intent(this, StockTransfersActivity.class);
                intent.putExtra("user_id", userId);
                intent.putExtra("role", role);
                intent.putExtra("store_id", storeId);
                intent.putExtra(StockTransfersActivity.EXTRA_PREFILL_SOURCE_STORE_ID, srcId);
                intent.putExtra(StockTransfersActivity.EXTRA_PREFILL_DEST_STORE_ID, destId);
                intent.putExtra(StockTransfersActivity.EXTRA_PREFILL_PRODUCT_ID, srcProdId);
                intent.putExtra(StockTransfersActivity.EXTRA_PREFILL_QTY, recQty);
                startActivity(intent);
            });

            card.addView(btnCreate);

            llTransferOpportunities.addView(card);
        }
    }

    private void renderStockoutRisks(JSONArray arr) {
        if (llStockoutRisks == null) return;
        llStockoutRisks.removeAllViews();

        float density = getResources().getDisplayMetrics().density;

        if (arr == null || arr.length() == 0) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No immediate stockout risks identified.");
            emptyTv.setTextColor(0xFF64748B);
            emptyTv.setTextSize(12f);
            llStockoutRisks.addView(emptyTv);
            return;
        }

        for (int i = 0; i < Math.min(5, arr.length()); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;

            String name = item.optString("product_name", "Product");
            String store = item.optString("store_name", "Store");
            int stock = item.optInt("stock", 0);
            double days = item.optDouble("days_of_stock", 0.0);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

            LinearLayout colLeft = new LinearLayout(this);
            colLeft.setOrientation(LinearLayout.VERTICAL);
            colLeft.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvName = new TextView(this);
            tvName.setText(name);
            tvName.setTextSize(12f);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setTextColor(0xFF0F172A);
            colLeft.addView(tvName);

            TextView tvStore = new TextView(this);
            tvStore.setText(store + " • Stock: " + stock);
            tvStore.setTextSize(11f);
            tvStore.setTextColor(0xFF64748B);
            colLeft.addView(tvStore);

            row.addView(colLeft);

            TextView tvCoverage = new TextView(this);
            tvCoverage.setText(days <= 0 ? "Stocked out" : String.format(Locale.US, "%.1fd coverage", days));
            tvCoverage.setTextSize(11f);
            tvCoverage.setTypeface(null, Typeface.BOLD);
            tvCoverage.setTextColor(0xFFDC2626);
            tvCoverage.setGravity(Gravity.END);
            row.addView(tvCoverage);

            llStockoutRisks.addView(row);
        }
    }

    private void renderFastMoving(JSONArray arr) {
        if (llFastMoving == null) return;
        llFastMoving.removeAllViews();

        float density = getResources().getDisplayMetrics().density;

        if (arr == null || arr.length() == 0) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No items currently classified as fast moving.");
            emptyTv.setTextColor(0xFF64748B);
            emptyTv.setTextSize(12f);
            llFastMoving.addView(emptyTv);
            return;
        }

        for (int i = 0; i < Math.min(5, arr.length()); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;

            String name = item.optString("product_name", "Product");
            String store = item.optString("store_name", "Store");
            int sold = item.optInt("units_sold", 0);
            double velocity = item.optDouble("velocity_per_day", 0.0);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

            LinearLayout colLeft = new LinearLayout(this);
            colLeft.setOrientation(LinearLayout.VERTICAL);
            colLeft.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvName = new TextView(this);
            tvName.setText(name);
            tvName.setTextSize(12f);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setTextColor(0xFF0F172A);
            colLeft.addView(tvName);

            TextView tvStore = new TextView(this);
            tvStore.setText(store);
            tvStore.setTextSize(11f);
            tvStore.setTextColor(0xFF64748B);
            colLeft.addView(tvStore);

            row.addView(colLeft);

            TextView tvUnits = new TextView(this);
            tvUnits.setText(sold + " sold (" + velocity + "/day)");
            tvUnits.setTextSize(11f);
            tvUnits.setTypeface(null, Typeface.BOLD);
            tvUnits.setTextColor(0xFF059669);
            tvUnits.setGravity(Gravity.END);
            row.addView(tvUnits);

            llFastMoving.addView(row);
        }
    }

    private void renderStoreComparison(JSONArray arr) {
        if (!"super_admin".equals(role)) {
            if (cardStoreComparison != null) {
                cardStoreComparison.setVisibility(View.GONE);
            }
            return;
        }

        if (llStoreComparisonRows == null) return;
        llStoreComparisonRows.removeAllViews();

        if (arr == null || arr.length() == 0) {
            if (cardStoreComparison != null) {
                cardStoreComparison.setVisibility(View.GONE);
            }
            return;
        }

        if (cardStoreComparison != null) {
            cardStoreComparison.setVisibility(View.VISIBLE);
        }

        float density = getResources().getDisplayMetrics().density;
        int padH = (int) (12 * density);
        int padV = (int) (10 * density);

        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s == null) continue;

            String name = s.optString("store_name", "Store");
            double sales = s.optDouble("sales", 0.0);
            int orders = s.optInt("transactions", 0);
            double avg = s.optDouble("avg_order", 0.0);
            int units = s.optInt("units_sold", 0);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(padH, padV, padH, padV);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(0xFFFFFFFF);

            // Store Name (1.8)
            TextView tvName = new TextView(this);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.8f));
            tvName.setText(name);
            tvName.setTextSize(12f);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setTextColor(0xFF0F172A);
            row.addView(tvName);

            // Sales (1.2)
            TextView tvSales = new TextView(this);
            tvSales.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
            tvSales.setText(String.format(Locale.US, "₱%.2f", sales));
            tvSales.setTextSize(12f);
            tvSales.setTypeface(null, Typeface.BOLD);
            tvSales.setTextColor(0xFF0F172A);
            tvSales.setGravity(Gravity.END);
            row.addView(tvSales);

            // Orders (0.8)
            TextView tvOrders = new TextView(this);
            tvOrders.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f));
            tvOrders.setText(String.valueOf(orders));
            tvOrders.setTextSize(12f);
            tvOrders.setTextColor(0xFF64748B);
            tvOrders.setGravity(Gravity.END);
            row.addView(tvOrders);

            // Avg Order (1.0)
            TextView tvAvg = new TextView(this);
            tvAvg.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            tvAvg.setText(String.format(Locale.US, "₱%.2f", avg));
            tvAvg.setTextSize(12f);
            tvAvg.setTextColor(0xFF64748B);
            tvAvg.setGravity(Gravity.END);
            row.addView(tvAvg);

            // Units (0.8)
            TextView tvUnits = new TextView(this);
            tvUnits.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f));
            tvUnits.setText(String.valueOf(units));
            tvUnits.setTextSize(12f);
            tvUnits.setTextColor(0xFF64748B);
            tvUnits.setGravity(Gravity.END);
            row.addView(tvUnits);

            llStoreComparisonRows.addView(row);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFE2E8F0);
            llStoreComparisonRows.addView(divider);
        }
    }

    private void showStatusMessage(String message) {
        if (tvInsightsLoadingStatus != null) {
            tvInsightsLoadingStatus.setVisibility(View.VISIBLE);
            tvInsightsLoadingStatus.setText(message);
        }
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
