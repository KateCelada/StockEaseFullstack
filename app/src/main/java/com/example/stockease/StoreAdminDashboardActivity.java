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
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.widget.ScrollView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StoreAdminDashboardActivity extends AppCompatActivity {

    private TextView tvAdminGreeting, tvHeaderSubtitle, tvHeaderStorePill, tvHeaderDate;
    private TextView tvAdminAvatarInitials, tvAdminProfileName;
    private TextView tvTodaySales, tvTodaySalesTrend, tvTransactions, tvTxTrend, tvProducts, tvLowStockAlerts, tvLowStockSubtitle;
    private View cardLowStock;
    private SimpleLineChartView chartStoreEarnings;
    private TextView tvChartWeeklyTotal;
    private TextView btnPeriodToday, btnPeriodThisWeek, btnPeriodThisMonth, btnPeriodCustom;
    private String selectedPeriod = "this_week";
    private JSONArray cachedTodaySoldProducts = new JSONArray();
    private JSONArray cachedAllLowStockItems = new JSONArray();
    private JSONArray cachedTodayHourlyTrend = new JSONArray();
    private View cardActionProducts, cardActionStaff, cardActionReports;
    private View cardActionTransfers, cardActionInsights;
    private TextView tvViewAllTransactions, tvViewAllLowStock;
    private LinearLayout containerLowStockList, containerRecentSales;
    private View navDashboard, navProducts, navStores, navInventory, navSales, navStaff, navReports, navTransfers, navInsights, navSubscription, navSettings, navLogout;

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
    private View containerManagerBranchSection;
    private LinearLayout containerManagerBranchCards;
    private Button btnManagerAddBranch;

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

        if (storeId <= 0 || userId <= 0 || (!"store_admin".equals(role) && !"manager".equals(role))) {
            returnToLogin("This dashboard is available only to Store Admin or Manager accounts with a valid store.");
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

        // Manager Multi-Branch Section
        containerManagerBranchSection = findViewById(R.id.containerManagerBranchSection);
        containerManagerBranchCards = findViewById(R.id.containerManagerBranchCards);
        btnManagerAddBranch = findViewById(R.id.btnManagerAddBranch);

        if ("manager".equals(role)) {
            if (containerManagerBranchSection != null) containerManagerBranchSection.setVisibility(View.VISIBLE);
            if (btnManagerAddBranch != null) {
                btnManagerAddBranch.setOnClickListener(v -> showManagerAddBranchDialog());
            }
            loadManagerBranches();
        } else {
            if (containerManagerBranchSection != null) containerManagerBranchSection.setVisibility(View.GONE);
        }

        // Stat Card Views
        tvTodaySales = findViewById(R.id.tvTodaySales);
        tvTodaySalesTrend = findViewById(R.id.tvTodaySalesTrend);
        tvTransactions = findViewById(R.id.tvTransactions);
        tvTxTrend = findViewById(R.id.tvTxTrend);
        tvProducts = findViewById(R.id.tvProducts);
        tvLowStockAlerts = findViewById(R.id.tvLowStockAlerts);
        tvLowStockSubtitle = findViewById(R.id.tvLowStockSubtitle);
        cardLowStock = findViewById(R.id.cardLowStock);
        View cardTodaySales = findViewById(R.id.cardTodaySales);
        View cardTransactions = findViewById(R.id.cardTransactions);
        View cardTotalProducts = findViewById(R.id.cardTotalProducts);

        if (cardTodaySales != null) {
            cardTodaySales.setOnClickListener(v -> showTodaySalesRankedDialog());
        }

        if (cardTransactions != null) {
            cardTransactions.setOnClickListener(v -> {
                Intent intent = new Intent(this, TransactionHistoryActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }

        if (cardTotalProducts != null) {
            cardTotalProducts.setOnClickListener(v -> {
                Intent intent = new Intent(this, ProductsActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }

        if (cardLowStock != null) {
            cardLowStock.setOnClickListener(v -> showAllLowStockDialog());
        }

        // Earnings Line Chart & Period Filters
        chartStoreEarnings = findViewById(R.id.chartStoreEarnings);
        tvChartWeeklyTotal = findViewById(R.id.tvChartWeeklyTotal);
        btnPeriodToday = findViewById(R.id.btnPeriodToday);
        btnPeriodThisWeek = findViewById(R.id.btnPeriodThisWeek);
        btnPeriodThisMonth = findViewById(R.id.btnPeriodThisMonth);
        btnPeriodCustom = findViewById(R.id.btnPeriodCustom);

        setupPeriodButtons();

        // Quick Action Cards
        cardActionProducts = findViewById(R.id.cardActionProducts);
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
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }

        if (tvViewAllLowStock != null) {
            tvViewAllLowStock.setOnClickListener(v -> openLowStockSuggestions());
        }

        // Sidebar Navigation Items
        navDashboard = findViewById(R.id.navDashboard);
        navProducts = findViewById(R.id.navProducts);
        navStores = findViewById(R.id.navStores);
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

        if (!"manager".equals(role) && !"super_admin".equals(role)) {
            if (btnManagerAddBranch != null) btnManagerAddBranch.setVisibility(View.GONE);
            if (containerManagerBranchSection != null) containerManagerBranchSection.setVisibility(View.GONE);
            if (navStores != null) navStores.setVisibility(View.GONE);
        }

        // Collapsible sidebar sections (accordion): OVERVIEW opens by default
        SidebarSection.attach(this, R.id.navHeaderOverview, R.id.navArrowOverview, R.id.navSectionOverviewItems, true);
        SidebarSection.attach(this, R.id.navHeaderManagement, R.id.navArrowManagement, R.id.navSectionManagementItems, false);
        SidebarSection.attach(this, R.id.navHeaderAnalytics, R.id.navArrowAnalytics, R.id.navSectionAnalyticsItems, false);
        SidebarSection.attach(this, R.id.navHeaderSystem, R.id.navArrowSystem, R.id.navSectionSystemItems, false);

        // Tapping the profile name or avatar opens the profile screen
        View.OnClickListener openProfile = v -> {
            Intent intent = new Intent(StoreAdminDashboardActivity.this, ProfileActivity.class);
            if (getIntent().getExtras() != null) {
                intent.putExtras(getIntent().getExtras());
            }
            ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
        };
        if (tvAdminProfileName != null) {
            tvAdminProfileName.setOnClickListener(openProfile);
        }
        if (tvAdminAvatarInitials != null) {
            tvAdminAvatarInitials.setOnClickListener(openProfile);
        }

        // Every dashboard card responds to a tap: navigation where a matching
        // screen exists, a detail sheet for the rest.
        setupCardClicks();

        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));
    }

    /** Wires the cards that are not already covered by a dedicated listener. */
    private void setupCardClicks() {
        if (containerManagerBranchSection != null) {
            containerManagerBranchSection.setOnClickListener(v -> showBranchesMenuDialog());
        }

        View recentTransactions = findViewById(R.id.cardRecentTransactions);
        if (recentTransactions != null) {
            recentTransactions.setOnClickListener(v -> {
                Intent intent = new Intent(this, TransactionHistoryActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }

        View lowStockPanel = findViewById(R.id.cardLowStockPanel);
        if (lowStockPanel != null) {
            lowStockPanel.setOnClickListener(v -> openLowStockSuggestions());
        }

        View earningsChart = findViewById(R.id.cardEarningsChart);
        if (earningsChart != null) {
            earningsChart.setOnClickListener(v -> {
                TextView tvSubtitle = findViewById(R.id.tvChartEarningsSubtitle);
                TextView tvWeeklyTotal = findViewById(R.id.tvChartWeeklyTotal);
                CardDetailDialog.show(this, "Store Earnings Progress",
                        "Period: " + (tvSubtitle != null ? tvSubtitle.getText() : ""),
                        "Total: " + (tvWeeklyTotal != null ? tvWeeklyTotal.getText() : ""));
            });
        }
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
            tvHeaderStorePill.setText(displayStore + " ▾");
            tvHeaderStorePill.setOnClickListener(v -> showBranchSwitchDialog());
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
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }
        if (cardActionStaff != null) {
            cardActionStaff.setOnClickListener(v -> {
                Intent intent = new Intent(this, StaffActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }
        if (cardActionReports != null) {
            cardActionReports.setOnClickListener(v -> {
                Intent intent = new Intent(this, ReportsActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }
        if (cardActionTransfers != null) {
            cardActionTransfers.setOnClickListener(v -> {
                Intent intent = new Intent(this, StockTransfersActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }
        if (cardActionInsights != null) {
            cardActionInsights.setOnClickListener(v -> {
                Intent intent = new Intent(this, StockEaseInsightsActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (storeId > 0 && userId > 0 && ("store_admin".equals(role) || "manager".equals(role))) {
            loadDashboardMetrics();
        }
    }

    private void showBranchSwitchDialog() {
        RequestQueue queue = ApiSession.getRequestQueue(this);
        String url = ApiConfig.getUrl("manage_stores.php?action=fetch");
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    try {
                        if (response.optBoolean("success", false)) {
                            JSONArray stores = response.optJSONArray("stores");
                            if (stores != null && stores.length() > 1) {
                                String[] storeNames = new String[stores.length()];
                                int[] storeIds = new int[stores.length()];
                                int selectedIndex = 0;

                                for (int i = 0; i < stores.length(); i++) {
                                    JSONObject st = stores.getJSONObject(i);
                                    int id = st.getInt("id");
                                    String name = st.getString("store_name");
                                    storeNames[i] = name;
                                    storeIds[i] = id;
                                    if (id == storeId) {
                                        selectedIndex = i;
                                    }
                                }

                                new androidx.appcompat.app.AlertDialog.Builder(this)
                                        .setTitle("Select Store Branch")
                                        .setSingleChoiceItems(storeNames, selectedIndex, (dialog, which) -> {
                                            storeId = storeIds[which];
                                            storeName = storeNames[which];
                                            if (tvHeaderStorePill != null) {
                                                tvHeaderStorePill.setText(storeName + " ▾");
                                            }
                                            dialog.dismiss();
                                            Toast.makeText(this, "Switched to " + storeName, Toast.LENGTH_SHORT).show();
                                            loadDashboardMetrics();
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                            } else {
                                Toast.makeText(this, "Only 1 branch registered for your account.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> Toast.makeText(this, "Failed to load branch list", Toast.LENGTH_SHORT).show()
        );
        queue.add(req);
    }

    private void loadDashboardMetrics() {
        setMetricPlaceholders();

        RequestQueue queue = ApiSession.getRequestQueue(this);
        String dashUrl = ApiConfig.getUrl("get_store_admin_dashboard.php?store_id=" + storeId);
        JsonObjectRequest summaryRequest = new JsonObjectRequest(
                Request.Method.GET,
                dashUrl,
                null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            int totalProducts = response.optInt("total_products", 0);
                            int lowStock = response.optInt("low_stock", 0);
                            double todaySalesAmt = response.optDouble("today_sales", 0.0);
                            int todayTxCount = response.optInt("today_tx_count", 0);

                            if (tvTodaySales != null) {
                                tvTodaySales.setText(String.format(Locale.US, "₱%.2f", todaySalesAmt));
                            }
                            if (tvTransactions != null && todayTxCount > 0) {
                                tvTransactions.setText(String.valueOf(todayTxCount));
                            }
                            if (tvProducts != null) tvProducts.setText(String.valueOf(totalProducts));
                            if (tvLowStockAlerts != null) tvLowStockAlerts.setText(String.valueOf(lowStock));
                            bindLowStockState(lowStock);

                            cachedTodaySoldProducts = response.optJSONArray("today_sold_products");
                            if (cachedTodaySoldProducts == null) cachedTodaySoldProducts = new JSONArray();

                            cachedAllLowStockItems = response.optJSONArray("all_low_stock_items");
                            if (cachedAllLowStockItems == null) cachedAllLowStockItems = new JSONArray();

                            cachedTodayHourlyTrend = response.optJSONArray("today_sales_trend");
                            if (cachedTodayHourlyTrend == null) cachedTodayHourlyTrend = new JSONArray();

                            JSONArray lowStockItems = response.optJSONArray("low_stock_items");
                            renderLowStockPreview(lowStockItems);

                            JSONArray recentTx = response.optJSONArray("recent_transactions");
                            renderRecentSalesPreview(recentTx);

                            // Load Period Chart
                            loadPeriodEarningsData(selectedPeriod);

                            // 7-Day Sales Trend Line Chart
                            JSONArray trendArray = response.optJSONArray("daily_sales_trend");
                            if (trendArray != null && chartStoreEarnings != null) {
                                java.util.List<Float> points = new java.util.ArrayList<>();
                                java.util.List<String> labels = new java.util.ArrayList<>();
                                double weeklySum = 0;
                                for (int i = 0; i < trendArray.length(); i++) {
                                    JSONObject dayObj = trendArray.optJSONObject(i);
                                    if (dayObj != null) {
                                        float amount = (float) dayObj.optDouble("daily_sales", 0.0);
                                        String label = dayObj.optString("label", "");
                                        points.add(amount);
                                        labels.add(label);
                                        weeklySum += amount;
                                    }
                                }
                                chartStoreEarnings.setData(points, labels);
                                if (tvChartWeeklyTotal != null) {
                                    tvChartWeeklyTotal.setText(String.format(Locale.US, "₱%.2f", weeklySum));
                                }
                            }

                            // Real trend lines, derived from the same daily_sales_trend the chart uses.
                            bindSalesTrend(response.optJSONArray("daily_sales_trend"));
                            bindAverageTicket(todaySalesAmt, todayTxCount);

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
                                    ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
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

        String txUrl = ApiConfig.getUrl("get_transactions.php?store_id=" + storeId);
        JsonArrayRequest transactionsRequest = new JsonArrayRequest(
                Request.Method.GET,
                txUrl,
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

    /**
     * Real day-over-day delta, derived from daily_sales_trend (last entry = today,
     * the one before it = yesterday). Colour is earned by the data: up is green,
     * down is red, flat is muted. Replaces the old hardcoded "+12% vs yesterday".
     */
    private void bindSalesTrend(JSONArray dailyTrend) {
        if (tvTodaySalesTrend == null) return;
        if (dailyTrend == null || dailyTrend.length() < 2) {
            tvTodaySalesTrend.setVisibility(View.GONE);
            return;
        }

        JSONObject today = dailyTrend.optJSONObject(dailyTrend.length() - 1);
        JSONObject yesterday = dailyTrend.optJSONObject(dailyTrend.length() - 2);
        double todaySales = today == null ? 0.0 : today.optDouble("daily_sales", 0.0);
        double yesterdaySales = yesterday == null ? 0.0 : yesterday.optDouble("daily_sales", 0.0);

        if (yesterdaySales <= 0.0) {
            // No baseline to compare against — don't invent a percentage.
            if (todaySales > 0.0) {
                tvTodaySalesTrend.setText(R.string.trend_first_sales);
                tvTodaySalesTrend.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
                tvTodaySalesTrend.setVisibility(View.VISIBLE);
            } else {
                tvTodaySalesTrend.setVisibility(View.GONE);
            }
            return;
        }

        double pct = ((todaySales - yesterdaySales) / yesterdaySales) * 100.0;
        String rounded = String.format(Locale.US, "%+.0f%%", pct);
        tvTodaySalesTrend.setText(getString(R.string.trend_vs_yesterday, rounded));
        if (pct > 0.5) {
            tvTodaySalesTrend.setTextColor(ContextCompat.getColor(this, R.color.success));
        } else if (pct < -0.5) {
            tvTodaySalesTrend.setTextColor(ContextCompat.getColor(this, R.color.danger));
        } else {
            tvTodaySalesTrend.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        }
        tvTodaySalesTrend.setVisibility(View.VISIBLE);
    }

    /** Average ticket size — real, derived from today's sales and transaction count. */
    private void bindAverageTicket(double todaySalesAmt, int todayTxCount) {
        if (tvTxTrend == null) return;
        if (todayTxCount <= 0 || todaySalesAmt <= 0.0) {
            tvTxTrend.setVisibility(View.GONE);
            return;
        }
        double avg = todaySalesAmt / todayTxCount;
        tvTxTrend.setText(getString(R.string.trend_avg_ticket, String.format(Locale.US, "₱%.2f", avg)));
        tvTxTrend.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        tvTxTrend.setVisibility(View.VISIBLE);
    }

    /** Low-stock colour is earned: quiet at zero, red only when something needs attention. */
    private void bindLowStockState(int lowStock) {
        if (tvLowStockAlerts != null) {
            tvLowStockAlerts.setTextColor(ContextCompat.getColor(this,
                    lowStock > 0 ? R.color.danger : R.color.text_primary));
        }
        if (tvLowStockSubtitle != null) {
            tvLowStockSubtitle.setText(lowStock > 0
                    ? R.string.low_stock_attention
                    : R.string.low_stock_all_good);
            tvLowStockSubtitle.setTextColor(ContextCompat.getColor(this,
                    lowStock > 0 ? R.color.danger : R.color.text_muted));
        }
    }

    private void renderLowStockPreview(JSONArray items) {
        if (containerLowStockList == null) return;
        containerLowStockList.removeAllViews();

        if (items == null || items.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("All products well-stocked.");
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
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
            tvName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvName.setTextSize(13f);

            TextView tvStock = new TextView(this);
            tvStock.setText(stock + " left");
            tvStock.setTextColor(stock < 5 ? ContextCompat.getColor(this, R.color.danger) : ContextCompat.getColor(this, R.color.warning));
            tvStock.setTypeface(tvStock.getTypeface(), Typeface.BOLD);
            tvStock.setTextSize(12f);

            row.addView(tvName);
            row.addView(tvStock);
            row.setOnClickListener(v -> openLowStockSuggestions());

            containerLowStockList.addView(row);

            // Divider
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.card_border));
            containerLowStockList.addView(divider);
        }
    }

    private void renderRecentSalesPreview(JSONArray recentTx) {
        if (containerRecentSales == null) return;
        containerRecentSales.removeAllViews();

        if (recentTx == null || recentTx.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No recent sales recorded.");
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
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
            tvTxId.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvTxId.setTextSize(13f);

            TextView tvAmt = new TextView(this);
            tvAmt.setText(String.format(Locale.US, "₱%.2f", amount));
            tvAmt.setTextColor(ContextCompat.getColor(this, R.color.success));
            tvAmt.setTypeface(tvAmt.getTypeface(), Typeface.BOLD);
            tvAmt.setTextSize(13f);

            row.addView(tvTxId);
            row.addView(tvAmt);

            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, TransactionHistoryActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });

            containerRecentSales.addView(row);

            // Divider
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.card_border));
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
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }

        if (navStores != null) {
            navStores.setOnClickListener(v -> {
                if ("manager".equals(role)) {
                    showBranchesMenuDialog();
                } else if ("super_admin".equals(role)) {
                    Intent intent = new Intent(this, StoresActivity.class);
                    addIdentityExtras(intent);
                    ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
                } else {
                    Toast.makeText(this, "Branch management is restricted to Store Managers.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (navInventory != null) {
            navInventory.setOnClickListener(v -> {
                Intent intent = new Intent(this, InventoryActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }

        if (navStaff != null) {
            navStaff.setOnClickListener(v -> {
                Intent intent = new Intent(this, StaffActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }

        if (navSales != null) {
            navSales.setOnClickListener(v -> {
                Intent intent = new Intent(this, TransactionHistoryActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }

        if (navReports != null) {
            navReports.setOnClickListener(v -> {
                Intent intent = new Intent(this, ReportsActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }

        if (navTransfers != null) {
            navTransfers.setOnClickListener(v -> {
                Intent intent = new Intent(this, StockTransfersActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }

        if (navInsights != null) {
            navInsights.setOnClickListener(v -> {
                Intent intent = new Intent(this, StockEaseInsightsActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }

        if (navSubscription != null) {
            navSubscription.setOnClickListener(v -> {
                Intent intent = new Intent(this, SubscriptionActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }

        if (navSettings != null) {
            navSettings.setOnClickListener(v -> {
                Intent intent = new Intent(this, SettingsActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
            });
        }

        if (navLogout != null) {
            navLogout.setOnClickListener(v -> ApiSession.logout(this));
        }
    }

    private void setupPeriodButtons() {
        if (btnPeriodToday != null) {
            btnPeriodToday.setOnClickListener(v -> setPeriod("today"));
        }
        if (btnPeriodThisWeek != null) {
            btnPeriodThisWeek.setOnClickListener(v -> setPeriod("this_week"));
        }
        if (btnPeriodThisMonth != null) {
            btnPeriodThisMonth.setOnClickListener(v -> setPeriod("this_month"));
        }
        if (btnPeriodCustom != null) {
            btnPeriodCustom.setOnClickListener(v -> showCustomDatePickerDialog());
        }
    }

    private void setPeriod(String period) {
        selectedPeriod = period;
        updatePeriodButtonUI();
        loadPeriodEarningsData(period);
    }

    private void updatePeriodButtonUI() {
        int activeBg = R.drawable.bg_btn_primary;
        int normalBg = R.drawable.bg_pill_header;
        int activeText = ContextCompat.getColor(this, R.color.white);
        int normalText = ContextCompat.getColor(this, R.color.text_primary);

        if (btnPeriodToday != null) {
            boolean active = "today".equals(selectedPeriod);
            btnPeriodToday.setBackground(ContextCompat.getDrawable(this, active ? activeBg : normalBg));
            btnPeriodToday.setTextColor(active ? activeText : normalText);
        }
        if (btnPeriodThisWeek != null) {
            boolean active = "this_week".equals(selectedPeriod);
            btnPeriodThisWeek.setBackground(ContextCompat.getDrawable(this, active ? activeBg : normalBg));
            btnPeriodThisWeek.setTextColor(active ? activeText : normalText);
        }
        if (btnPeriodThisMonth != null) {
            boolean active = "this_month".equals(selectedPeriod);
            btnPeriodThisMonth.setBackground(ContextCompat.getDrawable(this, active ? activeBg : normalBg));
            btnPeriodThisMonth.setTextColor(active ? activeText : normalText);
        }
        if (btnPeriodCustom != null) {
            boolean active = "custom".equals(selectedPeriod);
            btnPeriodCustom.setBackground(ContextCompat.getDrawable(this, active ? activeBg : normalBg));
            btnPeriodCustom.setTextColor(active ? activeText : normalText);
        }
    }

    private void loadPeriodEarningsData(String period) {
        String url = ApiConfig.getUrl("get_business_progress.php?store_id=" + storeId + "&period=" + period);
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONObject progress = response.optJSONObject("progress");
                            if (progress != null) {
                                JSONObject summary = progress.optJSONObject("summary");
                                if (summary != null && tvChartWeeklyTotal != null) {
                                    double totalSales = summary.optDouble("total_sales", 0.0);
                                    tvChartWeeklyTotal.setText(String.format(Locale.US, "₱%.2f", totalSales));
                                }

                                JSONArray chartPoints = progress.optJSONArray("chart_points");
                                if (chartPoints != null && chartStoreEarnings != null) {
                                    List<Float> points = new ArrayList<>();
                                    List<String> labels = new ArrayList<>();
                                    for (int i = 0; i < chartPoints.length(); i++) {
                                        JSONObject pt = chartPoints.optJSONObject(i);
                                        if (pt != null) {
                                            points.add((float) pt.optDouble("sales", 0.0));
                                            labels.add(pt.optString("label", ""));
                                        }
                                    }
                                    chartStoreEarnings.setData(points, labels);
                                }
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    // Keep existing chart data on failure
                }
        );
        ApiSession.getRequestQueue(this).add(req);
    }

    private void showCustomDatePickerDialog() {
        Calendar cal = Calendar.getInstance();
        new android.app.DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String startDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            selectedPeriod = "custom";
            updatePeriodButtonUI();
            String url = ApiConfig.getUrl("get_business_progress.php?store_id=" + storeId + "&period=custom&start_date=" + startDate + "&end_date=" + startDate);
            JsonObjectRequest req = new JsonObjectRequest(
                    Request.Method.GET, url, null,
                    response -> {
                        try {
                            if (response.getBoolean("success")) {
                                JSONObject progress = response.optJSONObject("progress");
                                if (progress != null) {
                                    JSONObject summary = progress.optJSONObject("summary");
                                    if (summary != null && tvChartWeeklyTotal != null) {
                                        double totalSales = summary.optDouble("total_sales", 0.0);
                                        tvChartWeeklyTotal.setText(String.format(Locale.US, "₱%.2f", totalSales));
                                    }
                                    JSONArray chartPoints = progress.optJSONArray("chart_points");
                                    if (chartPoints != null && chartStoreEarnings != null) {
                                        List<Float> points = new ArrayList<>();
                                        List<String> labels = new ArrayList<>();
                                        for (int i = 0; i < chartPoints.length(); i++) {
                                            JSONObject pt = chartPoints.optJSONObject(i);
                                            if (pt != null) {
                                                points.add((float) pt.optDouble("sales", 0.0));
                                                labels.add(pt.optString("label", ""));
                                            }
                                        }
                                        chartStoreEarnings.setData(points, labels);
                                    }
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    },
                    null
            );
            ApiSession.getRequestQueue(this).add(req);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTodaySalesRankedDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Today's Sold Products (Most to Least Purchased)");

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 24, 32, 24);

        // Hourly Mini Line Chart preview
        if (cachedTodayHourlyTrend != null && cachedTodayHourlyTrend.length() > 0) {
            TextView tvHourlyTitle = new TextView(this);
            tvHourlyTitle.setText("Intraday Sales Trajectory (Hourly)");
            tvHourlyTitle.setTextSize(12f);
            tvHourlyTitle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvHourlyTitle.setTypeface(tvHourlyTitle.getTypeface(), Typeface.BOLD);
            container.addView(tvHourlyTitle);

            SimpleLineChartView hourlyChart = new SimpleLineChartView(this);
            hourlyChart.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 200));
            hourlyChart.setBackgroundColor(ContextCompat.getColor(this, R.color.app_background));

            List<Float> hPoints = new ArrayList<>();
            List<String> hLabels = new ArrayList<>();
            for (int i = 0; i < cachedTodayHourlyTrend.length(); i++) {
                JSONObject hObj = cachedTodayHourlyTrend.optJSONObject(i);
                if (hObj != null) {
                    hPoints.add((float) hObj.optDouble("hourly_sales", 0.0));
                    hLabels.add(hObj.optString("label", ""));
                }
            }
            hourlyChart.setData(hPoints, hLabels);
            container.addView(hourlyChart);

            View div = new View(this);
            div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
            div.setBackgroundColor(ContextCompat.getColor(this, R.color.card_border));
            div.setPadding(0, 16, 0, 16);
            container.addView(div);
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout itemsList = new LinearLayout(this);
        itemsList.setOrientation(LinearLayout.VERTICAL);

        if (cachedTodaySoldProducts == null || cachedTodaySoldProducts.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No products sold today yet.");
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            empty.setPadding(0, 24, 0, 24);
            itemsList.addView(empty);
        } else {
            for (int i = 0; i < cachedTodaySoldProducts.length(); i++) {
                JSONObject prod = cachedTodaySoldProducts.optJSONObject(i);
                if (prod == null) continue;

                String name = prod.optString("product_name", "Product");
                String category = prod.optString("category_name", "General");
                int qty = prod.optInt("total_sold_qty", 0);
                double earned = prod.optDouble("total_earned", 0.0);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, 14, 0, 14);

                TextView tvRank = new TextView(this);
                tvRank.setText("#" + (i + 1) + " ");
                tvRank.setTextColor(ContextCompat.getColor(this, R.color.primary));
                tvRank.setTextSize(14f);
                tvRank.setTypeface(tvRank.getTypeface(), Typeface.BOLD);

                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvName = new TextView(this);
                tvName.setText(name);
                tvName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                tvName.setTextSize(13f);
                tvName.setTypeface(tvName.getTypeface(), Typeface.BOLD);

                TextView tvCat = new TextView(this);
                tvCat.setText(category + " • " + qty + " units sold");
                tvCat.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                tvCat.setTextSize(11f);

                info.addView(tvName);
                info.addView(tvCat);

                TextView tvEarn = new TextView(this);
                tvEarn.setText(String.format(Locale.US, "₱%.2f", earned));
                tvEarn.setTextColor(ContextCompat.getColor(this, R.color.success));
                tvEarn.setTextSize(13f);
                tvEarn.setTypeface(tvEarn.getTypeface(), Typeface.BOLD);

                row.addView(tvRank);
                row.addView(info);
                row.addView(tvEarn);
                itemsList.addView(row);

                View div = new View(this);
                div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                div.setBackgroundColor(ContextCompat.getColor(this, R.color.card_border));
                itemsList.addView(div);
            }
        }

        scroll.addView(itemsList);
        container.addView(scroll);
        builder.setView(container);
        builder.setPositiveButton("Close", null);
        builder.show();
    }

    private void showAllLowStockDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Low Stock Alert (Items with Stock < 10)");

        ScrollView scroll = new ScrollView(this);
        LinearLayout itemsList = new LinearLayout(this);
        itemsList.setOrientation(LinearLayout.VERTICAL);
        itemsList.setPadding(32, 24, 32, 24);

        if (cachedAllLowStockItems == null || cachedAllLowStockItems.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("All items in store are currently well-stocked.");
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            empty.setPadding(0, 24, 0, 24);
            itemsList.addView(empty);
        } else {
            for (int i = 0; i < cachedAllLowStockItems.length(); i++) {
                JSONObject item = cachedAllLowStockItems.optJSONObject(i);
                if (item == null) continue;

                String name = item.optString("product_name", "Item");
                int stock = item.optInt("stock", 0);
                double price = item.optDouble("price", 0.0);

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, 14, 0, 14);

                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvName = new TextView(this);
                tvName.setText(name);
                tvName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                tvName.setTextSize(13f);
                tvName.setTypeface(tvName.getTypeface(), Typeface.BOLD);

                TextView tvPrice = new TextView(this);
                tvPrice.setText(String.format(Locale.US, "Unit Price: ₱%.2f", price));
                tvPrice.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                tvPrice.setTextSize(11f);

                info.addView(tvName);
                info.addView(tvPrice);

                TextView tvStock = new TextView(this);
                tvStock.setText(stock + " left");
                tvStock.setTextColor(stock < 5 ? ContextCompat.getColor(this, R.color.danger) : ContextCompat.getColor(this, R.color.warning));
                tvStock.setTextSize(13f);
                tvStock.setTypeface(tvStock.getTypeface(), Typeface.BOLD);

                row.addView(info);
                row.addView(tvStock);
                itemsList.addView(row);

                View div = new View(this);
                div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                div.setBackgroundColor(ContextCompat.getColor(this, R.color.card_border));
                itemsList.addView(div);
            }
        }

        scroll.addView(itemsList);
        builder.setView(scroll);
        builder.setPositiveButton("Manage Products", (dialog, which) -> {
            Intent intent = new Intent(this, ProductsActivity.class);
            addIdentityExtras(intent);
            ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void loadManagerBranches() {
        if (containerManagerBranchCards == null) return;
        String url = ApiConfig.getUrl("get_store_owners.php");
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    try {
                        if (response.optBoolean("success", false)) {
                            JSONArray owners = response.optJSONArray("owners");
                            renderManagerBranchPortfolios(owners);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                },
                null
        );
        ApiSession.getRequestQueue(this).add(req);
    }

    private void renderManagerBranchPortfolios(JSONArray owners) {
        if (containerManagerBranchCards == null) return;
        containerManagerBranchCards.removeAllViews();

        if (owners == null || owners.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No branches found. Tap '+ Add Branch' to create your first store branch.");
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            empty.setTextSize(12f);
            empty.setPadding(0, 12, 0, 12);
            containerManagerBranchCards.addView(empty);
            return;
        }

        for (int i = 0; i < owners.length(); i++) {
            JSONObject owner = owners.optJSONObject(i);
            if (owner == null) continue;

            String storeNameStr = owner.optString("store_name", "Store Portfolio");
            String ownerNameStr = owner.optString("owner_name", userName);
            String desc = owner.optString("description", "");
            double overallSales = owner.optDouble("overall_sales", 0.0);
            double overallToday = owner.optDouble("overall_today_sales", 0.0);
            JSONArray branches = owner.optJSONArray("branches");
            JSONArray overallTrend = owner.optJSONArray("sales_progress");

            if (branches == null || branches.length() == 0) continue;

            for (int b = 0; b < branches.length(); b++) {
                JSONObject branch = branches.optJSONObject(b);
                if (branch == null) continue;

                int bId = branch.optInt("id", 0);
                String bName = branch.optString("store_name", "Branch");
                String bLoc = branch.optString("location", "—");

                // Compact Branch Card: Only displays the name of the branch
                LinearLayout branchCard = new LinearLayout(this);
                branchCard.setOrientation(LinearLayout.HORIZONTAL);
                branchCard.setGravity(Gravity.CENTER_VERTICAL);
                branchCard.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_card_white));
                branchCard.setPadding(28, 22, 28, 22);
                LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                bLp.setMargins(0, 0, 0, 14);
                branchCard.setLayoutParams(bLp);
                branchCard.setClickable(true);
                branchCard.setFocusable(true);

                LinearLayout colName = new LinearLayout(this);
                colName.setOrientation(LinearLayout.VERTICAL);
                colName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvBranchName = new TextView(this);
                tvBranchName.setText(bName);
                tvBranchName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                tvBranchName.setTextSize(16f);
                tvBranchName.setTypeface(tvBranchName.getTypeface(), Typeface.BOLD);

                TextView tvBranchLoc = new TextView(this);
                tvBranchLoc.setText(bLoc);
                tvBranchLoc.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                tvBranchLoc.setTextSize(12f);
                tvBranchLoc.setPadding(0, 2, 0, 0);

                colName.addView(tvBranchName);
                colName.addView(tvBranchLoc);

                TextView tvArrow = new TextView(this);
                tvArrow.setText("View Progress ▸");
                tvArrow.setTextColor(ContextCompat.getColor(this, R.color.primary));
                tvArrow.setTextSize(12f);
                tvArrow.setTypeface(tvArrow.getTypeface(), Typeface.BOLD);
                tvArrow.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_badge_blue));
                tvArrow.setPadding(16, 6, 16, 6);

                branchCard.addView(colName);
                branchCard.addView(tvArrow);

                branchCard.setOnClickListener(v -> {
                    Intent intent = new Intent(this, BusinessProgressActivity.class);
                    intent.putExtra(BusinessProgressActivity.EXTRA_MODE, "branch");
                    intent.putExtra(BusinessProgressActivity.EXTRA_STORE_ID, bId);
                    intent.putExtra(BusinessProgressActivity.EXTRA_TITLE, bName);
                    intent.putExtra(BusinessProgressActivity.EXTRA_SUBTITLE, "Location: " + bLoc);
                    intent.putExtra("user_id", userId);
                    intent.putExtra("role", role);
                    ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
                });

                containerManagerBranchCards.addView(branchCard);
            }
        }
    }

    private void showBranchesMenuDialog() {
        String[] options = {"Add New Branch", "Create & Assign Store Admins", "View Branches List"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Branches Management")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showManagerAddBranchDialog();
                    } else if (which == 1) {
                        Intent intent = new Intent(this, StoreAdminsActivity.class);
                        addIdentityExtras(intent);
                        ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
                    } else if (which == 2) {
                        if (containerManagerBranchSection != null) {
                            containerManagerBranchSection.setVisibility(View.VISIBLE);
                            ScrollView sv = findViewById(R.id.scrollDashboardContent);
                            if (sv != null) sv.smoothScrollTo(0, containerManagerBranchSection.getTop());
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showManagerContributionDialog(String storeName, JSONArray branches) {
        if (branches == null || branches.length() == 0) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < branches.length(); i++) {
            JSONObject b = branches.optJSONObject(i);
            if (b == null) continue;
            String name = b.optString("store_name", "Branch");
            double s = b.optDouble("total_sales", 0.0);
            double p = b.optDouble("contribution_pct", 0.0);
            sb.append("• ").append(name).append("\n")
              .append("   Contribution: ").append(p).append("%")
              .append(" (₱").append(String.format(Locale.US, "%.2f", s)).append(")\n\n");
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Branch Contribution Breakdown")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }

    private void showManagerBranchInspectDialog(JSONObject branch) {
        if (branch == null) return;
        int bId = branch.optInt("id", 0);
        String bName = branch.optString("store_name", "Branch");
        String bLoc = branch.optString("location", "—");
        double bToday = branch.optDouble("today_sales", 0.0);
        double bTotal = branch.optDouble("total_sales", 0.0);
        JSONArray trend = branch.optJSONArray("sales_progress");
        JSONArray topProducts = branch.optJSONArray("top_products");
        JSONArray branchUsers = branch.optJSONArray("branch_users");

        ScrollView sv = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(36, 24, 36, 24);

        TextView tvH = new TextView(this);
        tvH.setText(bName + " (" + bLoc + ")");
        tvH.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        tvH.setTextSize(15f);
        tvH.setTypeface(tvH.getTypeface(), Typeface.BOLD);
        layout.addView(tvH);

        TextView tvMetrics = new TextView(this);
        tvMetrics.setText(String.format(Locale.US, "Today: ₱%.2f  |  Total Sales: ₱%.2f", bToday, bTotal));
        tvMetrics.setTextColor(ContextCompat.getColor(this, R.color.primary));
        tvMetrics.setTextSize(12f);
        tvMetrics.setPadding(0, 4, 0, 10);
        layout.addView(tvMetrics);

        // Branch Line Chart
        TextView tvChartLbl = new TextView(this);
        tvChartLbl.setText("Branch Sales Progress Line Chart (tap for top products):");
        tvChartLbl.setTextColor(ContextCompat.getColor(this, R.color.slate_700));
        tvChartLbl.setTextSize(12f);
        tvChartLbl.setTypeface(tvChartLbl.getTypeface(), Typeface.BOLD);
        layout.addView(tvChartLbl);

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
            lbls.add("Mon"); lbls.add("Wed"); lbls.add("Fri");
        }
        chart.setData(pts, lbls);
        chart.setClickable(true);
        chart.setFocusable(true);
        chart.setOnClickListener(v -> showManagerTopProductsDialog(bName, topProducts));
        layout.addView(chart);

        Button btnTop = new Button(this);
        btnTop.setText("View Top Purchased Products & Profit");
        btnTop.setTextSize(11f);
        btnTop.setTextColor(ContextCompat.getColor(this, R.color.success_strong));
        btnTop.setBackgroundColor(ContextCompat.getColor(this, R.color.success_bg));
        btnTop.setOnClickListener(v -> showManagerTopProductsDialog(bName, topProducts));
        LinearLayout.LayoutParams btnTLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnTLp.setMargins(0, 6, 0, 14);
        btnTop.setLayoutParams(btnTLp);
        layout.addView(btnTop);

        // Branch Users & Roles
        TextView tvUsersH = new TextView(this);
        tvUsersH.setText("Branch Staff & Roles (tap user for details):");
        tvUsersH.setTextColor(ContextCompat.getColor(this, R.color.slate_700));
        tvUsersH.setTextSize(12f);
        tvUsersH.setTypeface(tvUsersH.getTypeface(), Typeface.BOLD);
        layout.addView(tvUsersH);

        if (branchUsers != null && branchUsers.length() > 0) {
            for (int u = 0; u < branchUsers.length(); u++) {
                JSONObject userObj = branchUsers.optJSONObject(u);
                if (userObj == null) continue;

                String fn = userObj.optString("full_name", "User");
                String un = userObj.optString("username", "user");
                String rl = userObj.optString("role_display", userObj.optString("role", "Staff"));

                LinearLayout uCard = new LinearLayout(this);
                uCard.setOrientation(LinearLayout.VERTICAL);
                uCard.setBackgroundColor(ContextCompat.getColor(this, R.color.app_background));
                uCard.setPadding(16, 10, 16, 10);
                LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                uLp.setMargins(0, 6, 0, 0);
                uCard.setLayoutParams(uLp);

                TextView tvUN = new TextView(this);
                tvUN.setText(fn + " (@" + un + ")");
                tvUN.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                tvUN.setTextSize(12f);
                tvUN.setTypeface(tvUN.getTypeface(), Typeface.BOLD);

                TextView tvUR = new TextView(this);
                tvUR.setText("Role: " + rl);
                tvUR.setTextColor(ContextCompat.getColor(this, R.color.primary));
                tvUR.setTextSize(11f);

                uCard.addView(tvUN);
                uCard.addView(tvUR);
                uCard.setClickable(true);
                uCard.setFocusable(true);
                uCard.setOnClickListener(v -> showManagerUserDetailsDialog(userObj));
                layout.addView(uCard);
            }
        }

        sv.addView(layout);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Branch Real-time Details")
                .setView(sv)
                .setPositiveButton("Switch to this Branch", (dialog, which) -> {
                    storeId = bId;
                    storeName = bName;
                    if (tvHeaderStorePill != null) tvHeaderStorePill.setText(storeName + " ▾");
                    Toast.makeText(this, "Switched to " + storeName, Toast.LENGTH_SHORT).show();
                    loadDashboardMetrics();
                    loadManagerBranches();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showManagerTopProductsDialog(String branchName, JSONArray topProducts) {
        if (topProducts == null || topProducts.length() == 0) {
            Toast.makeText(this, "No product sales recorded yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < topProducts.length(); i++) {
            JSONObject p = topProducts.optJSONObject(i);
            if (p == null) continue;
            String pName = p.optString("product_name", "Product");
            int sold = p.optInt("quantity_sold", 0);
            double gross = p.optDouble("gross_earnings", 0.0);
            double net = p.optDouble("net_profit", 0.0);
            int stock = p.optInt("stock_on_hand", 0);

            sb.append("• ").append(pName).append("\n")
              .append("   Sold: ").append(sold).append(" units | On-Hand: ").append(stock).append("\n")
              .append("   Gross: ₱").append(String.format(Locale.US, "%.2f", gross))
              .append(" | Net Profit: ₱").append(String.format(Locale.US, "%.2f", net)).append("\n\n");
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Top Products (" + branchName + ")")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }

    private void showManagerUserDetailsDialog(JSONObject userObj) {
        if (userObj == null) return;
        String fn = userObj.optString("full_name", "User");
        String un = userObj.optString("username", "username");
        String em = userObj.optString("email", "No email provided");
        String rl = userObj.optString("role_display", userObj.optString("role", "Staff"));
        String lastTx = userObj.optString("last_transaction_at", "No transactions on duty yet");

        String msg = "Full Name: " + fn + "\n"
                   + "Username: @" + un + "\n"
                   + "Role: " + rl + "\n"
                   + "Email: " + em + "\n"
                   + "Cashier Duty Status: " + (lastTx.equals("null") ? "None" : lastTx);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("User Profile & Role")
                .setMessage(msg)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showManagerAddBranchDialog() {
        android.widget.EditText etName = new android.widget.EditText(this);
        etName.setHint("Branch Name (e.g., Downtown Store)");
        android.widget.EditText etLoc = new android.widget.EditText(this);
        etLoc.setHint("Branch Location (e.g., Main Street, City)");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 16);
        layout.addView(etName);
        layout.addView(etLoc);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Add New Store Branch")
                .setMessage("As a business manager, you can add new retail branches to your store portfolio.")
                .setView(layout)
                .setPositiveButton("Create Branch", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String loc = etLoc.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Branch name is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createBranchApi(name, loc);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createBranchApi(String name, String loc) {
        String url = ApiConfig.getUrl("manage_stores.php?action=add");
        JSONObject body = new JSONObject();
        try {
            body.put("store_name", name);
            body.put("location", loc);
            body.put("owner_id", userId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.POST, url, body,
                response -> {
                    boolean ok = response.optBoolean("success", false);
                    String msg = response.optString("message", "Branch created successfully!");
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    if (ok) {
                        loadManagerBranches();
                    }
                },
                error -> Toast.makeText(this, "Failed to create branch", Toast.LENGTH_SHORT).show()
        );
        ApiSession.getRequestQueue(this).add(req);
    }

    private void openLowStockSuggestions() {
        Intent intent = new Intent(this, LowStockActivity.class);
        addIdentityExtras(intent);
        ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
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
        ScreenTransitions.open(StoreAdminDashboardActivity.this, intent);
        ScreenTransitions.finish(StoreAdminDashboardActivity.this);
    }
}
