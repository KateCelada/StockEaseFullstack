package com.example.stockease;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SuperAdminDashboardActivity extends AppCompatActivity {

    private TextView tvSuperHeaderDate, tvSuperHeaderUserPill;
    private TextView tvSuperAvatarInitials, tvSuperProfileName;
    private TextView tvSuperTotalStores, tvSuperActiveStoresBadge, tvSuperTotalUsers, tvSuperTotalSales, tvSuperTotalTransactions;
    private TextView tvOverviewTotalSales, tvOverviewTransactions, tvOverviewAvgOrder;
    private Button btnSuperCompareStores;
    private LinearLayout llStorePerformanceRows, llStoreOwnersCards;
    private View navSuperDashboard, navSuperStores, navSuperAdmins, navSuperUsers, navSuperReports, navSuperAuditLogs, navSuperInsights, navSuperSubscription, navSuperSettings, navSuperLogout;

    private int userId = -1;
    private String role = "";
    private String fullName = "";
    private String username = "";
    private String email = "";

    private final List<StorePerformance> currentPerformanceList = new ArrayList<>();
    private final Map<Integer, Integer> storeStaffCountMap = new HashMap<>();

    private static final String STORES_URL = ApiConfig.getUrl("manage_stores.php?action=fetch");
    private static final String USERS_URL = ApiConfig.getUrl("get_users.php");
    private static final String TRANSACTIONS_URL = ApiConfig.getUrl("get_transactions.php");

    private static class StoreInfo {
        final int id;
        final String name;
        final String location;

        StoreInfo(int id, String name, String location) {
            this.id = id;
            this.name = name;
            this.location = location;
        }
    }

    private static class StorePerformance {
        final int storeId;
        final String storeName;
        final String location;
        double sales = 0.0;
        int transactionCount = 0;
        int staffCount = 0;

        StorePerformance(int storeId, String storeName, String location) {
            this.storeId = storeId;
            this.storeName = storeName;
            this.location = location;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_super_admin_dashboard);

        userId = getIntent().getIntExtra("user_id", -1);
        role = getIntent().getStringExtra("role");
        fullName = getIntent().getStringExtra("full_name");
        username = getIntent().getStringExtra("username");
        email = getIntent().getStringExtra("email");

        if (!isAuthorized()) {
            returnToLogin("This dashboard is available only to Super Admin accounts.");
            return;
        }

        // Header Views
        tvSuperHeaderDate = findViewById(R.id.tvSuperHeaderDate);
        tvSuperHeaderUserPill = findViewById(R.id.tvSuperHeaderUserPill);
        tvSuperAvatarInitials = findViewById(R.id.tvSuperAvatarInitials);
        tvSuperProfileName = findViewById(R.id.tvSuperProfileName);

        setupHeaderInfo();

        // Metric Bar (Stores, Users, Today's Sales, Transactions)
        tvSuperTotalStores = findViewById(R.id.tvSuperTotalStores);
        tvSuperActiveStoresBadge = findViewById(R.id.tvSuperActiveStoresBadge);
        tvSuperTotalUsers = findViewById(R.id.tvSuperTotalUsers);
        tvSuperTotalSales = findViewById(R.id.tvSuperTotalSales);
        tvSuperTotalTransactions = findViewById(R.id.tvSuperTotalTransactions);

        // Sales Overview Summary
        tvOverviewTotalSales = findViewById(R.id.tvOverviewTotalSales);
        tvOverviewTransactions = findViewById(R.id.tvOverviewTransactions);
        tvOverviewAvgOrder = findViewById(R.id.tvOverviewAvgOrder);

        // Store Owners Portfolios
        llStoreOwnersCards = findViewById(R.id.llStoreOwnersCards);

        // Store Performance Overview Views
        btnSuperCompareStores = findViewById(R.id.btnSuperCompareStores);
        llStorePerformanceRows = findViewById(R.id.llStorePerformanceRows);

        if (btnSuperCompareStores != null) {
            btnSuperCompareStores.setOnClickListener(v -> showStoreComparisonDialog());
        }

        // Categorized Sidebar Navigation Items
        navSuperDashboard = findViewById(R.id.navSuperDashboard);
        navSuperStores = findViewById(R.id.navSuperStores);
        navSuperUsers = findViewById(R.id.navSuperUsers);
        navSuperAdmins = findViewById(R.id.navSuperAdmins);
        navSuperReports = findViewById(R.id.navSuperReports);
        navSuperAuditLogs = findViewById(R.id.navSuperAuditLogs);
        navSuperInsights = findViewById(R.id.navSuperInsights);
        navSuperSubscription = findViewById(R.id.navSuperSubscription);
        navSuperSettings = findViewById(R.id.navSuperSettings);
        navSuperLogout = findViewById(R.id.navSuperLogout);

        if (navSuperStores != null) {
            navSuperStores.setOnClickListener(v -> openSuperAdminDestination(StoresActivity.class));
        }

        if (navSuperUsers != null) {
            navSuperUsers.setOnClickListener(v -> openSuperAdminDestination(UsersActivity.class));
        }

        if (navSuperAdmins != null) {
            navSuperAdmins.setOnClickListener(v -> openSuperAdminDestination(StoresActivity.class));
        }

        if (navSuperReports != null) {
            navSuperReports.setOnClickListener(v -> openStoreSelection(StoresActivity.TARGET_REPORTS));
        }

        if (navSuperAuditLogs != null) {
            navSuperAuditLogs.setOnClickListener(v -> openSuperAdminDestination(AuditLogsActivity.class));
        }

        if (navSuperInsights != null) {
            navSuperInsights.setOnClickListener(v -> openSuperAdminDestination(StockEaseInsightsActivity.class));
        }

        if (navSuperSubscription != null) {
            navSuperSubscription.setOnClickListener(v -> openSuperAdminDestination(SubscriptionActivity.class));
        }

        if (navSuperSettings != null) {
            navSuperSettings.setOnClickListener(v -> openSuperAdminDestination(SettingsActivity.class));
        }

        if (navSuperLogout != null) {
            navSuperLogout.setOnClickListener(v -> ApiSession.logout(this));
        }

        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));
    }

    private void setupHeaderInfo() {
        String displayName = (fullName != null && !fullName.isEmpty()) ? fullName : (username != null ? username : "System Admin");

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM d, yyyy", Locale.US);
        if (tvSuperHeaderDate != null) {
            tvSuperHeaderDate.setText(dateFormat.format(new Date()));
        }

        if (tvSuperHeaderUserPill != null) {
            tvSuperHeaderUserPill.setText("Super Admin");
        }

        if (tvSuperProfileName != null) {
            tvSuperProfileName.setText(displayName);
        }

        if (tvSuperAvatarInitials != null) {
            String initials = "SA";
            if (!displayName.isEmpty()) {
                String[] parts = displayName.trim().split("\\s+");
                if (parts.length >= 2) {
                    initials = ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase(Locale.US);
                } else if (displayName.length() >= 2) {
                    initials = displayName.substring(0, 2).toUpperCase(Locale.US);
                } else {
                    initials = displayName.toUpperCase(Locale.US);
                }
            }
            tvSuperAvatarInitials.setText(initials);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isAuthorized()) {
            loadDashboardMetrics();
        }
    }

    private void loadDashboardMetrics() {
        setMetricPlaceholders();
        RequestQueue queue = ApiSession.getRequestQueue(this);

        loadStoresAndSalesMetrics(queue);
        loadUserMetrics(queue);
        loadStoreOwnersCards(queue);
    }

    private void loadStoresAndSalesMetrics(RequestQueue queue) {
        JsonObjectRequest storesRequest = new JsonObjectRequest(
                Request.Method.GET, STORES_URL, null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONArray storesArray = response.getJSONArray("stores");
                            int storeCount = storesArray.length();
                            if (tvSuperTotalStores != null) {
                                tvSuperTotalStores.setText(String.valueOf(storeCount));
                            }
                            if (tvSuperActiveStoresBadge != null) {
                                tvSuperActiveStoresBadge.setText(storeCount + " active");
                            }

                            List<StoreInfo> stores = new ArrayList<>();
                            for (int i = 0; i < storesArray.length(); i++) {
                                JSONObject store = storesArray.optJSONObject(i);
                                int storeId = store == null ? -1 : store.optInt("id", -1);
                                String name = store == null ? "Store" : store.optString("store_name", "Store");
                                String location = store == null ? "" : store.optString("location", "");
                                if (storeId <= 0) {
                                    if (tvSuperTotalSales != null) tvSuperTotalSales.setText("—");
                                    Toast.makeText(this, "Store metrics contain an invalid store", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                stores.add(new StoreInfo(storeId, name, location));
                            }

                            if (stores.isEmpty()) {
                                if (tvSuperTotalSales != null) tvSuperTotalSales.setText("₱0.00");
                                if (tvSuperTotalTransactions != null) tvSuperTotalTransactions.setText("0");
                                if (tvOverviewTotalSales != null) tvOverviewTotalSales.setText("₱0.00");
                                if (tvOverviewTransactions != null) tvOverviewTransactions.setText("0");
                                if (tvOverviewAvgOrder != null) tvOverviewAvgOrder.setText("₱0.00");
                                currentPerformanceList.clear();
                                renderStorePerformanceRows(new ArrayList<>());
                                return;
                            }

                            loadStorePerformanceBreakdown(queue, stores);
                        } else {
                            if (tvSuperTotalStores != null) tvSuperTotalStores.setText("—");
                            if (tvSuperTotalSales != null) tvSuperTotalSales.setText("—");
                            Toast.makeText(this, response.optString("message", "Failed to load store metrics"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        if (tvSuperTotalStores != null) tvSuperTotalStores.setText("—");
                        if (tvSuperTotalSales != null) tvSuperTotalSales.setText("—");
                        Toast.makeText(this, "Invalid store metrics response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(SuperAdminDashboardActivity.this, error)) return;
                    if (tvSuperTotalStores != null) tvSuperTotalStores.setText("—");
                    if (tvSuperTotalSales != null) tvSuperTotalSales.setText("—");
                    Toast.makeText(SuperAdminDashboardActivity.this, "Failed to load stores", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(storesRequest);
    }

    private void loadStorePerformanceBreakdown(RequestQueue queue, List<StoreInfo> stores) {
        int[] pendingRequests = {stores.size()};
        double[] totalSales = {0.0};
        boolean[] failed = {false};
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        List<StorePerformance> performanceList = new ArrayList<>();
        for (StoreInfo s : stores) {
            performanceList.add(new StorePerformance(s.id, s.name, s.location));
        }

        for (int i = 0; i < stores.size(); i++) {
            StoreInfo store = stores.get(i);
            StorePerformance perf = performanceList.get(i);

            String transactionsUrl = TRANSACTIONS_URL
                    + "?store_id=" + store.id
                    + "&start_date=" + today
                    + "&end_date=" + today;
            JsonArrayRequest transactionsRequest = new JsonArrayRequest(
                    Request.Method.GET,
                    transactionsUrl,
                    null,
                    response -> {
                        perf.transactionCount = response.length();
                        for (int t = 0; t < response.length(); t++) {
                            JSONObject transaction = response.optJSONObject(t);
                            if (transaction != null && transaction.has("total_amount")) {
                                double amt = transaction.optDouble("total_amount", 0.0);
                                perf.sales += amt;
                                totalSales[0] += amt;
                            }
                        }
                        finishStorePerformanceRequest(pendingRequests, totalSales, performanceList, failed);
                    },
                    error -> {
                        if (ApiSession.handleAuthError(this, error)) return;
                        failed[0] = true;
                        finishStorePerformanceRequest(pendingRequests, totalSales, performanceList, failed);
                    }
            );

            queue.add(transactionsRequest);
        }
    }

    private void finishStorePerformanceRequest(
            int[] pendingRequests,
            double[] totalSales,
            List<StorePerformance> performanceList,
            boolean[] failed
    ) {
        pendingRequests[0]--;
        if (pendingRequests[0] > 0) return;

        int totalTx = 0;
        for (StorePerformance p : performanceList) {
            totalTx += p.transactionCount;
            Integer staff = storeStaffCountMap.get(p.storeId);
            p.staffCount = (staff != null) ? staff : 0;
        }

        if (failed[0]) {
            if (tvSuperTotalSales != null) tvSuperTotalSales.setText("—");
            if (tvSuperTotalTransactions != null) tvSuperTotalTransactions.setText("—");
            if (tvOverviewTotalSales != null) tvOverviewTotalSales.setText("—");
            if (tvOverviewTransactions != null) tvOverviewTransactions.setText("—");
            if (tvOverviewAvgOrder != null) tvOverviewAvgOrder.setText("—");
            Toast.makeText(this, "Some store metrics could not be loaded", Toast.LENGTH_SHORT).show();
        } else {
            String formattedSales = String.format(Locale.US, "₱%.2f", totalSales[0]);
            if (tvSuperTotalSales != null) tvSuperTotalSales.setText(formattedSales);
            if (tvSuperTotalTransactions != null) tvSuperTotalTransactions.setText(String.valueOf(totalTx));
            if (tvOverviewTotalSales != null) tvOverviewTotalSales.setText(formattedSales);
            if (tvOverviewTransactions != null) tvOverviewTransactions.setText(String.valueOf(totalTx));

            double avgOrder = totalTx > 0 ? (totalSales[0] / totalTx) : 0.0;
            if (tvOverviewAvgOrder != null) {
                tvOverviewAvgOrder.setText(String.format(Locale.US, "₱%.2f", avgOrder));
            }
        }

        currentPerformanceList.clear();
        currentPerformanceList.addAll(performanceList);
        renderStorePerformanceRows(performanceList);
    }

    private void renderStorePerformanceRows(List<StorePerformance> performanceList) {
        if (llStorePerformanceRows == null) return;
        llStorePerformanceRows.removeAllViews();

        if (performanceList.isEmpty()) {
            TextView emptyTv = new TextView(this);
            emptyTv.setText("No registered stores found.");
            emptyTv.setPadding(0, 48, 0, 48);
            emptyTv.setTextSize(13f);
            emptyTv.setTextColor(0xFF64748B);
            emptyTv.setGravity(Gravity.CENTER);
            llStorePerformanceRows.addView(emptyTv);
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int padH = (int) (14 * density);
        int padV = (int) (12 * density);

        for (StorePerformance item : performanceList) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(padH, padV, padH, padV);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(0xFFFFFFFF);

            // Column 1: STORE (weight 1.8)
            TextView tvName = new TextView(this);
            LinearLayout.LayoutParams lpName = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.8f);
            tvName.setLayoutParams(lpName);
            tvName.setText(item.storeName);
            tvName.setTextSize(13f);
            tvName.setTypeface(null, Typeface.BOLD);
            tvName.setTextColor(0xFF0F172A);
            tvName.setSingleLine(true);
            tvName.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(tvName);

            // Column 2: LOCATION (weight 1.6)
            TextView tvLoc = new TextView(this);
            LinearLayout.LayoutParams lpLoc = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f);
            tvLoc.setLayoutParams(lpLoc);
            tvLoc.setText(!TextUtils.isEmpty(item.location) ? item.location : "—");
            tvLoc.setTextSize(12f);
            tvLoc.setTextColor(0xFF64748B);
            tvLoc.setSingleLine(true);
            tvLoc.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(tvLoc);

            // Column 3: TODAY'S SALES (weight 1.2, end)
            TextView tvSales = new TextView(this);
            LinearLayout.LayoutParams lpSales = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f);
            tvSales.setLayoutParams(lpSales);
            tvSales.setText(String.format(Locale.US, "₱%.2f", item.sales));
            tvSales.setTextSize(13f);
            tvSales.setTypeface(null, Typeface.BOLD);
            tvSales.setTextColor(0xFF0F172A);
            tvSales.setGravity(Gravity.END);
            row.addView(tvSales);

            // Column 4: ORDERS (weight 0.8, end)
            TextView tvTx = new TextView(this);
            LinearLayout.LayoutParams lpTx = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f);
            tvTx.setLayoutParams(lpTx);
            tvTx.setText(String.valueOf(item.transactionCount));
            tvTx.setTextSize(13f);
            tvTx.setTextColor(0xFF64748B);
            tvTx.setGravity(Gravity.END);
            row.addView(tvTx);

            // Column 5: STATUS (weight 0.8, center)
            LinearLayout colStatus = new LinearLayout(this);
            LinearLayout.LayoutParams lpStatusCol = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f);
            colStatus.setLayoutParams(lpStatusCol);
            colStatus.setGravity(Gravity.CENTER);

            TextView tvStatus = new TextView(this);
            tvStatus.setText("Active");
            tvStatus.setTextSize(11f);
            tvStatus.setTypeface(null, Typeface.BOLD);
            tvStatus.setTextColor(0xFF059669);
            tvStatus.setBackgroundResource(R.drawable.bg_badge_emerald);
            tvStatus.setPadding((int) (8 * density), (int) (3 * density), (int) (8 * density), (int) (3 * density));
            colStatus.addView(tvStatus);
            row.addView(colStatus);

            // Column 6: ACTIONS (weight 0.6, center)
            LinearLayout colAction = new LinearLayout(this);
            LinearLayout.LayoutParams lpActionCol = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f);
            colAction.setLayoutParams(lpActionCol);
            colAction.setGravity(Gravity.CENTER);

            TextView tvAction = new TextView(this);
            tvAction.setText("View");
            tvAction.setTextSize(11f);
            tvAction.setTypeface(null, Typeface.BOLD);
            tvAction.setTextColor(0xFF2563EB);
            tvAction.setBackgroundResource(R.drawable.bg_badge_blue);
            tvAction.setPadding((int) (8 * density), (int) (3 * density), (int) (8 * density), (int) (3 * density));
            colAction.addView(tvAction);
            row.addView(colAction);

            // Row click listener to open Reports
            View.OnClickListener openReportsListener = v -> {
                Intent intent = new Intent(this, ReportsActivity.class);
                intent.putExtra("store_id", item.storeId);
                intent.putExtra("selected_store_name", item.storeName);
                intent.putExtra("user_id", userId);
                intent.putExtra("role", role);
                intent.putExtra("full_name", fullName);
                intent.putExtra("username", username);
                intent.putExtra("email", email);
                startActivity(intent);
            };

            row.setOnClickListener(openReportsListener);
            tvAction.setOnClickListener(openReportsListener);

            llStorePerformanceRows.addView(row);

            // Subtle 1px Divider
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFE2E8F0);
            llStorePerformanceRows.addView(divider);
        }
    }

    private void showStoreComparisonDialog() {
        if (currentPerformanceList == null || currentPerformanceList.size() < 2) {
            Toast.makeText(this, "At least 2 stores are required to compare performance.", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_compare_stores, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        Spinner spinnerA = dialogView.findViewById(R.id.spinnerCompareStoreA);
        Spinner spinnerB = dialogView.findViewById(R.id.spinnerCompareStoreB);

        TextView tvColA = dialogView.findViewById(R.id.tvCompareColA);
        TextView tvColB = dialogView.findViewById(R.id.tvCompareColB);
        TextView tvSalesA = dialogView.findViewById(R.id.tvCompareSalesA);
        TextView tvSalesB = dialogView.findViewById(R.id.tvCompareSalesB);
        TextView tvTxA = dialogView.findViewById(R.id.tvCompareTxA);
        TextView tvTxB = dialogView.findViewById(R.id.tvCompareTxB);
        TextView tvAvgA = dialogView.findViewById(R.id.tvCompareAvgA);
        TextView tvAvgB = dialogView.findViewById(R.id.tvCompareAvgB);
        TextView tvStaffA = dialogView.findViewById(R.id.tvCompareStaffA);
        TextView tvStaffB = dialogView.findViewById(R.id.tvCompareStaffB);
        TextView tvLocA = dialogView.findViewById(R.id.tvCompareLocA);
        TextView tvLocB = dialogView.findViewById(R.id.tvCompareLocB);
        Button btnClose = dialogView.findViewById(R.id.btnCompareClose);

        List<String> names = new ArrayList<>();
        for (StorePerformance p : currentPerformanceList) {
            names.add(p.storeName);
        }

        ArrayAdapter<String> adapterA = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        ArrayAdapter<String> adapterB = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        spinnerA.setAdapter(adapterA);
        spinnerB.setAdapter(adapterB);

        spinnerA.setSelection(0);
        spinnerB.setSelection(1);

        AdapterView.OnItemSelectedListener updateListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int posA = spinnerA.getSelectedItemPosition();
                int posB = spinnerB.getSelectedItemPosition();
                if (posA < 0 || posA >= currentPerformanceList.size() || posB < 0 || posB >= currentPerformanceList.size()) {
                    return;
                }

                StorePerformance a = currentPerformanceList.get(posA);
                StorePerformance b = currentPerformanceList.get(posB);

                tvColA.setText(a.storeName.toUpperCase(Locale.US));
                tvColB.setText(b.storeName.toUpperCase(Locale.US));

                tvSalesA.setText(String.format(Locale.US, "₱%.2f", a.sales));
                tvSalesB.setText(String.format(Locale.US, "₱%.2f", b.sales));

                if (a.sales > b.sales) {
                    tvSalesA.setTextColor(0xFF10B981);
                    tvSalesB.setTextColor(0xFF0F172A);
                } else if (b.sales > a.sales) {
                    tvSalesA.setTextColor(0xFF0F172A);
                    tvSalesB.setTextColor(0xFF10B981);
                } else {
                    tvSalesA.setTextColor(0xFF0F172A);
                    tvSalesB.setTextColor(0xFF0F172A);
                }

                tvTxA.setText(String.valueOf(a.transactionCount));
                tvTxB.setText(String.valueOf(b.transactionCount));

                double avgA = a.transactionCount > 0 ? a.sales / a.transactionCount : 0.0;
                double avgB = b.transactionCount > 0 ? b.sales / b.transactionCount : 0.0;
                tvAvgA.setText(String.format(Locale.US, "₱%.2f", avgA));
                tvAvgB.setText(String.format(Locale.US, "₱%.2f", avgB));

                tvStaffA.setText(String.valueOf(a.staffCount));
                tvStaffB.setText(String.valueOf(b.staffCount));

                tvLocA.setText(a.location != null && !a.location.isEmpty() ? a.location : "—");
                tvLocB.setText(b.location != null && !b.location.isEmpty() ? b.location : "—");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spinnerA.setOnItemSelectedListener(updateListener);
        spinnerB.setOnItemSelectedListener(updateListener);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void loadUserMetrics(RequestQueue queue) {
        JsonObjectRequest usersRequest = new JsonObjectRequest(
                Request.Method.GET, USERS_URL, null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONArray userArray = response.getJSONArray("users");
                            int totalUsers = userArray.length();
                            int totalAdmins = 0;

                            storeStaffCountMap.clear();
                            for (int i = 0; i < userArray.length(); i++) {
                                JSONObject userObj = userArray.optJSONObject(i);
                                if (userObj != null) {
                                    String userRole = userObj.optString("role");
                                    if ("store_admin".equals(userRole)) {
                                        totalAdmins++;
                                    }
                                    if ("staff".equals(userRole)) {
                                        int sId = userObj.optInt("store_id", -1);
                                        if (sId > 0) {
                                            storeStaffCountMap.put(sId, storeStaffCountMap.getOrDefault(sId, 0) + 1);
                                        }
                                    }
                                }
                            }

                            for (StorePerformance p : currentPerformanceList) {
                                Integer count = storeStaffCountMap.get(p.storeId);
                                p.staffCount = (count != null) ? count : 0;
                            }

                            if (tvSuperTotalUsers != null) {
                                tvSuperTotalUsers.setText(String.valueOf(totalUsers));
                            }
                        } else {
                            if (tvSuperTotalUsers != null) tvSuperTotalUsers.setText("—");
                        }
                    } catch (JSONException e) {
                        if (tvSuperTotalUsers != null) tvSuperTotalUsers.setText("—");
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(SuperAdminDashboardActivity.this, error)) return;
                    if (tvSuperTotalUsers != null) tvSuperTotalUsers.setText("—");
                }
        );

        queue.add(usersRequest);
    }

    private void loadStoreOwnersCards(RequestQueue queue) {
        if (llStoreOwnersCards == null) return;
        String url = ApiConfig.getUrl("get_store_owners.php");
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONArray owners = response.optJSONArray("owners");
                            renderStoreOwnersCards(owners);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    // Fail silently or keep placeholder
                }
        );
        queue.add(req);
    }

    private void renderStoreOwnersCards(JSONArray owners) {
        if (llStoreOwnersCards == null) return;
        llStoreOwnersCards.removeAllViews();

        if (owners == null || owners.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No multi-branch store owners configured.");
            empty.setTextColor(0xFF64748B);
            empty.setTextSize(13f);
            empty.setPadding(0, 16, 0, 16);
            llStoreOwnersCards.addView(empty);
            return;
        }

        for (int i = 0; i < owners.length(); i++) {
            JSONObject owner = owners.optJSONObject(i);
            if (owner == null) continue;

            String storeNameStr = owner.optString("store_name", "Retail Store Portfolio");
            String ownerName = owner.optString("owner_name", "Store Owner");
            String managerName = owner.optString("manager_name", "");
            String usernameStr = owner.optString("owner_username", owner.optString("username", "owner"));
            String description = owner.optString("description", "");
            int primaryStoreId = owner.optInt("primary_store_id", 0);
            double totalSales = owner.optDouble("overall_sales", owner.optDouble("total_sales", 0.0));
            double todaySales = owner.optDouble("overall_today_sales", owner.optDouble("today_sales", 0.0));
            int totalOrders = owner.optInt("overall_transactions", owner.optInt("total_orders", 0));
            JSONArray branches = owner.optJSONArray("branches");
            JSONArray salesProgress = owner.optJSONArray("sales_progress");

            if (primaryStoreId <= 0 && branches != null && branches.length() > 0) {
                primaryStoreId = branches.optJSONObject(0).optInt("id", 0);
            }
            final int editStoreId = primaryStoreId;
            final String finalStoreName = storeNameStr;
            final String finalCurrentDesc = description;

            // Outer Business Flashcard (clean, compact card)
            LinearLayout ownerCard = new LinearLayout(this);
            ownerCard.setOrientation(LinearLayout.VERTICAL);
            ownerCard.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_card_white));
            ownerCard.setPadding(28, 22, 28, 22);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(0, 0, 0, 16);
            ownerCard.setLayoutParams(cardLp);
            ownerCard.setClickable(true);
            ownerCard.setFocusable(true);

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout nameCol = new LinearLayout(this);
            nameCol.setOrientation(LinearLayout.VERTICAL);
            nameCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvBigStoreName = new TextView(this);
            tvBigStoreName.setText(storeNameStr);
            tvBigStoreName.setTextColor(0xFF0F172A);
            tvBigStoreName.setTextSize(18f);
            tvBigStoreName.setTypeface(null, Typeface.BOLD);

            TextView tvOwnerUnderneath = new TextView(this);
            String ownerText = "👤 Business Owner: " + ownerName + (managerName.isEmpty() ? "" : " • Manager: " + managerName);
            tvOwnerUnderneath.setText(ownerText);
            tvOwnerUnderneath.setTextColor(0xFF64748B);
            tvOwnerUnderneath.setTextSize(13f);
            tvOwnerUnderneath.setPadding(0, 3, 0, 0);

            nameCol.addView(tvBigStoreName);
            nameCol.addView(tvOwnerUnderneath);

            // Badge showing branch count
            TextView tvBranchBadge = new TextView(this);
            int branchCount = (branches != null) ? branches.length() : 1;
            tvBranchBadge.setText(branchCount + (branchCount == 1 ? " Branch" : " Branches"));
            tvBranchBadge.setTextColor(0xFF2563EB);
            tvBranchBadge.setTextSize(11f);
            tvBranchBadge.setTypeface(null, Typeface.BOLD);
            tvBranchBadge.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_badge_blue));
            tvBranchBadge.setPadding(16, 6, 16, 6);

            topRow.addView(nameCol);
            topRow.addView(tvBranchBadge);
            ownerCard.addView(topRow);

            // Tap hint bar
            TextView tvTapHint = new TextView(this);
            tvTapHint.setText("Tap to view full business progress & line chart trend ▶");
            tvTapHint.setTextColor(0xFF2563EB);
            tvTapHint.setTextSize(11f);
            tvTapHint.setPadding(0, 8, 0, 0);
            ownerCard.addView(tvTapHint);

            final int fOwnerId = owner.optInt("owner_id", 0);
            ownerCard.setOnClickListener(v -> {
                Intent progIntent = new Intent(this, BusinessProgressActivity.class);
                progIntent.putExtra(BusinessProgressActivity.EXTRA_MODE, "business");
                progIntent.putExtra(BusinessProgressActivity.EXTRA_OWNER_ID, fOwnerId > 0 ? fOwnerId : editStoreId);
                progIntent.putExtra(BusinessProgressActivity.EXTRA_STORE_ID, editStoreId);
                progIntent.putExtra(BusinessProgressActivity.EXTRA_TITLE, storeNameStr);
                progIntent.putExtra(BusinessProgressActivity.EXTRA_SUBTITLE, "Owner: " + ownerName);
                progIntent.putExtra("user_id", userId);
                progIntent.putExtra("role", role);
                startActivity(progIntent);
            });

            llStoreOwnersCards.addView(ownerCard);
        }
    }

    private void showEditDescriptionDialog(int storeId, String storeName, String currentDesc) {
        EditText etDesc = new EditText(this);
        etDesc.setHint("Enter description, business model, operating hours, etc.");
        etDesc.setText(currentDesc);
        etDesc.setLines(4);
        etDesc.setGravity(Gravity.TOP | Gravity.START);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 24, 40, 16);
        layout.addView(etDesc);

        new AlertDialog.Builder(this)
                .setTitle("Store Description")
                .setMessage("Update description for \"" + storeName + "\":")
                .setView(layout)
                .setPositiveButton("Save Description", (dialog, which) -> {
                    String newDesc = etDesc.getText().toString().trim();
                    saveStoreDescription(storeId, newDesc);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveStoreDescription(int storeId, String description) {
        if (storeId <= 0) return;
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JSONObject payload = new JSONObject();
        try {
            payload.put("store_id", storeId);
            payload.put("description", description);
        } catch (JSONException ignored) {}

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST,
                ApiConfig.getUrl("manage_stores.php?action=update_description"),
                payload,
                res -> {
                    Toast.makeText(this, res.optString("message", "Description saved!"), Toast.LENGTH_SHORT).show();
                    loadStoreOwnersCards(ApiSession.getRequestQueue(this));
                },
                err -> Toast.makeText(this, "Failed to update description", Toast.LENGTH_SHORT).show()
        );
        queue.add(req);
    }

    private void showContributionBreakdownDialog(String storeName, JSONArray branches) {
        if (branches == null || branches.length() == 0) {
            Toast.makeText(this, "No branch sales data available to calculate contribution.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < branches.length(); i++) {
            JSONObject b = branches.optJSONObject(i);
            if (b == null) continue;
            String bName = b.optString("store_name", "Branch");
            double sales = b.optDouble("total_sales", 0.0);
            double pct = b.optDouble("contribution_pct", 0.0);
            sb.append("• ").append(bName).append("\n")
              .append("   Contribution: ").append(pct).append("%")
              .append(" (₱").append(String.format(Locale.US, "%.2f", sales)).append(")\n\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("Branch Contribution Breakdown")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }

    private void showBranchProgressDialog(JSONObject branch) {
        if (branch == null) return;

        int branchId = branch.optInt("id", 0);
        String branchName = branch.optString("store_name", "Branch");
        String location = branch.optString("location", "—");
        double bToday = branch.optDouble("today_sales", 0.0);
        double bTotal = branch.optDouble("total_sales", 0.0);
        int totalProducts = branch.optInt("total_products", 0);
        JSONArray trend = branch.optJSONArray("sales_progress");
        JSONArray topProducts = branch.optJSONArray("top_products");
        JSONArray branchUsers = branch.optJSONArray("branch_users");

        ScrollView sv = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(36, 24, 36, 24);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(branchName + "\n📍 " + location);
        tvTitle.setTextColor(0xFF0F172A);
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        content.addView(tvTitle);

        TextView tvStats = new TextView(this);
        tvStats.setText(String.format(Locale.US, "Today: ₱%.2f  |  Total: ₱%.2f  |  Items: %d", bToday, bTotal, totalProducts));
        tvStats.setTextColor(0xFF2563EB);
        tvStats.setTextSize(12f);
        tvStats.setPadding(0, 6, 0, 12);
        content.addView(tvStats);

        // Branch Line Chart
        TextView tvChartLbl = new TextView(this);
        tvChartLbl.setText("📈 Real-time Branch Sales Line Chart (tap for top products):");
        tvChartLbl.setTextColor(0xFF334155);
        tvChartLbl.setTextSize(12f);
        tvChartLbl.setTypeface(null, Typeface.BOLD);
        content.addView(tvChartLbl);

        SimpleLineChartView chart = new SimpleLineChartView(this);
        chart.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 200));

        List<Float> pts = new ArrayList<>();
        List<String> lbls = new ArrayList<>();
        if (trend != null && trend.length() > 0) {
            for (int i = 0; i < trend.length(); i++) {
                JSONObject pt = trend.optJSONObject(i);
                if (pt != null) {
                    pts.add((float) pt.optDouble("sales", 0.0));
                    lbls.add(pt.optString("label", "Day"));
                }
            }
        } else {
            pts.add(0f); pts.add(0f); pts.add(0f);
            lbls.add("Day 1"); lbls.add("Day 2"); lbls.add("Day 3");
        }
        chart.setData(pts, lbls);

        chart.setClickable(true);
        chart.setFocusable(true);
        chart.setOnClickListener(v -> showTopPurchasedProductsDialog(branchName, topProducts));
        content.addView(chart);

        // Top products quick button
        Button btnTopProds = new Button(this);
        btnTopProds.setText("🏆 View Top Purchased Products & Profit");
        btnTopProds.setTextSize(11f);
        btnTopProds.setTextColor(0xFF059669);
        btnTopProds.setBackgroundColor(0xFFECFDF5);
        btnTopProds.setOnClickListener(v -> showTopPurchasedProductsDialog(branchName, topProducts));
        LinearLayout.LayoutParams btnPLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnPLp.setMargins(0, 6, 0, 16);
        btnTopProds.setLayoutParams(btnPLp);
        content.addView(btnTopProds);

        // Branch Users & Roles Section
        TextView tvUsersLbl = new TextView(this);
        tvUsersLbl.setText("👥 Branch Staff & Roles (tap user for details):");
        tvUsersLbl.setTextColor(0xFF334155);
        tvUsersLbl.setTextSize(12f);
        tvUsersLbl.setTypeface(null, Typeface.BOLD);
        content.addView(tvUsersLbl);

        if (branchUsers != null && branchUsers.length() > 0) {
            for (int u = 0; u < branchUsers.length(); u++) {
                JSONObject userObj = branchUsers.optJSONObject(u);
                if (userObj == null) continue;

                String fn = userObj.optString("full_name", "User");
                String un = userObj.optString("username", "user");
                String rl = userObj.optString("role_display", userObj.optString("role", "Staff"));

                LinearLayout uCard = new LinearLayout(this);
                uCard.setOrientation(LinearLayout.HORIZONTAL);
                uCard.setGravity(Gravity.CENTER_VERTICAL);
                uCard.setBackgroundColor(0xFFF8FAFC);
                uCard.setPadding(16, 12, 16, 12);
                LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                uLp.setMargins(0, 6, 0, 0);
                uCard.setLayoutParams(uLp);

                LinearLayout uInfo = new LinearLayout(this);
                uInfo.setOrientation(LinearLayout.VERTICAL);
                uInfo.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvUName = new TextView(this);
                tvUName.setText(fn + " (@" + un + ")");
                tvUName.setTextColor(0xFF0F172A);
                tvUName.setTextSize(12f);
                tvUName.setTypeface(null, Typeface.BOLD);

                TextView tvURole = new TextView(this);
                tvURole.setText("Role: " + rl);
                tvURole.setTextColor(0xFF2563EB);
                tvURole.setTextSize(11f);

                uInfo.addView(tvUName);
                uInfo.addView(tvURole);

                uCard.addView(uInfo);

                uCard.setClickable(true);
                uCard.setFocusable(true);
                uCard.setOnClickListener(v -> showUserDetailsDialog(userObj));
                content.addView(uCard);
            }
        } else {
            TextView noUsers = new TextView(this);
            noUsers.setText("No staff assigned to this branch.");
            noUsers.setTextColor(0xFF94A3B8);
            noUsers.setTextSize(11f);
            noUsers.setPadding(0, 6, 0, 6);
            content.addView(noUsers);
        }

        sv.addView(content);

        new AlertDialog.Builder(this)
                .setTitle("Branch Real-time Details")
                .setView(sv)
                .setPositiveButton("Full Reports", (dialog, which) -> {
                    Intent repIntent = new Intent(this, ReportsActivity.class);
                    repIntent.putExtra("store_id", branchId);
                    repIntent.putExtra("store_name", branchName);
                    repIntent.putExtra("user_id", userId);
                    repIntent.putExtra("role", role);
                    repIntent.putExtra("full_name", fullName);
                    startActivity(repIntent);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showTopPurchasedProductsDialog(String branchName, JSONArray topProducts) {
        if (topProducts == null || topProducts.length() == 0) {
            Toast.makeText(this, "No sales recorded for this branch yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < topProducts.length(); i++) {
            JSONObject p = topProducts.optJSONObject(i);
            if (p == null) continue;
            String pName = p.optString("product_name", "Product");
            String cat = p.optString("category_name", "General");
            int sold = p.optInt("quantity_sold", 0);
            double gross = p.optDouble("gross_earnings", 0.0);
            double net = p.optDouble("net_profit", 0.0);
            int stock = p.optInt("stock_on_hand", 0);

            sb.append("• ").append(pName).append(" [").append(cat).append("]\n")
              .append("   Sold: ").append(sold).append(" units | On-Hand: ").append(stock).append("\n")
              .append("   Gross Earned: ₱").append(String.format(Locale.US, "%.2f", gross))
              .append(" | Net Profit: ₱").append(String.format(Locale.US, "%.2f", net)).append("\n\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("Top Purchased Products (" + branchName + ")")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }

    private void showUserDetailsDialog(JSONObject userObj) {
        if (userObj == null) return;
        String fn = userObj.optString("full_name", "User");
        String un = userObj.optString("username", "username");
        String em = userObj.optString("email", "No email provided");
        String rl = userObj.optString("role_display", userObj.optString("role", "Staff"));
        String lastTx = userObj.optString("last_transaction_at", "No transactions on duty yet");

        String msg = "Full Name: " + fn + "\n"
                   + "Username: @" + un + "\n"
                   + "Assigned Role: " + rl + "\n"
                   + "Email: " + em + "\n"
                   + "Cashier Duty Status: " + (lastTx.equals("null") ? "None" : lastTx);

        new AlertDialog.Builder(this)
                .setTitle("User Profile & Role")
                .setMessage(msg)
                .setPositiveButton("Close", null)
                .show();
    }

    private void setMetricPlaceholders() {
        if (tvSuperTotalStores != null) tvSuperTotalStores.setText("—");
        if (tvSuperTotalUsers != null) tvSuperTotalUsers.setText("—");
        if (tvSuperTotalSales != null) tvSuperTotalSales.setText("—");
        if (tvSuperTotalTransactions != null) tvSuperTotalTransactions.setText("—");
        if (tvOverviewTotalSales != null) tvOverviewTotalSales.setText("—");
        if (tvOverviewTransactions != null) tvOverviewTransactions.setText("—");
        if (tvOverviewAvgOrder != null) tvOverviewAvgOrder.setText("—");
    }

    private boolean isAuthorized() {
        return userId > 0 && "super_admin".equals(role);
    }

    private void openSuperAdminDestination(Class<?> destination) {
        Intent intent = new Intent(this, destination);
        intent.putExtra("user_id", userId);
        intent.putExtra("role", role);
        intent.putExtra("full_name", fullName);
        intent.putExtra("username", username);
        intent.putExtra("email", email);
        startActivity(intent);
    }

    private void openStoreSelection(String selectionTarget) {
        Intent intent = new Intent(this, StoresActivity.class);
        intent.putExtra(StoresActivity.EXTRA_SELECTION_MODE, true);
        intent.putExtra(StoresActivity.EXTRA_SELECTION_TARGET, selectionTarget);
        intent.putExtra("user_id", userId);
        intent.putExtra("role", role);
        intent.putExtra("full_name", fullName);
        intent.putExtra("username", username);
        intent.putExtra("email", email);
        startActivity(intent);
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
