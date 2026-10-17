package com.example.stockease;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AuditLogsActivity extends AppCompatActivity {

    private static final String AUDIT_LOGS_URL = ApiConfig.getUrl("get_audit_logs.php");

    private int userId = -1;
    private String role = "";
    private String fullName = "";
    private String username = "";
    private String email = "";

    private EditText etAuditSearch;
    private Spinner spinnerAuditActionFilter;
    private Button btnAuditRefresh;
    private Button btnAuditFilterAll, btnAuditFilterToday, btnAuditFilterWeek, btnAuditFilterMonth, btnAuditFilterCustom;
    private String selectedPeriod = "";
    private String customStartDate = "";
    private String customEndDate = "";
    private LinearLayout llAuditLogRows;
    private TextView tvAuditLogsStatus;
    private TextView tvAuditAvatarInitials, tvAuditProfileName;

    /** Server side pagination of the audit table. */
    private static final int PAGE_SIZE = 25;
    private int currentPage = 1;
    private int totalPages = 1;
    private int totalLogs = 0;
    private Button btnAuditPrevPage, btnAuditNextPage;
    private TextView tvAuditPageInfo;
    private final android.os.Handler searchDebounce = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSearch;

    private View navAuditDashboard, navAuditStores, navAuditUsers, navAuditAdmins, navAuditReports, navAuditSettings, navAuditLogout;

    private static class LogEntry {
        final int id;
        final String userName;
        final String role;
        final String action;
        final String target;
        final String storeName;
        final String details;
        final String createdAt;

        LogEntry(int id, String userName, String role, String action, String target, String storeName, String details, String createdAt) {
            this.id = id;
            this.userName = userName;
            this.role = role;
            this.action = action;
            this.target = target;
            this.storeName = storeName;
            this.details = details;
            this.createdAt = createdAt;
        }
    }

    private final List<LogEntry> logsList = new ArrayList<>();
    /** How many entries the table is currently showing (after search filtering). */
    private int renderedCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audit_logs);

        userId = getIntent().getIntExtra("user_id", -1);
        role = getIntent().getStringExtra("role");
        fullName = getIntent().getStringExtra("full_name");
        username = getIntent().getStringExtra("username");
        email = getIntent().getStringExtra("email");

        if (!isAuthorized()) {
            returnToLogin("Audit Logs are restricted to Super Admin accounts.");
            return;
        }

        // Header and Profile
        tvAuditAvatarInitials = findViewById(R.id.tvAuditAvatarInitials);
        tvAuditProfileName = findViewById(R.id.tvAuditProfileName);
        setupProfileInfo();

        // Search & Filters
        etAuditSearch = findViewById(R.id.etAuditSearch);
        spinnerAuditActionFilter = findViewById(R.id.spinnerAuditActionFilter);
        btnAuditRefresh = findViewById(R.id.btnAuditRefresh);
        llAuditLogRows = findViewById(R.id.llAuditLogRows);
        tvAuditLogsStatus = findViewById(R.id.tvAuditLogsStatus);

        btnAuditFilterAll = findViewById(R.id.btnAuditFilterAll);
        btnAuditFilterToday = findViewById(R.id.btnAuditFilterToday);
        btnAuditFilterWeek = findViewById(R.id.btnAuditFilterWeek);
        btnAuditFilterMonth = findViewById(R.id.btnAuditFilterMonth);
        btnAuditFilterCustom = findViewById(R.id.btnAuditFilterCustom);

        btnAuditPrevPage = findViewById(R.id.btnAuditPrevPage);
        btnAuditNextPage = findViewById(R.id.btnAuditNextPage);
        tvAuditPageInfo = findViewById(R.id.tvAuditPageInfo);

        if (btnAuditPrevPage != null) {
            btnAuditPrevPage.setOnClickListener(v -> {
                if (currentPage > 1) {
                    currentPage--;
                    fetchLogs();
                }
            });
        }
        if (btnAuditNextPage != null) {
            btnAuditNextPage.setOnClickListener(v -> {
                if (currentPage < totalPages) {
                    currentPage++;
                    fetchLogs();
                }
            });
        }

        setupFilters();
        setupDateFilters();

        // Sidebar Navigation
        navAuditDashboard = findViewById(R.id.navAuditDashboard);
        navAuditStores = findViewById(R.id.navAuditStores);
        navAuditUsers = findViewById(R.id.navAuditUsers);
        navAuditAdmins = findViewById(R.id.navAuditAdmins);
        navAuditReports = findViewById(R.id.navAuditReports);
        navAuditSettings = findViewById(R.id.navAuditSettings);
        navAuditLogout = findViewById(R.id.navAuditLogout);

        if (navAuditDashboard != null) {
            navAuditDashboard.setOnClickListener(v -> {
                if ("super_admin".equals(role)) {
                    openDestination(SuperAdminDashboardActivity.class);
                } else {
                    openDestination(StoreAdminDashboardActivity.class);
                }
            });
        }
        if (navAuditStores != null) {
            navAuditStores.setOnClickListener(v -> openDestination(StoresActivity.class));
        }
        if (navAuditUsers != null) {
            navAuditUsers.setOnClickListener(v -> openDestination(UsersActivity.class));
        }
        if (navAuditAdmins != null) {
            navAuditAdmins.setOnClickListener(v -> openDestination(StoreAdminsActivity.class));
        }
        if (navAuditReports != null) {
            navAuditReports.setOnClickListener(v -> openStoreSelection(StoresActivity.TARGET_REPORTS));
        }
        if (navAuditSettings != null) {
            navAuditSettings.setOnClickListener(v -> openDestination(SettingsActivity.class));
        }
        if (navAuditLogout != null) {
            navAuditLogout.setOnClickListener(v -> ApiSession.logout(this));
        }

        if (btnAuditRefresh != null) {
            btnAuditRefresh.setOnClickListener(v -> fetchLogs());
        }

        // Collapsible sidebar sections (accordion): ANALYTICS opens by default (current screen)
        SidebarSection.attach(this, R.id.navHeaderOverview, R.id.navArrowOverview, R.id.navSectionOverviewItems, false);
        SidebarSection.attach(this, R.id.navHeaderManagement, R.id.navArrowManagement, R.id.navSectionManagementItems, false);
        SidebarSection.attach(this, R.id.navHeaderAnalytics, R.id.navArrowAnalytics, R.id.navSectionAnalyticsItems, true);
        SidebarSection.attach(this, R.id.navHeaderSystem, R.id.navArrowSystem, R.id.navSectionSystemItems, false);

        // Tapping the profile name or avatar opens the profile screen
        View.OnClickListener openProfile = v -> {
            Intent intent = new Intent(AuditLogsActivity.this, ProfileActivity.class);
            if (getIntent().getExtras() != null) {
                intent.putExtras(getIntent().getExtras());
            }
            ScreenTransitions.open(AuditLogsActivity.this, intent);
        };
        if (tvAuditProfileName != null) {
            tvAuditProfileName.setOnClickListener(openProfile);
        }
        if (tvAuditAvatarInitials != null) {
            tvAuditAvatarInitials.setOnClickListener(openProfile);
        }

        // The filter toolbar and the audit table react to taps as well
        setupCardClicks();

        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));

        fetchLogs();
    }

    /** Makes the filter toolbar and the audit table card respond to taps. */
    private void setupCardClicks() {
        View filtersCard = findViewById(R.id.cardAuditFilters);
        if (filtersCard != null) {
            filtersCard.setOnClickListener(v -> {
                if (etAuditSearch == null) return;
                etAuditSearch.requestFocus();
                etAuditSearch.post(() -> {
                    android.view.inputmethod.InputMethodManager imm =
                            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(etAuditSearch,
                                android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }
                });
            });
        }

        View tableCard = findViewById(R.id.cardAuditTable);
        if (tableCard != null) {
            tableCard.setOnClickListener(v -> {
                String dateFilter = selectedPeriod.isEmpty()
                        ? "All time"
                        : ("custom".equals(selectedPeriod)
                            ? (customStartDate + " - " + customEndDate)
                            : selectedPeriod);
                String actionFilter = (spinnerAuditActionFilter != null
                        && spinnerAuditActionFilter.getSelectedItem() != null)
                        ? spinnerAuditActionFilter.getSelectedItem().toString()
                        : "All Actions";
                String query = etAuditSearch != null
                        ? etAuditSearch.getText().toString().trim()
                        : "";

                CardDetailDialog.show(this, "Audit Logs",
                        "Events shown: " + renderedCount + " of " + totalLogs + " (page " + currentPage + " of " + totalPages + ")",
                        "Date filter: " + dateFilter,
                        "Action filter: " + actionFilter,
                        query.isEmpty() ? "Search: none" : "Search: " + query);
            });
        }
    }

    private void setupProfileInfo() {
        String displayName = (!TextUtils.isEmpty(fullName)) ? fullName : (!TextUtils.isEmpty(username) ? username : "System Admin");
        if (tvAuditProfileName != null) {
            tvAuditProfileName.setText(displayName);
        }

        if (tvAuditAvatarInitials != null) {
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
            tvAuditAvatarInitials.setText(initials);
        }
    }

    private void setupFilters() {
        String[] actions = new String[]{"All Actions", "SYSTEM_LOGIN", "STORE_CONFIG", "PRODUCT_RESTOCK", "STAFF_UPDATE", "PROCESS_SALE"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, actions);
        if (spinnerAuditActionFilter != null) {
            spinnerAuditActionFilter.setAdapter(adapter);
            spinnerAuditActionFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    currentPage = 1;
                    fetchLogs();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        if (etAuditSearch != null) {
            etAuditSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    if (pendingSearch != null) searchDebounce.removeCallbacks(pendingSearch);
                    currentPage = 1;
                    fetchLogs();
                    return true;
                }
                return false;
            });

            etAuditSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    // The API searches every column, so re-query instead of filtering this page only
                    scheduleServerSearch();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void setupDateFilters() {
        if (btnAuditFilterAll != null) {
            btnAuditFilterAll.setOnClickListener(v -> selectDateFilter("", "", ""));
        }
        if (btnAuditFilterToday != null) {
            btnAuditFilterToday.setOnClickListener(v -> selectDateFilter("today", "", ""));
        }
        if (btnAuditFilterWeek != null) {
            btnAuditFilterWeek.setOnClickListener(v -> selectDateFilter("this_week", "", ""));
        }
        if (btnAuditFilterMonth != null) {
            btnAuditFilterMonth.setOnClickListener(v -> selectDateFilter("this_month", "", ""));
        }
        if (btnAuditFilterCustom != null) {
            btnAuditFilterCustom.setOnClickListener(v -> showCustomDatePicker());
        }
        updateDateFilterButtonsUI();
    }

    private void selectDateFilter(String period, String start, String end) {
        selectedPeriod = period;
        customStartDate = start;
        customEndDate = end;
        currentPage = 1;
        updateDateFilterButtonsUI();
        fetchLogs();
    }

    private void updateDateFilterButtonsUI() {
        int activeBg = ContextCompat.getColor(this, R.color.primary);
        int activeText = ContextCompat.getColor(this, R.color.white);
        int inactiveBg = ContextCompat.getColor(this, R.color.card_border);
        int inactiveText = ContextCompat.getColor(this, R.color.slate_600);

        if (btnAuditFilterAll != null) {
            boolean active = selectedPeriod.isEmpty();
            btnAuditFilterAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(active ? activeBg : inactiveBg));
            btnAuditFilterAll.setTextColor(active ? activeText : inactiveText);
        }
        if (btnAuditFilterToday != null) {
            boolean active = "today".equals(selectedPeriod);
            btnAuditFilterToday.setBackgroundTintList(android.content.res.ColorStateList.valueOf(active ? activeBg : inactiveBg));
            btnAuditFilterToday.setTextColor(active ? activeText : inactiveText);
        }
        if (btnAuditFilterWeek != null) {
            boolean active = "this_week".equals(selectedPeriod);
            btnAuditFilterWeek.setBackgroundTintList(android.content.res.ColorStateList.valueOf(active ? activeBg : inactiveBg));
            btnAuditFilterWeek.setTextColor(active ? activeText : inactiveText);
        }
        if (btnAuditFilterMonth != null) {
            boolean active = "this_month".equals(selectedPeriod);
            btnAuditFilterMonth.setBackgroundTintList(android.content.res.ColorStateList.valueOf(active ? activeBg : inactiveBg));
            btnAuditFilterMonth.setTextColor(active ? activeText : inactiveText);
        }
        if (btnAuditFilterCustom != null) {
            boolean active = "custom".equals(selectedPeriod);
            btnAuditFilterCustom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(active ? activeBg : inactiveBg));
            btnAuditFilterCustom.setTextColor(active ? activeText : inactiveText);
            if (active && !customStartDate.isEmpty()) {
                btnAuditFilterCustom.setText(customStartDate + " - " + customEndDate);
            } else {
                btnAuditFilterCustom.setText("Custom");
            }
        }
    }

    private void showCustomDatePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog startDialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String startStr = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            DatePickerDialog endDialog = new DatePickerDialog(this, (view2, year2, month2, dayOfMonth2) -> {
                String endStr = String.format(Locale.US, "%04d-%02d-%02d", year2, month2 + 1, dayOfMonth2);
                selectDateFilter("custom", startStr, endStr);
            }, year, month, dayOfMonth);
            endDialog.setTitle("Select End Date");
            endDialog.show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        startDialog.setTitle("Select Start Date");
        startDialog.show();
    }

    private void fetchLogs() {
        if (tvAuditLogsStatus != null) {
            tvAuditLogsStatus.setVisibility(View.VISIBLE);
            tvAuditLogsStatus.setText("Loading audit logs...");
        }

        String search = (etAuditSearch != null && etAuditSearch.getText() != null) ? etAuditSearch.getText().toString().trim() : "";
        String actionFilter = "";
        if (spinnerAuditActionFilter != null && spinnerAuditActionFilter.getSelectedItemPosition() > 0) {
            actionFilter = spinnerAuditActionFilter.getSelectedItem().toString();
        }

        StringBuilder urlBuilder = new StringBuilder(AUDIT_LOGS_URL);
        try {
            boolean firstParam = true;
            if (!search.isEmpty()) {
                urlBuilder.append("?search=").append(URLEncoder.encode(search, "UTF-8"));
                firstParam = false;
            }
            if (!actionFilter.isEmpty()) {
                urlBuilder.append(firstParam ? "?" : "&").append("action_filter=").append(URLEncoder.encode(actionFilter, "UTF-8"));
                firstParam = false;
            }
            if (!selectedPeriod.isEmpty()) {
                urlBuilder.append(firstParam ? "?" : "&").append("period=").append(URLEncoder.encode(selectedPeriod, "UTF-8"));
                firstParam = false;
                if ("custom".equals(selectedPeriod) && !customStartDate.isEmpty() && !customEndDate.isEmpty()) {
                    urlBuilder.append("&start_date=").append(URLEncoder.encode(customStartDate, "UTF-8"));
                    urlBuilder.append("&end_date=").append(URLEncoder.encode(customEndDate, "UTF-8"));
                }
            }
            urlBuilder.append(firstParam ? "?" : "&").append("page=").append(currentPage);
            urlBuilder.append("&per_page=").append(PAGE_SIZE);
        } catch (UnsupportedEncodingException ignored) {}

        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                urlBuilder.toString(),
                null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONArray logsArray = response.getJSONArray("logs");
                            logsList.clear();
                            for (int i = 0; i < logsArray.length(); i++) {
                                JSONObject log = logsArray.optJSONObject(i);
                                if (log != null) {
                                    logsList.add(new LogEntry(
                                            log.optInt("id", 0),
                                            log.optString("user_name", "Unknown"),
                                            log.optString("role", "staff"),
                                            log.optString("action", "ACTION"),
                                            log.optString("target", "—"),
                                            log.optString("store_name", "Global"),
                                            log.optString("details", "—"),
                                            log.optString("created_at", "—")
                                    ));
                                }
                            }
                            renderLogRows(logsList);

                            JSONObject pagination = response.optJSONObject("pagination");
                            if (pagination != null) {
                                currentPage = Math.max(1, pagination.optInt("page", 1));
                                totalPages = Math.max(1, pagination.optInt("total_pages", 1));
                                totalLogs = pagination.optInt("total", logsList.size());
                            } else {
                                totalPages = 1;
                                totalLogs = logsList.size();
                            }
                            updatePaginationUi();
                        } else {
                            showErrorState(response.optString("message", "Unable to load audit trail"));
                        }
                    } catch (JSONException e) {
                        showErrorState("Malformed audit logs response");
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    showErrorState("Network error while retrieving audit logs");
                }
        );

        queue.add(request);
    }

    /** Refreshes the pagination footer under the audit table. */
    private void updatePaginationUi() {
        if (tvAuditPageInfo != null) {
            int first = logsList.isEmpty() ? 0 : ((currentPage - 1) * PAGE_SIZE) + 1;
            int last = first + logsList.size() - 1;
            tvAuditPageInfo.setText("Page " + currentPage + " of " + totalPages
                    + " • showing " + first + "-" + last + " of " + totalLogs + " events");
        }
        if (btnAuditPrevPage != null) {
            btnAuditPrevPage.setEnabled(currentPage > 1);
        }
        if (btnAuditNextPage != null) {
            btnAuditNextPage.setEnabled(currentPage < totalPages);
        }
    }

    /** Typing in the search box re-queries the server for the first matching page. */
    private void scheduleServerSearch() {
        if (pendingSearch != null) {
            searchDebounce.removeCallbacks(pendingSearch);
        }
        pendingSearch = () -> {
            currentPage = 1;
            fetchLogs();
        };
        searchDebounce.postDelayed(pendingSearch, 400);
    }

    private void renderLogRows(List<LogEntry> list) {
        if (llAuditLogRows == null) return;
        llAuditLogRows.removeAllViews();
        renderedCount = (list == null) ? 0 : list.size();

        if (list.isEmpty()) {
            if (tvAuditLogsStatus != null) {
                tvAuditLogsStatus.setVisibility(View.VISIBLE);
                tvAuditLogsStatus.setText("No audit log records found.");
            }
            return;
        }

        if (tvAuditLogsStatus != null) {
            tvAuditLogsStatus.setVisibility(View.GONE);
        }

        float density = getResources().getDisplayMetrics().density;
        int padH = (int) (14 * density);
        int padV = (int) (12 * density);

        for (LogEntry item : list) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(padH, padV, padH, padV);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(ContextCompat.getColor(this, R.color.white));

            // 1. DATE & TIME (weight 1.4)
            TextView tvTime = new TextView(this);
            LinearLayout.LayoutParams lpTime = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f);
            tvTime.setLayoutParams(lpTime);
            tvTime.setText(item.createdAt);
            tvTime.setTextSize(12f);
            tvTime.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            row.addView(tvTime);

            // 2. USER (weight 1.4)
            TextView tvUser = new TextView(this);
            LinearLayout.LayoutParams lpUser = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f);
            tvUser.setLayoutParams(lpUser);
            tvUser.setText(item.userName);
            tvUser.setTextSize(13f);
            tvUser.setTypeface(tvUser.getTypeface(), Typeface.BOLD);
            tvUser.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvUser.setSingleLine(true);
            tvUser.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(tvUser);

            // 3. ROLE (weight 1.0)
            LinearLayout colRole = new LinearLayout(this);
            LinearLayout.LayoutParams lpRole = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            colRole.setLayoutParams(lpRole);
            TextView tvRole = new TextView(this);
            tvRole.setTextSize(10f);
            tvRole.setTypeface(tvRole.getTypeface(), Typeface.BOLD);
            tvRole.setPadding((int) (6 * density), (int) (2 * density), (int) (6 * density), (int) (2 * density));

            if ("super_admin".equalsIgnoreCase(item.role)) {
                tvRole.setText("Super Admin");
                tvRole.setTextColor(ContextCompat.getColor(this, R.color.primary));
                tvRole.setBackgroundResource(R.drawable.bg_badge_blue);
            } else if ("store_admin".equalsIgnoreCase(item.role)) {
                tvRole.setText("Store Admin");
                tvRole.setTextColor(ContextCompat.getColor(this, R.color.success_strong));
                tvRole.setBackgroundResource(R.drawable.bg_badge_emerald);
            } else {
                tvRole.setText("Staff");
                tvRole.setTextColor(ContextCompat.getColor(this, R.color.slate_600));
                tvRole.setBackgroundResource(R.drawable.bg_badge_gray);
            }
            colRole.addView(tvRole);
            row.addView(colRole);

            // 4. ACTION (weight 1.4)
            TextView tvAction = new TextView(this);
            LinearLayout.LayoutParams lpAction = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f);
            tvAction.setLayoutParams(lpAction);
            tvAction.setText(item.action);
            tvAction.setTextSize(12f);
            tvAction.setTypeface(tvAction.getTypeface(), Typeface.BOLD);
            tvAction.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvAction.setSingleLine(true);
            tvAction.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(tvAction);

            // 5. STORE / SCOPE (weight 1.2)
            TextView tvStore = new TextView(this);
            LinearLayout.LayoutParams lpStore = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f);
            tvStore.setLayoutParams(lpStore);
            tvStore.setText(item.storeName);
            tvStore.setTextSize(12f);
            tvStore.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvStore.setSingleLine(true);
            tvStore.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(tvStore);

            // 6. DETAILS (weight 2.6)
            TextView tvDetails = new TextView(this);
            LinearLayout.LayoutParams lpDetails = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.6f);
            tvDetails.setLayoutParams(lpDetails);
            tvDetails.setText(item.details);
            tvDetails.setTextSize(12f);
            tvDetails.setTextColor(ContextCompat.getColor(this, R.color.slate_700));
            tvDetails.setMaxLines(2);
            tvDetails.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(tvDetails);

            // Tapping a row opens the full audit entry
            final String roleLabel = tvRole.getText().toString();
            row.setOnClickListener(v -> CardDetailDialog.show(this, "Audit Event",
                    "Date & Time: " + item.createdAt,
                    "User: " + item.userName,
                    "Role: " + roleLabel,
                    "Action: " + item.action,
                    "Target: " + item.target,
                    "Store / Scope: " + item.storeName,
                    "Details: " + item.details));
            ViewFeedbackUtil.attach(row);

            llAuditLogRows.addView(row);

            // 1px Subtle Divider
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.card_border));
            llAuditLogRows.addView(divider);
        }
    }

    private void showErrorState(String message) {
        if (tvAuditLogsStatus != null) {
            tvAuditLogsStatus.setVisibility(View.VISIBLE);
            tvAuditLogsStatus.setText(message);
        }
    }

    private boolean isAuthorized() {
        return userId > 0 && ("super_admin".equals(role) || "manager".equals(role) || "store_owner".equals(role));
    }

    private void openDestination(Class<?> destination) {
        Intent intent = new Intent(this, destination);
        intent.putExtra("user_id", userId);
        intent.putExtra("role", role);
        intent.putExtra("full_name", fullName);
        intent.putExtra("username", username);
        intent.putExtra("email", email);
        ScreenTransitions.open(AuditLogsActivity.this, intent);
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
        ScreenTransitions.open(AuditLogsActivity.this, intent);
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ScreenTransitions.open(AuditLogsActivity.this, intent);
        ScreenTransitions.finish(AuditLogsActivity.this);
    }
}
