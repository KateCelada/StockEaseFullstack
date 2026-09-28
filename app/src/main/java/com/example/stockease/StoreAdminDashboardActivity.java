package com.example.stockease;

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
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class StoreAdminDashboardActivity extends AppCompatActivity {

    private TextView tvAdminGreeting, tvHeaderSubtitle, tvHeaderStorePill, tvHeaderDate;
    private TextView tvAdminAvatarInitials, tvAdminProfileName;
    private TextView tvTodaySales, tvTodaySalesTrend, tvTransactions, tvTxTrend, tvProducts, tvLowStockAlerts, tvLowStockSubtitle;
    private View cardLowStock;
    private View cardActionProducts, cardActionInventory, cardActionStaff, cardActionReports;
    private View cardActionTransfers, cardActionInsights;
    private TextView tvViewAllTransactions, tvViewAllLowStock;
    private LinearLayout containerLowStockList, containerRecentSales;
    private View navDashboard, navProducts, navInventory, navSales, navStaff, navReports, navTransfers, navInsights, navSubscription, navSettings, navLogout;

    private int storeId = -1;
    private int userId = -1;
    private String userName = "";
    private String role = "";
    private String storeName = "";
    private String username = "";
    private String email = "";

    private static final String DASHBOARD_URL = ApiConfig.getUrl("get_store_admin_dashboard.php");
    private static final String TRANSACTIONS_URL = ApiConfig.getUrl("get_transactions.php");

    private View containerInboundTransferAlert;
    private TextView tvInboundBadge, tvInboundTitle, tvInboundDesc;
    private Button btnViewInboundTransfer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store_admin_dashboard);

        if (getIntent() != null) {
            storeId = getIntent().getIntExtra("store_id", -1);
            userId = getIntent().getIntExtra("user_id", -1);
            userName = getIntent().getStringExtra("full_name");
            role = getIntent().getStringExtra("role");
            storeName = getIntent().getStringExtra("store_name");
            username = getIntent().getStringExtra("username");
            email = getIntent().getStringExtra("email");
        }

        if (storeId <= 0 || userId <= 0 || !"store_admin".equals(role)) {
            returnToLogin("This dashboard is available only to Store Admin accounts with a valid store.");
            return;
        }

        // Header Views
        tvAdminGreeting = findViewById(R.id.tvAdminGreeting);
        tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle);
        tvHeaderStorePill = findViewById(R.id.tvHeaderStorePill);
        tvHeaderDate = findViewById(R.id.tvHeaderDate);
        tvAdminAvatarInitials = findViewById(R.id.tvAdminAvatarInitials);
        tvAdminProfileName = findViewById(R.id.tvAdminProfileName);

        setupHeaderInfo();

        // Inbound Transfer Alert
        containerInboundTransferAlert = findViewById(R.id.containerInboundTransferAlert);
        tvInboundBadge = findViewById(R.id.tvInboundBadge);
        tvInboundTitle = findViewById(R.id.tvInboundTitle);
        tvInboundDesc = findViewById(R.id.tvInboundDesc);
        btnViewInboundTransfer = findViewById(R.id.btnViewInboundTransfer);

        // Stat Card Views
        tvTodaySales = findViewById(R.id.tvTodaySales);
        tvTodaySalesTrend = findViewById(R.id.tvTodaySalesTrend);
        tvTransactions = findViewById(R.id.tvTransactions);
        tvTxTrend = findViewById(R.id.tvTxTrend);
        tvProducts = findViewById(R.id.tvProducts);
        tvLowStockAlerts = findViewById(R.id.tvLowStockAlerts);
        tvLowStockSubtitle = findViewById(R.id.tvLowStockSubtitle);
        cardLowStock = findViewById(R.id.cardLowStock);

        if (cardLowStock != null) {
            cardLowStock.setOnClickListener(v -> openLowStockSuggestions());
        }

        // Quick Action Cards
        cardActionProducts = findViewById(R.id.cardActionProducts);
        cardActionInventory = findViewById(R.id.cardActionInventory);
        cardActionStaff = findViewById(R.id.cardActionStaff);
        cardActionReports = findViewById(R.id.cardActionReports);
        cardActionTransfers = findViewById(R.id.cardActionTransfers);
        cardActionInsights = findViewById(R.id.cardActionInsights);

        setupQuickActionListeners();

        // Bottom Sections
        containerLowStockList = findViewById(R.id.containerLowStockList);
        containerRecentSales = findViewById(R.id.containerRecentSales);
        tvViewAllTransactions = findViewById(R.id.tvViewAllTransactions);
        tvViewAllLowStock = findViewById(R.id.tvViewAllLowStock);

        if (tvViewAllTransactions != null) {
            tvViewAllTransactions.setOnClickListener(v -> {
                Intent intent = new Intent(this, TransactionHistoryActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }

        if (tvViewAllLowStock != null) {
            tvViewAllLowStock.setOnClickListener(v -> openLowStockSuggestions());
        }

        // Sidebar Navigation Items
        navDashboard = findViewById(R.id.navDashboard);
        navProducts = findViewById(R.id.navProducts);
        navInventory = findViewById(R.id.navInventory);
        navSales = findViewById(R.id.navSales);
        navStaff = findViewById(R.id.navStaff);
        navReports = findViewById(R.id.navReports);
        navTransfers = findViewById(R.id.navTransfers);
        navInsights = findViewById(R.id.navInsights);
        navSubscription = findViewById(R.id.navSubscription);
        navSettings = findViewById(R.id.navSettings);
        navLogout = findViewById(R.id.navLogout);

        setupNavigationListeners();
    }

    private void setupHeaderInfo() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = "Good evening";
        if (hour >= 5 && hour < 12) {
            greeting = "Good morning";
        } else if (hour >= 12 && hour < 17) {
            greeting = "Good afternoon";
        }

        String displayName = (userName != null && !userName.isEmpty()) ? userName : "Admin";
        if (tvAdminGreeting != null) {
            tvAdminGreeting.setText(greeting + ", " + displayName);
        }

        String displayStore = (storeName != null && !storeName.isEmpty()) ? storeName : "Branch " + storeId;
        if (tvHeaderStorePill != null) {
            tvHeaderStorePill.setText(displayStore);
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.US);
        if (tvHeaderDate != null) {
            tvHeaderDate.setText(dateFormat.format(new Date()));
        }

        if (tvAdminProfileName != null) {
            tvAdminProfileName.setText(displayName);
        }

        if (tvAdminAvatarInitials != null) {
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
            tvAdminAvatarInitials.setText(initials);
        }
    }

    private void setupQuickActionListeners() {
        if (cardActionProducts != null) {
            cardActionProducts.setOnClickListener(v -> {
                Intent intent = new Intent(this, ProductsActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }
        if (cardActionInventory != null) {
            cardActionInventory.setOnClickListener(v -> {
                Intent intent = new Intent(this, InventoryActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }
        if (cardActionStaff != null) {
            cardActionStaff.setOnClickListener(v -> {
                Intent intent = new Intent(this, StaffActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }
        if (cardActionReports != null) {
            cardActionReports.setOnClickListener(v -> {
                Intent intent = new Intent(this, ReportsActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }
        if (cardActionTransfers != null) {
            cardActionTransfers.setOnClickListener(v -> {
                Intent intent = new Intent(this, StockTransfersActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }
        if (cardActionInsights != null) {
            cardActionInsights.setOnClickListener(v -> {
                Intent intent = new Intent(this, StockEaseInsightsActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (storeId > 0 && userId > 0 && "store_admin".equals(role)) {
            loadDashboardMetrics();
        }
    }

    private void loadDashboardMetrics() {
        setMetricPlaceholders();

        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest summaryRequest = new JsonObjectRequest(
                Request.Method.GET,
                DASHBOARD_URL,
                null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            int totalProducts = response.optInt("total_products", 0);
                            int lowStock = response.optInt("low_stock", 0);

                            if (tvProducts != null) tvProducts.setText(String.valueOf(totalProducts));
                            if (tvLowStockAlerts != null) tvLowStockAlerts.setText(String.valueOf(lowStock));

                            JSONArray lowStockItems = response.optJSONArray("low_stock_items");
                            renderLowStockPreview(lowStockItems);

                            JSONArray recentTx = response.optJSONArray("recent_transactions");
                            renderRecentSalesPreview(recentTx);

                            // Inbound Stock Transfer Alert
                            JSONObject inbound = response.optJSONObject("inbound_transfer");
                            if (inbound != null && containerInboundTransferAlert != null) {
                                containerInboundTransferAlert.setVisibility(View.VISIBLE);
                                int transferId = inbound.optInt("id", 0);
                                String fromStore = inbound.optString("from_store_name", "Another Store");
                                int totalQty = inbound.optInt("total_quantity", 0);
                                String status = inbound.optString("status", "in_transit");

                                if (tvInboundBadge != null) {
                                    tvInboundBadge.setText("in_transit".equals(status) ? "IN TRANSIT" : "REQUESTED");
                                }
                                if (tvInboundTitle != null) {
                                    tvInboundTitle.setText("Transfer #" + transferId + " from " + fromStore);
                                }
                                if (tvInboundDesc != null) {
                                    tvInboundDesc.setText(totalQty + " units are arriving. Tap to inspect and confirm receipt.");
                                }

                                View.OnClickListener openTransfers = v -> {
                                    Intent intent = new Intent(this, StockTransfersActivity.class);
                                    addIdentityExtras(intent);
                                    startActivity(intent);
                                };

                                containerInboundTransferAlert.setOnClickListener(openTransfers);
                                if (btnViewInboundTransfer != null) {
                                    btnViewInboundTransfer.setOnClickListener(openTransfers);
                                }
                            } else if (containerInboundTransferAlert != null) {
                                containerInboundTransferAlert.setVisibility(View.GONE);
                            }

                        } else {
                            String msg = response.optString("message", "Failed to load metrics");
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Network error loading dashboard", Toast.LENGTH_SHORT).show();
                }
        );

        JsonArrayRequest transactionsRequest = new JsonArrayRequest(
                Request.Method.GET,
                TRANSACTIONS_URL,
                null,
                response -> updateTransactionMetrics(response),
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Network error loading transaction metrics", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(summaryRequest);
        queue.add(transactionsRequest);
    }

    private void renderLowStockPreview(JSONArray items) {
        if (containerLowStockList == null) return;
        containerLowStockList.removeAllViews();

        if (items == null || items.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("All products well-stocked.");
            empty.setTextColor(0xFF64748B);
            empty.setTextSize(12f);
            empty.setPadding(0, 16, 0, 16);
            containerLowStockList.addView(empty);
            return;
        }

        for (int i = 0; i < items.length(); i++) {
            JSONObject obj = items.optJSONObject(i);
            if (obj == null) continue;

            String name = obj.optString("product_name", "Product");
            int stock = obj.optInt("stock", 0);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 10, 0, 10);

            TextView tvName = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvName.setLayoutParams(lp);
            tvName.setText(name);
            tvName.setTextColor(0xFF0F172A);
            tvName.setTextSize(13f);

            TextView tvStock = new TextView(this);
            tvStock.setText(stock + " left");
            tvStock.setTextColor(stock < 5 ? 0xFFEF4444 : 0xFFF59E0B);
            tvStock.setTypeface(null, Typeface.BOLD);
            tvStock.setTextSize(12f);

            row.addView(tvName);
            row.addView(tvStock);
            row.setOnClickListener(v -> openLowStockSuggestions());

            containerLowStockList.addView(row);

            // Divider
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFE2E8F0);
            containerLowStockList.addView(divider);
        }
    }

    private void renderRecentSalesPreview(JSONArray recentTx) {
        if (containerRecentSales == null) return;
        containerRecentSales.removeAllViews();

        if (recentTx == null || recentTx.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No recent sales recorded.");
            empty.setTextColor(0xFF64748B);
            empty.setTextSize(12f);
            empty.setPadding(0, 16, 0, 16);
            containerRecentSales.addView(empty);
            return;
        }

        for (int i = 0; i < recentTx.length(); i++) {
            JSONObject tx = recentTx.optJSONObject(i);
            if (tx == null) continue;

            int id = tx.optInt("id", 0);
            double amount = tx.optDouble("total_amount", 0.0);
            String method = tx.optString("payment_method", "Cash");

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 10, 0, 10);

            TextView tvTxId = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f);
            tvTxId.setLayoutParams(lp);
            tvTxId.setText("#" + id + " • " + method);
            tvTxId.setTextColor(0xFF0F172A);
            tvTxId.setTextSize(13f);

            TextView tvAmt = new TextView(this);
            tvAmt.setText(String.format(Locale.US, "₱%.2f", amount));
            tvAmt.setTextColor(0xFF10B981);
            tvAmt.setTypeface(null, Typeface.BOLD);
            tvAmt.setTextSize(13f);

            row.addView(tvTxId);
            row.addView(tvAmt);

            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, TransactionHistoryActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });

            containerRecentSales.addView(row);

            // Divider
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFE2E8F0);
            containerRecentSales.addView(divider);
        }
    }

    private void updateTransactionMetrics(JSONArray transactions) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        double todaySales = 0.0;

        for (int i = 0; i < transactions.length(); i++) {
            JSONObject transaction = transactions.optJSONObject(i);
            if (transaction == null) continue;

            String transactionDate = transaction.optString("transaction_date", "");
            if (transactionDate.startsWith(today)) {
                todaySales += transaction.optDouble("total_amount", 0.0);
            }
        }

        if (tvTransactions != null) {
            tvTransactions.setText(String.valueOf(transactions.length()));
        }
        if (tvTodaySales != null) {
            tvTodaySales.setText(String.format(Locale.US, "₱%.2f", todaySales));
        }
    }

    private void setMetricPlaceholders() {
        if (tvTodaySales != null) tvTodaySales.setText("—");
        if (tvTransactions != null) tvTransactions.setText("—");
        if (tvProducts != null) tvProducts.setText("—");
        if (tvLowStockAlerts != null) tvLowStockAlerts.setText("—");
    }

    private void setupNavigationListeners() {
        if (navProducts != null) {
            navProducts.setOnClickListener(v -> {
                Intent intent = new Intent(this, ProductsActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }

        if (navInventory != null) {
            navInventory.setOnClickListener(v -> {
                Intent intent = new Intent(this, InventoryActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }

        if (navStaff != null) {
            navStaff.setOnClickListener(v -> {
                Intent intent = new Intent(this, StaffActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }

        if (navSales != null) {
            navSales.setOnClickListener(v -> {
                Intent intent = new Intent(this, TransactionHistoryActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }

        if (navReports != null) {
            navReports.setOnClickListener(v -> {
                Intent intent = new Intent(this, ReportsActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }

        if (navTransfers != null) {
            navTransfers.setOnClickListener(v -> {
                Intent intent = new Intent(this, StockTransfersActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }

        if (navInsights != null) {
            navInsights.setOnClickListener(v -> {
                Intent intent = new Intent(this, StockEaseInsightsActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }

        if (navSubscription != null) {
            navSubscription.setOnClickListener(v -> {
                Intent intent = new Intent(this, SubscriptionActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }

        if (navSettings != null) {
            navSettings.setOnClickListener(v -> {
                Intent intent = new Intent(this, SettingsActivity.class);
                addIdentityExtras(intent);
                startActivity(intent);
            });
        }

        if (navLogout != null) {
            navLogout.setOnClickListener(v -> ApiSession.logout(this));
        }
    }

    private void openLowStockSuggestions() {
        Intent intent = new Intent(this, LowStockActivity.class);
        addIdentityExtras(intent);
        startActivity(intent);
    }

    private void addIdentityExtras(Intent intent) {
        intent.putExtra("store_id", storeId);
        intent.putExtra("user_id", userId);
        intent.putExtra("role", role);
        intent.putExtra("full_name", userName);
        intent.putExtra("store_name", storeName);
        intent.putExtra("username", username);
        intent.putExtra("email", email);
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
