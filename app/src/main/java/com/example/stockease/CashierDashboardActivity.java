package com.example.stockease;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CashierDashboardActivity extends AppCompatActivity {

    private int storeId = -1;
    private int userId = -1;
    private String role = "";
    private String fullName = "";
    private String storeName = "";

    private static final String TRANSACTIONS_URL = ApiConfig.getUrl("get_transactions.php");

    private TextView tvCashierStaffName, tvCashierHeaderStorePill, tvCashierHeaderDate;
    private TextView tvStaffAvatarInitials, tvStaffProfileName;
    private TextView tvCashierShiftSales, tvCashierShiftTxCount, tvCashierEmptySales;
    private TextView tvCashierShiftSalesTrend;
    private TextView tvShiftStartTime, tvShiftLastTime, tvShiftTotalSalesSummary;
    private View navStaffDashboard, navStaffPOS, navStaffTransactions, navStaffShift, navStaffSettings, navStaffLogout;
    private RecyclerView rvCashierRecentSales;
    private TransactionAdapter adapter;
    private final List<Transaction> shiftTransactions = new ArrayList<>();

    private double totalShiftSales = 0.0;
    private double cashSales = 0.0;
    private double gcashSales = 0.0;
    private String firstSaleTime = "08:00 AM";
    private String lastSaleTime = "—";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cashier_dashboard);

        storeId = getIntent().getIntExtra("store_id", -1);
        userId = getIntent().getIntExtra("user_id", -1);
        role = getIntent().getStringExtra("role");
        fullName = getIntent().getStringExtra("full_name");
        storeName = getIntent().getStringExtra("store_name");

        if (storeId <= 0 || userId <= 0 || !"staff".equals(role)) {
            Toast.makeText(this, "This dashboard is available only to Staff accounts with a valid store.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            ScreenTransitions.open(CashierDashboardActivity.this, intent);
            ScreenTransitions.finish(CashierDashboardActivity.this);
            return;
        }

        // Header Views
        tvCashierStaffName = findViewById(R.id.tvCashierStaffName);
        tvCashierHeaderStorePill = findViewById(R.id.tvCashierHeaderStorePill);
        tvCashierHeaderDate = findViewById(R.id.tvCashierHeaderDate);
        tvStaffAvatarInitials = findViewById(R.id.tvStaffAvatarInitials);
        tvStaffProfileName = findViewById(R.id.tvStaffProfileName);

        setupHeaderInfo();

        // Stat Card Views
        tvCashierShiftSales = findViewById(R.id.tvCashierShiftSales);
        tvCashierShiftTxCount = findViewById(R.id.tvCashierShiftTxCount);
        tvCashierShiftSalesTrend = findViewById(R.id.tvCashierShiftSalesTrend);

        // Shift Summary Card Views
        tvShiftStartTime = findViewById(R.id.tvShiftStartTime);
        tvShiftLastTime = findViewById(R.id.tvShiftLastTime);
        tvShiftTotalSalesSummary = findViewById(R.id.tvShiftTotalSalesSummary);

        tvCashierEmptySales = findViewById(R.id.tvCashierEmptySales);

        View btnOpenPOS = findViewById(R.id.btnOpenPOS);
        View btnCashierShiftSummary = findViewById(R.id.btnCashierShiftSummary);
        TextView tvCashierRefresh = findViewById(R.id.tvCashierRefresh);

        if (btnOpenPOS != null) {
            btnOpenPOS.setOnClickListener(v -> openPOS());
        }

        if (btnCashierShiftSummary != null) {
            btnCashierShiftSummary.setOnClickListener(v -> showShiftSummaryDialog());
        }

        if (tvCashierRefresh != null) {
            tvCashierRefresh.setOnClickListener(v -> openTransactionHistory());
        }

        // Sidebar Navigation
        navStaffDashboard = findViewById(R.id.navStaffDashboard);
        navStaffPOS = findViewById(R.id.navStaffPOS);
        navStaffTransactions = findViewById(R.id.navStaffTransactions);
        navStaffShift = findViewById(R.id.navStaffShift);
        navStaffLogout = findViewById(R.id.navStaffLogout);
        navStaffSettings = findViewById(R.id.navStaffSettings);

        if (navStaffPOS != null) {
            navStaffPOS.setOnClickListener(v -> openPOS());
        }
        if (navStaffTransactions != null) {
            navStaffTransactions.setOnClickListener(v -> openTransactionHistory());
        }
        if (navStaffShift != null) {
            navStaffShift.setOnClickListener(v -> showShiftSummaryDialog());
        }
        if (navStaffSettings != null) {
            // Settings is admin/manager-only and bounces other roles back to
            // login, so staff get an explanatory toast instead of a session wipe.
            navStaffSettings.setOnClickListener(v ->
                    Toast.makeText(this, "Settings is only available to administrator accounts.", Toast.LENGTH_SHORT).show());
        }
        if (navStaffLogout != null) {
            navStaffLogout.setOnClickListener(v -> ApiSession.logout(this));
        }

        // Collapsible sidebar sections (accordion): OVERVIEW opens by default
        SidebarSection.attach(this, R.id.navHeaderOverview, R.id.navArrowOverview, R.id.navSectionOverviewItems, true);
        SidebarSection.attach(this, R.id.navHeaderManagement, R.id.navArrowManagement, R.id.navSectionManagementItems, false);
        SidebarSection.attach(this, R.id.navHeaderAnalytics, R.id.navArrowAnalytics, R.id.navSectionAnalyticsItems, false);
        SidebarSection.attach(this, R.id.navHeaderSystem, R.id.navArrowSystem, R.id.navSectionSystemItems, false);

        // Tapping the profile name or avatar opens the profile screen
        View.OnClickListener openProfile = v -> {
            Intent intent = new Intent(CashierDashboardActivity.this, ProfileActivity.class);
            addIdentityExtras(intent);
            ScreenTransitions.open(CashierDashboardActivity.this, intent);
        };
        if (tvStaffProfileName != null) {
            tvStaffProfileName.setOnClickListener(openProfile);
        }
        if (tvStaffAvatarInitials != null) {
            tvStaffAvatarInitials.setOnClickListener(openProfile);
        }

        // Recycler View
        rvCashierRecentSales = findViewById(R.id.rvCashierRecentSales);
        rvCashierRecentSales.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter(this, shiftTransactions);
        rvCashierRecentSales.setAdapter(adapter);

        // Every dashboard card responds to a tap: navigation where a matching
        // screen exists, a detail sheet for the rest.
        setupCardClicks();

        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));
    }

    /** Wires the stat cards, the shift summary card and the transactions card. */
    private void setupCardClicks() {
        View shiftSalesCard = findViewById(R.id.cardCashierShiftSales);
        if (shiftSalesCard != null) {
            shiftSalesCard.setOnClickListener(v -> showShiftSummaryDialog());
        }

        View shiftTxCard = findViewById(R.id.cardCashierShiftTx);
        if (shiftTxCard != null) {
            shiftTxCard.setOnClickListener(v -> openTransactionHistory());
        }

        View shiftSummaryCard = findViewById(R.id.cardShiftSummary);
        if (shiftSummaryCard != null) {
            shiftSummaryCard.setOnClickListener(v -> showShiftSummaryDialog());
        }

        View recentSalesCard = findViewById(R.id.cardCashierRecentSales);
        if (recentSalesCard != null) {
            recentSalesCard.setOnClickListener(v -> openTransactionHistory());
        }
    }

    /** Opens the full transaction list for this shift's store and user. */
    private void openTransactionHistory() {
        Intent intent = new Intent(this, TransactionHistoryActivity.class);
        addIdentityExtras(intent);
        ScreenTransitions.open(CashierDashboardActivity.this, intent);
    }

    private void setupHeaderInfo() {
        String displayName = (fullName != null && !fullName.trim().isEmpty()) ? fullName : "Staff";
        if (tvCashierStaffName != null) {
            tvCashierStaffName.setText("Welcome, " + displayName);
        }

        String displayStore = (storeName != null && !storeName.trim().isEmpty()) ? storeName : "Branch " + storeId;
        if (tvCashierHeaderStorePill != null) {
            tvCashierHeaderStorePill.setText(displayStore);
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.US);
        if (tvCashierHeaderDate != null) {
            tvCashierHeaderDate.setText(dateFormat.format(new Date()));
        }

        if (tvStaffProfileName != null) {
            tvStaffProfileName.setText(displayName);
        }

        if (tvStaffAvatarInitials != null) {
            String initials = "ST";
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
            tvStaffAvatarInitials.setText(initials);
        }
    }

    private final android.os.Handler syncHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable syncRunnable = new Runnable() {
        @Override
        public void run() {
            loadShiftTransactions();
            syncHandler.postDelayed(this, 10000); // Poll every 10 seconds
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        loadShiftTransactions();
        syncHandler.postDelayed(syncRunnable, 10000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        syncHandler.removeCallbacks(syncRunnable);
    }

    private void openPOS() {
        Intent intent = new Intent(this, POSActivity.class);
        addIdentityExtras(intent);
        ScreenTransitions.open(CashierDashboardActivity.this, intent);
    }

    private void addIdentityExtras(Intent intent) {
        intent.putExtra("store_id", storeId);
        intent.putExtra("user_id", userId);
        intent.putExtra("role", role);
        intent.putExtra("full_name", fullName);
        intent.putExtra("store_name", storeName);
    }

    private void loadShiftTransactions() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String url = TRANSACTIONS_URL + "?store_id=" + storeId + "&start_date=" + today + "&end_date=" + today;

        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    shiftTransactions.clear();
                    totalShiftSales = 0.0;
                    cashSales = 0.0;
                    gcashSales = 0.0;
                    lastSaleTime = "—";

                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            int txUserId = obj.optInt("user_id", -1);
                            if (txUserId != -1 && txUserId != userId) {
                                continue;
                            }

                            int id = obj.getInt("id");
                            double totalAmount = obj.getDouble("total_amount");
                            String paymentMethod = obj.getString("payment_method");
                            String transactionDate = obj.getString("transaction_date");
                            String cashier = obj.optString("cashier_name", fullName);

                            totalShiftSales += totalAmount;
                            if ("GCash".equalsIgnoreCase(paymentMethod)) {
                                gcashSales += totalAmount;
                            } else {
                                cashSales += totalAmount;
                            }

                            String timeOnly = extractTime(transactionDate);
                            if (lastSaleTime.equals("—")) {
                                lastSaleTime = timeOnly;
                            }

                            Transaction transaction = new Transaction(
                                    id,
                                    totalAmount,
                                    paymentMethod,
                                    transactionDate,
                                    cashier,
                                    paymentMethod
                            );
                            shiftTransactions.add(transaction);
                        }

                        updateUIWithShiftData();

                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Failed to parse transactions", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to load shift records", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    private void updateUIWithShiftData() {
        if (tvCashierShiftSales != null) {
            tvCashierShiftSales.setText(String.format(Locale.US, "₱%.2f", totalShiftSales));
        }
        int shiftTxCount = shiftTransactions.size();
        if (tvCashierShiftTxCount != null) {
            tvCashierShiftTxCount.setText(String.valueOf(shiftTxCount));
        }
        // Real, derived figure: average sale so far this shift. Hidden when there's
        // nothing to average — the cashier view only has today's data, so a
        // day-over-day percentage isn't computable here.
        if (tvCashierShiftSalesTrend != null) {
            if (shiftTxCount > 0 && totalShiftSales > 0.0) {
                double avg = totalShiftSales / shiftTxCount;
                tvCashierShiftSalesTrend.setText(
                        getString(R.string.trend_avg_ticket, String.format(Locale.US, "₱%.2f", avg)));
                tvCashierShiftSalesTrend.setTextColor(
                        ContextCompat.getColor(this, R.color.text_muted));
                tvCashierShiftSalesTrend.setVisibility(View.VISIBLE);
            } else {
                tvCashierShiftSalesTrend.setVisibility(View.GONE);
            }
        }

        if (tvShiftStartTime != null) {
            tvShiftStartTime.setText(firstSaleTime);
        }
        if (tvShiftLastTime != null) {
            SimpleDateFormat currentTimeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
            tvShiftLastTime.setText(lastSaleTime.equals("—") ? currentTimeFormat.format(new Date()) : lastSaleTime);
        }
        if (tvShiftTotalSalesSummary != null) {
            tvShiftTotalSalesSummary.setText(String.format(Locale.US, "₱%.2f", totalShiftSales));
        }

        if (shiftTransactions.isEmpty()) {
            if (tvCashierEmptySales != null) tvCashierEmptySales.setVisibility(View.VISIBLE);
            if (rvCashierRecentSales != null) rvCashierRecentSales.setVisibility(View.GONE);
        } else {
            if (tvCashierEmptySales != null) tvCashierEmptySales.setVisibility(View.GONE);
            if (rvCashierRecentSales != null) rvCashierRecentSales.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }

    private String extractTime(String dateStr) {
        if (dateStr == null || dateStr.length() < 16) return "—";
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            Date date = inputFormat.parse(dateStr);
            if (date != null) {
                SimpleDateFormat outputFormat = new SimpleDateFormat("hh:mm a", Locale.US);
                return outputFormat.format(date);
            }
        } catch (Exception ignored) {
            if (dateStr.length() >= 16) {
                return dateStr.substring(11, 16);
            }
        }
        return "—";
    }

    private void showShiftSummaryDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_shift_summary, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvDialogStaffStore = dialogView.findViewById(R.id.tvDialogStaffStore);
        TextView tvDialogTotalSales = dialogView.findViewById(R.id.tvDialogTotalSales);
        TextView tvDialogTxCount = dialogView.findViewById(R.id.tvDialogTxCount);
        TextView tvDialogCashSales = dialogView.findViewById(R.id.tvDialogCashSales);
        TextView tvDialogGcashSales = dialogView.findViewById(R.id.tvDialogGcashSales);
        TextView tvDialogFirstSale = dialogView.findViewById(R.id.tvDialogFirstSale);
        TextView tvDialogLastSale = dialogView.findViewById(R.id.tvDialogLastSale);
        Button btnDialogClose = dialogView.findViewById(R.id.btnDialogClose);

        String staffDisplay = fullName != null && !fullName.isEmpty() ? fullName : "Cashier";
        String storeDisplay = storeName != null && !storeName.isEmpty() ? storeName : ("Store " + storeId);
        if (tvDialogStaffStore != null) {
            tvDialogStaffStore.setText("Staff: " + staffDisplay + " • Store: " + storeDisplay);
        }
        if (tvDialogTotalSales != null) {
            tvDialogTotalSales.setText(String.format(Locale.US, "₱%.2f", totalShiftSales));
        }
        if (tvDialogTxCount != null) {
            tvDialogTxCount.setText(shiftTransactions.size() + " transactions processed");
        }
        if (tvDialogCashSales != null) {
            tvDialogCashSales.setText(String.format(Locale.US, "₱%.2f", cashSales));
        }
        if (tvDialogGcashSales != null) {
            tvDialogGcashSales.setText(String.format(Locale.US, "₱%.2f", gcashSales));
        }
        if (tvDialogFirstSale != null) {
            tvDialogFirstSale.setText(firstSaleTime);
        }
        if (tvDialogLastSale != null) {
            tvDialogLastSale.setText(lastSaleTime);
        }

        if (btnDialogClose != null) {
            btnDialogClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }
}
