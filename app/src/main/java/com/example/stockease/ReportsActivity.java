package com.example.stockease;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ReportsActivity extends AppCompatActivity {

    private int storeId = -1;
    private int userId = -1;
    private String role = "";
    private String fullName = "";
    private String storeName = "";

    private static final String REPORTS_URL = ApiConfig.getUrl("get_store_reports.php");
    private static final String MANUAL_SALES_URL = ApiConfig.getUrl("manage_manual_sales.php");
    private static final String PRODUCTS_URL = ApiConfig.getUrl("get_products.php");

    private TextView tvReportsTitle, tvReportsSubtitle, tvActiveDateRange;
    private TextView tvReportTotalSales, tvReportTotalTransactions, tvReportAvgTransaction, tvReportProductsSold;
    private LinearLayout llPaymentMethodsList, llTopProductsList, llDailySalesList;
    private Button btnFilterToday, btnFilterThisWeek, btnFilterThisMonth, btnFilterCustom;

    // Other Sales (manual, non-POS) section
    private TextView tvOtherSalesTotal, tvOtherSalesCount, tvVoidedTotal, tvVoidedCount;
    private LinearLayout llOtherSalesEntries;
    private Button btnRecordManualSale;
    private JSONArray cachedOtherSales = new JSONArray();

    private String startDateFilter = null;
    private String endDateFilter = null;
    private String activeFilterLabel = "Today";

    // Cached report tables so they can be exported as a CSV spreadsheet
    private JSONObject cachedSummary = null;
    private JSONArray cachedPaymentMethods = new JSONArray();
    private JSONArray cachedTopProducts = new JSONArray();
    private JSONArray cachedDailySales = new JSONArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);
        SidebarNav.attach(this, "reports");

        if (getIntent() != null) {
            storeId = getIntent().getIntExtra("store_id", -1);
            userId = getIntent().getIntExtra("user_id", -1);
            role = getIntent().getStringExtra("role");
            fullName = getIntent().getStringExtra("full_name");
            storeName = getIntent().getStringExtra("selected_store_name");
        }

        if (userId <= 0 || (!"store_admin".equals(role) && !"super_admin".equals(role) && !"manager".equals(role) && !"store_owner".equals(role))) {
            returnToLogin("Reports are available to Store Admin, Manager, and Super Admin accounts.");
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

        tvOtherSalesTotal = findViewById(R.id.tvOtherSalesTotal);
        tvOtherSalesCount = findViewById(R.id.tvOtherSalesCount);
        tvVoidedTotal = findViewById(R.id.tvVoidedTotal);
        tvVoidedCount = findViewById(R.id.tvVoidedCount);
        llOtherSalesEntries = findViewById(R.id.llOtherSalesEntries);
        btnRecordManualSale = findViewById(R.id.btnRecordManualSale);

        // Recording a manual sale mirrors the API's manager-level permission
        if (btnRecordManualSale != null) {
            if ("store_admin".equals(role) || "manager".equals(role) || "super_admin".equals(role)) {
                btnRecordManualSale.setVisibility(View.VISIBLE);
                btnRecordManualSale.setOnClickListener(v -> showManualSaleDialog());
            } else {
                btnRecordManualSale.setVisibility(View.GONE);
            }
        }

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

        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));
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

        Button btnExportReportsCsv = findViewById(R.id.btnExportReportsCsv);
        if (btnExportReportsCsv != null) {
            btnExportReportsCsv.setOnClickListener(v -> exportReportsToCsv());
        }
    }

    private void highlightFilterButton(Button activeButton) {
        btnFilterToday.setBackgroundColor(ContextCompat.getColor(this, R.color.slate_600));
        btnFilterThisWeek.setBackgroundColor(ContextCompat.getColor(this, R.color.slate_600));
        btnFilterThisMonth.setBackgroundColor(ContextCompat.getColor(this, R.color.slate_600));
        btnFilterCustom.setBackgroundColor(ContextCompat.getColor(this, R.color.slate_600));

        activeButton.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));
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
                            cachedSummary = summary;
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
                            cachedPaymentMethods = paymentMethods != null ? paymentMethods : new JSONArray();
                            renderPaymentMethods(paymentMethods);

                            // 3. Top Products
                            JSONArray topProducts = response.optJSONArray("top_products");
                            cachedTopProducts = topProducts != null ? topProducts : new JSONArray();
                            renderTopProducts(topProducts);

                            // 4. Daily Sales
                            JSONArray dailySales = response.optJSONArray("daily_sales");
                            cachedDailySales = dailySales != null ? dailySales : new JSONArray();
                            renderDailySales(dailySales);

                            // 5. Other Sales (manual, non-POS + voided/refunded)
                            JSONObject otherSales = response.optJSONObject("other_sales");
                            cachedOtherSales = otherSales != null ? otherSales.optJSONArray("entries") : null;
                            renderOtherSales(otherSales);

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
            tvType.setTypeface(tvType.getTypeface(), Typeface.BOLD);
            tvType.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

            TextView tvCount = new TextView(this);
            tvCount.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvCount.setText(count + " tx");
            tvCount.setTextSize(11f);
            tvCount.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvCount.setGravity(Gravity.END);

            TextView tvAmt = new TextView(this);
            tvAmt.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
            tvAmt.setText(String.format(Locale.US, "₱%.2f", amount));
            tvAmt.setTextSize(13f);
            tvAmt.setTypeface(tvAmt.getTypeface(), Typeface.BOLD);
            tvAmt.setTextColor(ContextCompat.getColor(this, R.color.profit));
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
            tvRankName.setTypeface(tvRankName.getTypeface(), Typeface.BOLD);
            tvRankName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

            TextView tvSold = new TextView(this);
            tvSold.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvSold.setText(totalSold + " sold");
            tvSold.setTextSize(12f);
            tvSold.setTextColor(ContextCompat.getColor(this, R.color.primary));
            tvSold.setTypeface(tvSold.getTypeface(), Typeface.BOLD);
            tvSold.setGravity(Gravity.END);

            TextView tvRev = new TextView(this);
            tvRev.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
            tvRev.setText(String.format(Locale.US, "₱%.2f", revenue));
            tvRev.setTextSize(12f);
            tvRev.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
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
            tvDate.setTypeface(tvDate.getTypeface(), Typeface.BOLD);
            tvDate.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

            TextView tvSales = new TextView(this);
            tvSales.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvSales.setText(String.format(Locale.US, "₱%.2f", sales));
            tvSales.setTextSize(12f);
            tvSales.setTypeface(tvSales.getTypeface(), Typeface.BOLD);
            tvSales.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvSales.setGravity(Gravity.END);

            TextView tvCount = new TextView(this);
            tvCount.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvCount.setText(count + " tx");
            tvCount.setTextSize(12f);
            tvCount.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvCount.setGravity(Gravity.END);

            row.addView(tvDate);
            row.addView(tvSales);
            row.addView(tvCount);

            llDailySalesList.addView(row);

            // Divider
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.card_border));
            llDailySalesList.addView(divider);
        }
    }

    private TextView createEmptyTextView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
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

    // ---- Other Sales: manual (non-POS) sales + voided/refunded transactions ----

    private void renderOtherSales(JSONObject other) {
        if (other == null) {
            other = new JSONObject();
        }
        JSONArray entries = other.optJSONArray("entries");
        cachedOtherSales = entries != null ? entries : new JSONArray();

        if (tvOtherSalesTotal != null) {
            tvOtherSalesTotal.setText(String.format(Locale.US, "₱%.2f", other.optDouble("manual_sales_total", 0.0)));
        }
        if (tvOtherSalesCount != null) {
            tvOtherSalesCount.setText(other.optInt("manual_sales_count", 0) + " manual transaction(s)");
        }
        if (tvVoidedTotal != null) {
            tvVoidedTotal.setText(String.format(Locale.US, "₱%.2f", other.optDouble("voided_total", 0.0)));
        }
        if (tvVoidedCount != null) {
            tvVoidedCount.setText(other.optInt("voided_count", 0) + " voided / refunded transaction(s)");
        }
        if (llOtherSalesEntries == null) return;

        llOtherSalesEntries.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        if (cachedOtherSales.length() == 0) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("No manual sales or voided transactions in this period.");
            tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            tvEmpty.setTextSize(12f);
            llOtherSalesEntries.addView(tvEmpty);
            return;
        }

        for (int i = 0; i < cachedOtherSales.length(); i++) {
            JSONObject e = cachedOtherSales.optJSONObject(i);
            if (e == null) continue;

            boolean isVoided = "voided".equalsIgnoreCase(e.optString("status", ""));
            String when = e.optString("created_at", "");
            if (when.length() >= 16) {
                when = when.substring(5, 10).replace('-', '/') + " " + when.substring(11, 16);
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, (int) (6 * density), 0, (int) (6 * density));

            TextView tvHead = new TextView(this);
            tvHead.setText((isVoided ? "✖ Voided" : "● Completed")
                    + "  •  " + e.optString("payment_type", "—")
                    + "  •  ₱" + String.format(Locale.US, "%.2f", e.optDouble("total_amount", 0.0))
                    + "  •  " + when);
            tvHead.setTextSize(12f);
            tvHead.setTypeface(tvHead.getTypeface(), Typeface.BOLD);
            tvHead.setTextColor(ContextCompat.getColor(this,
                    isVoided ? R.color.danger_strong : R.color.text_primary));
            row.addView(tvHead);

            TextView tvDetail = new TextView(this);
            String items = e.optString("items_summary", "").replace("\n", "\n    ");
            tvDetail.setText("    " + (items.isEmpty() ? "—" : items)
                    + "\n    Recorded by " + e.optString("recorded_by", "—"));
            tvDetail.setTextSize(11f);
            tvDetail.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            row.addView(tvDetail);

            llOtherSalesEntries.addView(row);
        }
    }

    /** Dialog to record a sale made outside the POS (phone, bulk, delivery). */
    private void showManualSaleDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        float density = getResources().getDisplayMetrics().density;
        layout.setPadding((int) (24 * density), 0, (int) (24 * density), 0);

        Spinner spProduct = new Spinner(this);
        spProduct.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Loading products..."}));
        layout.addView(spProduct);

        final EditText etQty = new EditText(this);
        etQty.setHint("Quantity");
        etQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(etQty);

        final EditText etPrice = new EditText(this);
        etPrice.setHint("Unit price (defaults to shelf price)");
        etPrice.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(etPrice);

        Spinner spPayment = new Spinner(this);
        spPayment.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Cash", "GCash", "Bank Transfer", "Other"}));
        layout.addView(spPayment);

        final EditText etNotes = new EditText(this);
        etNotes.setHint("Notes (optional)");
        layout.addView(etNotes);

        final int[] productId = {-1};
        final double[] shelfPrice = {0.0};

        // Load the branch catalog into the picker
        RequestQueue queue = ApiSession.getRequestQueue(this);
        queue.add(new JsonObjectRequest(Request.Method.GET, PRODUCTS_URL + "?store_id=" + storeId, null,
                response -> {
                    JSONArray arr = response.optJSONArray("products");
                    List<String> names = new ArrayList<>();
                    List<Integer> ids = new ArrayList<>();
                    List<Double> prices = new ArrayList<>();
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject p = arr.optJSONObject(i);
                            if (p == null) continue;
                            ids.add(p.optInt("id", 0));
                            prices.add(p.optDouble("price", 0.0));
                            names.add(p.optString("product_name", "Product")
                                    + " (stock: " + p.optInt("stock", 0) + ")");
                        }
                    }
                    final List<Integer> finalIds = ids;
                    final List<Double> finalPrices = prices;
                    spProduct.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
                    spProduct.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                            if (position >= 0 && position < finalIds.size()) {
                                productId[0] = finalIds.get(position);
                                shelfPrice[0] = finalPrices.get(position);
                                if (etPrice.getText().toString().trim().isEmpty()) {
                                    etPrice.setText(String.format(Locale.US, "%.2f", shelfPrice[0]));
                                }
                            }
                        }

                        @Override
                        public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                    });
                },
                error -> Toast.makeText(this, "Could not load products", Toast.LENGTH_SHORT).show()));

        new AlertDialog.Builder(this)
                .setTitle("Record Manual Sale")
                .setView(layout)
                .setPositiveButton("Record", (dialog, which) -> {
                    String qtyStr = etQty.getText() != null ? etQty.getText().toString().trim() : "";
                    String priceStr = etPrice.getText() != null ? etPrice.getText().toString().trim() : "";
                    if (productId[0] <= 0 || TextUtils.isEmpty(qtyStr)) {
                        Toast.makeText(this, "Select a product and enter the quantity", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int qty;
                    try {
                        qty = Integer.parseInt(qtyStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid quantity", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    double price = TextUtils.isEmpty(priceStr) ? shelfPrice[0] : Double.parseDouble(priceStr);
                    recordManualSale(productId[0], qty, price,
                            spPayment.getSelectedItem().toString(),
                            etNotes.getText() != null ? etNotes.getText().toString().trim() : "");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void recordManualSale(int productId, int quantity, double unitPrice, String paymentType, String notes) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("store_id", storeId);
            payload.put("payment_type", paymentType);
            payload.put("notes", notes);
            JSONArray items = new JSONArray();
            JSONObject line = new JSONObject();
            line.put("product_id", productId);
            line.put("quantity", quantity);
            line.put("unit_price", unitPrice);
            items.put(line);
            payload.put("items", items);
        } catch (JSONException ignored) {}

        RequestQueue queue = ApiSession.getRequestQueue(this);
        queue.add(new JsonObjectRequest(Request.Method.POST,
                MANUAL_SALES_URL + "?action=create", payload,
                response -> {
                    // opt* accessors never throw, so no parse try/catch is needed
                    if (response.optBoolean("success", false)) {
                        Toast.makeText(this, response.optString("message", "Manual sale recorded"),
                                Toast.LENGTH_LONG).show();
                        loadReportsData();
                    } else {
                        Toast.makeText(this, response.optString("message", "Could not record the sale"),
                                Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Network error recording the manual sale", Toast.LENGTH_SHORT).show();
                }));
    }

    /** Exports the loaded summary + tables as a single CSV spreadsheet. */
    private void exportReportsToCsv() {
        List<String[]> rows = new ArrayList<>();

        rows.add(new String[]{"StockEase Store Report", storeName, "Period", activeFilterLabel});
        rows.add(new String[]{});

        rows.add(new String[]{"Summary"});
        rows.add(new String[]{"Metric", "Value"});
        if (cachedSummary != null) {
            rows.add(new String[]{"Total Sales", String.valueOf(cachedSummary.optDouble("total_sales", 0.0))});
            rows.add(new String[]{"Transactions", String.valueOf(cachedSummary.optInt("transaction_count", 0))});
            rows.add(new String[]{"Average Transaction", String.valueOf(cachedSummary.optDouble("average_transaction", 0.0))});
            rows.add(new String[]{"Products Sold", String.valueOf(cachedSummary.optInt("products_sold", 0))});
        }
        rows.add(new String[]{});

        rows.add(new String[]{"Payment Methods"});
        rows.add(new String[]{"Payment Type", "Transactions", "Total Amount"});
        for (int i = 0; i < cachedPaymentMethods.length(); i++) {
            JSONObject obj = cachedPaymentMethods.optJSONObject(i);
            if (obj == null) continue;
            rows.add(new String[]{
                    obj.optString("payment_type", "Unknown"),
                    String.valueOf(obj.optInt("count", 0)),
                    String.valueOf(obj.optDouble("total_amount", 0.0))
            });
        }
        rows.add(new String[]{});

        rows.add(new String[]{"Top Products"});
        rows.add(new String[]{"Rank", "Product", "Units Sold", "Revenue"});
        for (int i = 0; i < cachedTopProducts.length(); i++) {
            JSONObject obj = cachedTopProducts.optJSONObject(i);
            if (obj == null) continue;
            rows.add(new String[]{
                    String.valueOf(i + 1),
                    obj.optString("product_name", "Product"),
                    String.valueOf(obj.optInt("total_sold", 0)),
                    String.valueOf(obj.optDouble("total_revenue", 0.0))
            });
        }
        rows.add(new String[]{});

        rows.add(new String[]{"Daily Sales"});
        rows.add(new String[]{"Date", "Sales", "Transactions"});
        for (int i = 0; i < cachedDailySales.length(); i++) {
            JSONObject obj = cachedDailySales.optJSONObject(i);
            if (obj == null) continue;
            rows.add(new String[]{
                    obj.optString("sale_date", ""),
                    String.valueOf(obj.optDouble("daily_sales", 0.0)),
                    String.valueOf(obj.optInt("transaction_count", 0))
            });
        }

        String fileName = "stockease_report_" + (storeName != null && !storeName.isEmpty()
                ? storeName.replaceAll("\\s+", "_") : "store_" + storeId) + "_" + activeFilterLabel.replaceAll("\\s+", "_");
        ExportUtil.saveCsv(this, fileName, ExportUtil.rowsToCsv(rows));
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ScreenTransitions.open(ReportsActivity.this, intent);
        ScreenTransitions.finish(ReportsActivity.this);
    }
}
