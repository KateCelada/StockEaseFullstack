package com.example.stockease;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class ReportsActivity extends AppCompatActivity {

    private int storeId = -1;
    private int userId = -1;
    private String role = "";
    private String fullName = "";
    private String storeName = "";

    private static final String REPORTS_URL = ApiConfig.getUrl("get_store_reports.php");

    private TextView tvReportsTitle, tvReportsSubtitle, tvActiveDateRange;
    private TextView tvReportTotalSales, tvReportTotalTransactions, tvReportAvgTransaction, tvReportProductsSold;
    private LinearLayout llPaymentMethodsList, llTopProductsList, llDailySalesList;
    private Button btnFilterToday, btnFilterThisWeek, btnFilterThisMonth, btnFilterCustom;

    private String startDateFilter = null;
    private String endDateFilter = null;
    private String activeFilterLabel = "Today";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        if (getIntent() != null) {
            storeId = getIntent().getIntExtra("store_id", -1);
            userId = getIntent().getIntExtra("user_id", -1);
            role = getIntent().getStringExtra("role");
            fullName = getIntent().getStringExtra("full_name");
            storeName = getIntent().getStringExtra("selected_store_name");
        }

        if (userId <= 0 || (!"store_admin".equals(role) && !"super_admin".equals(role))) {
            returnToLogin("Reports are available only to Store Admin and Super Admin accounts.");
            return;
        }

        // Bind Views
        tvReportsTitle = findViewById(R.id.tvReportsTitle);
        tvReportsSubtitle = findViewById(R.id.tvReportsSubtitle);
        tvActiveDateRange = findViewById(R.id.tvActiveDateRange);

        tvReportTotalSales = findViewById(R.id.tvReportTotalSales);
        tvReportTotalTransactions = findViewById(R.id.tvReportTotalTransactions);
        tvReportAvgTransaction = findViewById(R.id.tvReportAvgTransaction);
        tvReportProductsSold = findViewById(R.id.tvReportProductsSold);

        llPaymentMethodsList = findViewById(R.id.llPaymentMethodsList);
        llTopProductsList = findViewById(R.id.llTopProductsList);
        llDailySalesList = findViewById(R.id.llDailySalesList);

        btnFilterToday = findViewById(R.id.btnFilterToday);
        btnFilterThisWeek = findViewById(R.id.btnFilterThisWeek);
        btnFilterThisMonth = findViewById(R.id.btnFilterThisMonth);
        btnFilterCustom = findViewById(R.id.btnFilterCustom);

        Button btnReportsBack = findViewById(R.id.btnReportsBack);
        if (btnReportsBack != null) {
            btnReportsBack.setOnClickListener(v -> finish());
        }

        if (storeName != null && !storeName.isEmpty()) {
            tvReportsSubtitle.setText(storeName + " — Performance Analytics");
        }

        setupFilterListeners();

        // Default to Today's report
        filterToday();
    }

    private void setupFilterListeners() {
        btnFilterToday.setOnClickListener(v -> {
            filterToday();
            highlightFilterButton(btnFilterToday);
        });

        btnFilterThisWeek.setOnClickListener(v -> {
            filterThisWeek();
            highlightFilterButton(btnFilterThisWeek);
        });

        btnFilterThisMonth.setOnClickListener(v -> {
            filterThisMonth();
            highlightFilterButton(btnFilterThisMonth);
        });

        btnFilterCustom.setOnClickListener(v -> showCustomDateRangePicker());
    }

    private void highlightFilterButton(Button activeButton) {
        btnFilterToday.setBackgroundColor(0xFF555555);
        btnFilterThisWeek.setBackgroundColor(0xFF555555);
        btnFilterThisMonth.setBackgroundColor(0xFF555555);
        btnFilterCustom.setBackgroundColor(0xFF555555);

        activeButton.setBackgroundColor(0xFF7C4DFF);
    }

    private void filterToday() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String today = sdf.format(Calendar.getInstance().getTime());
        startDateFilter = today;
        endDateFilter = today;
        activeFilterLabel = "Today (" + today + ")";
        loadReportsData();
    }

    private void filterThisWeek() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String end = sdf.format(cal.getTime());

        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        String start = sdf.format(cal.getTime());

        startDateFilter = start;
        endDateFilter = end;
        activeFilterLabel = "This Week (" + start + " to " + end + ")";
        loadReportsData();
    }

    private void filterThisMonth() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String end = sdf.format(cal.getTime());

        cal.set(Calendar.DAY_OF_MONTH, 1);
        String start = sdf.format(cal.getTime());

        startDateFilter = start;
        endDateFilter = end;
        activeFilterLabel = "This Month (" + start + " to " + end + ")";
        loadReportsData();
    }

    private void showCustomDateRangePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog startDialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String start = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);

            DatePickerDialog endDialog = new DatePickerDialog(this, (view1, yearEnd, monthEnd, dayOfMonthEnd) -> {
                String end = String.format(Locale.US, "%04d-%02d-%02d", yearEnd, monthEnd + 1, dayOfMonthEnd);
                startDateFilter = start;
                endDateFilter = end;
                activeFilterLabel = "Custom (" + start + " to " + end + ")";
                highlightFilterButton(btnFilterCustom);
                loadReportsData();
            }, year, month, dayOfMonth);

            endDialog.setTitle("Select End Date");
            endDialog.show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));

        startDialog.setTitle("Select Start Date");
        startDialog.show();
    }

    private void loadReportsData() {
        if (tvActiveDateRange != null) {
            tvActiveDateRange.setText("Period: " + activeFilterLabel);
        }

        String url = REPORTS_URL + "?store_id=" + storeId;
        if (startDateFilter != null && endDateFilter != null) {
            url += "&start_date=" + startDateFilter + "&end_date=" + endDateFilter;
        }

        setPlaceholders();
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            String fetchedStoreName = response.optString("store_name", "");
                            if (!fetchedStoreName.isEmpty() && tvReportsSubtitle != null) {
                                tvReportsSubtitle.setText(fetchedStoreName + " — Performance Analytics");
                            }

                            // 1. Summary
                            JSONObject summary = response.optJSONObject("summary");
                            if (summary != null) {
                                double totalSales = summary.optDouble("total_sales", 0.0);
                                int txCount = summary.optInt("transaction_count", 0);
                                double avgTx = summary.optDouble("average_transaction", 0.0);
                                int productsSold = summary.optInt("products_sold", 0);

                                tvReportTotalSales.setText(String.format(Locale.US, "₱%.2f", totalSales));
                                tvReportTotalTransactions.setText(String.valueOf(txCount));
                                tvReportAvgTransaction.setText(String.format(Locale.US, "₱%.2f", avgTx));
                                tvReportProductsSold.setText(String.valueOf(productsSold));
                            }

                            // 2. Payment Methods
                            JSONArray paymentMethods = response.optJSONArray("payment_methods");
                            renderPaymentMethods(paymentMethods);

                            // 3. Top Products
                            JSONArray topProducts = response.optJSONArray("top_products");
                            renderTopProducts(topProducts);

                            // 4. Daily Sales
                            JSONArray dailySales = response.optJSONArray("daily_sales");
                            renderDailySales(dailySales);

                        } else {
                            Toast.makeText(this, response.optString("message", "Unable to load reports"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(this, "Error parsing report response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to load store reports", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    private void renderPaymentMethods(JSONArray methods) {
        if (llPaymentMethodsList == null) return;
        llPaymentMethodsList.removeAllViews();

        if (methods == null || methods.length() == 0) {
            TextView empty = createEmptyTextView("No sales in this period");
            llPaymentMethodsList.addView(empty);
            return;
        }

        for (int i = 0; i < methods.length(); i++) {
            JSONObject obj = methods.optJSONObject(i);
            if (obj == null) continue;

            String type = obj.optString("payment_type", "Unknown");
            double amount = obj.optDouble("total_amount", 0.0);
            int count = obj.optInt("count", 0);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 10, 0, 10);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvType = new TextView(this);
            tvType.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f));
            tvType.setText(type);
            tvType.setTextSize(13f);
            tvType.setTypeface(null, Typeface.BOLD);
            tvType.setTextColor(0xFF1B1C30);

            TextView tvCount = new TextView(this);
            tvCount.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvCount.setText(count + " tx");
            tvCount.setTextSize(11f);
            tvCount.setTextColor(0xFF6E7191);
            tvCount.setGravity(Gravity.END);

            TextView tvAmt = new TextView(this);
            tvAmt.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
            tvAmt.setText(String.format(Locale.US, "₱%.2f", amount));
            tvAmt.setTextSize(13f);
            tvAmt.setTypeface(null, Typeface.BOLD);
            tvAmt.setTextColor(0xFF2E7D32);
            tvAmt.setGravity(Gravity.END);

            row.addView(tvType);
            row.addView(tvCount);
            row.addView(tvAmt);

            llPaymentMethodsList.addView(row);
        }
    }

    private void renderTopProducts(JSONArray products) {
        if (llTopProductsList == null) return;
        llTopProductsList.removeAllViews();

        if (products == null || products.length() == 0) {
            TextView empty = createEmptyTextView("No products sold in this period");
            llTopProductsList.addView(empty);
            return;
        }

        for (int i = 0; i < products.length(); i++) {
            JSONObject obj = products.optJSONObject(i);
            if (obj == null) continue;

            String name = obj.optString("product_name", "Product");
            int totalSold = obj.optInt("total_sold", 0);
            double revenue = obj.optDouble("total_revenue", 0.0);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 10, 0, 10);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvRankName = new TextView(this);
            tvRankName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
            tvRankName.setText((i + 1) + ". " + name);
            tvRankName.setTextSize(13f);
            tvRankName.setTypeface(null, Typeface.BOLD);
            tvRankName.setTextColor(0xFF1B1C30);

            TextView tvSold = new TextView(this);
            tvSold.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvSold.setText(totalSold + " sold");
            tvSold.setTextSize(12f);
            tvSold.setTextColor(0xFF7C4DFF);
            tvSold.setTypeface(null, Typeface.BOLD);
            tvSold.setGravity(Gravity.END);

            TextView tvRev = new TextView(this);
            tvRev.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
            tvRev.setText(String.format(Locale.US, "₱%.2f", revenue));
            tvRev.setTextSize(12f);
            tvRev.setTextColor(0xFF6E7191);
            tvRev.setGravity(Gravity.END);

            row.addView(tvRankName);
            row.addView(tvSold);
            row.addView(tvRev);

            llTopProductsList.addView(row);
        }
    }

    private void renderDailySales(JSONArray days) {
        if (llDailySalesList == null) return;
        llDailySalesList.removeAllViews();

        if (days == null || days.length() == 0) {
            TextView empty = createEmptyTextView("No transaction history recorded yet");
            llDailySalesList.addView(empty);
            return;
        }

        for (int i = 0; i < days.length(); i++) {
            JSONObject obj = days.optJSONObject(i);
            if (obj == null) continue;

            String date = obj.optString("sale_date", "");
            double sales = obj.optDouble("daily_sales", 0.0);
            int count = obj.optInt("transaction_count", 0);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(14, 12, 14, 12);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView tvDate = new TextView(this);
            tvDate.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f));
            tvDate.setText(date);
            tvDate.setTextSize(12f);
            tvDate.setTypeface(null, Typeface.BOLD);
            tvDate.setTextColor(0xFF1B1C30);

            TextView tvSales = new TextView(this);
            tvSales.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvSales.setText(String.format(Locale.US, "₱%.2f", sales));
            tvSales.setTextSize(12f);
            tvSales.setTypeface(null, Typeface.BOLD);
            tvSales.setTextColor(0xFF1B1C30);
            tvSales.setGravity(Gravity.END);

            TextView tvCount = new TextView(this);
            tvCount.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvCount.setText(count + " tx");
            tvCount.setTextSize(12f);
            tvCount.setTextColor(0xFF6E7191);
            tvCount.setGravity(Gravity.END);

            row.addView(tvDate);
            row.addView(tvSales);
            row.addView(tvCount);

            llDailySalesList.addView(row);

            // Divider
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFEFEFF6);
            llDailySalesList.addView(divider);
        }
    }

    private TextView createEmptyTextView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF6E7191);
        tv.setTextSize(12f);
        tv.setPadding(0, 16, 0, 16);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private void setPlaceholders() {
        tvReportTotalSales.setText("—");
        tvReportTotalTransactions.setText("—");
        tvReportAvgTransaction.setText("—");
        tvReportProductsSold.setText("—");
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
