package com.example.stockease;

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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
    private LinearLayout llAuditLogRows;
    private TextView tvAuditLogsStatus;
    private TextView tvAuditAvatarInitials, tvAuditProfileName;

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

        setupFilters();

        // Sidebar Navigation
        navAuditDashboard = findViewById(R.id.navAuditDashboard);
        navAuditStores = findViewById(R.id.navAuditStores);
        navAuditUsers = findViewById(R.id.navAuditUsers);
        navAuditAdmins = findViewById(R.id.navAuditAdmins);
        navAuditReports = findViewById(R.id.navAuditReports);
        navAuditSettings = findViewById(R.id.navAuditSettings);
        navAuditLogout = findViewById(R.id.navAuditLogout);

        if (navAuditDashboard != null) {
            navAuditDashboard.setOnClickListener(v -> openDestination(SuperAdminDashboardActivity.class));
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

        fetchLogs();
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
                    fetchLogs();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        if (etAuditSearch != null) {
            etAuditSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
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
                    // Filter existing list locally for fast response
                    renderFilteredLogs(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
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
            }
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

    private void renderFilteredLogs(String query) {
        if (TextUtils.isEmpty(query)) {
            renderLogRows(logsList);
            return;
        }

        String lowerQuery = query.toLowerCase(Locale.US);
        List<LogEntry> filtered = new ArrayList<>();
        for (LogEntry item : logsList) {
            if (item.userName.toLowerCase(Locale.US).contains(lowerQuery)
                    || item.action.toLowerCase(Locale.US).contains(lowerQuery)
                    || item.target.toLowerCase(Locale.US).contains(lowerQuery)
                    || item.storeName.toLowerCase(Locale.US).contains(lowerQuery)
                    || item.details.toLowerCase(Locale.US).contains(lowerQuery)) {
                filtered.add(item);
            }
        }
        renderLogRows(filtered);
    }

    private void renderLogRows(List<LogEntry> list) {
        if (llAuditLogRows == null) return;
        llAuditLogRows.removeAllViews();

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
            row.setBackgroundColor(0xFFFFFFFF);

            // 1. DATE & TIME (weight 1.4)
            TextView tvTime = new TextView(this);
            LinearLayout.LayoutParams lpTime = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f);
            tvTime.setLayoutParams(lpTime);
            tvTime.setText(item.createdAt);
            tvTime.setTextSize(12f);
            tvTime.setTextColor(0xFF64748B);
            row.addView(tvTime);

            // 2. USER (weight 1.4)
            TextView tvUser = new TextView(this);
            LinearLayout.LayoutParams lpUser = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f);
            tvUser.setLayoutParams(lpUser);
            tvUser.setText(item.userName);
            tvUser.setTextSize(13f);
            tvUser.setTypeface(null, Typeface.BOLD);
            tvUser.setTextColor(0xFF0F172A);
            tvUser.setSingleLine(true);
            tvUser.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(tvUser);

            // 3. ROLE (weight 1.0)
            LinearLayout colRole = new LinearLayout(this);
            LinearLayout.LayoutParams lpRole = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            colRole.setLayoutParams(lpRole);
            TextView tvRole = new TextView(this);
            tvRole.setTextSize(10f);
            tvRole.setTypeface(null, Typeface.BOLD);
            tvRole.setPadding((int) (6 * density), (int) (2 * density), (int) (6 * density), (int) (2 * density));

            if ("super_admin".equalsIgnoreCase(item.role)) {
                tvRole.setText("Super Admin");
                tvRole.setTextColor(0xFF2563EB);
                tvRole.setBackgroundResource(R.drawable.bg_badge_blue);
            } else if ("store_admin".equalsIgnoreCase(item.role)) {
                tvRole.setText("Store Admin");
                tvRole.setTextColor(0xFF059669);
                tvRole.setBackgroundResource(R.drawable.bg_badge_emerald);
            } else {
                tvRole.setText("Staff");
                tvRole.setTextColor(0xFF475569);
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
            tvAction.setTypeface(null, Typeface.BOLD);
            tvAction.setTextColor(0xFF0F172A);
            tvAction.setSingleLine(true);
            tvAction.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(tvAction);

            // 5. STORE / SCOPE (weight 1.2)
            TextView tvStore = new TextView(this);
            LinearLayout.LayoutParams lpStore = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f);
            tvStore.setLayoutParams(lpStore);
            tvStore.setText(item.storeName);
            tvStore.setTextSize(12f);
            tvStore.setTextColor(0xFF64748B);
            tvStore.setSingleLine(true);
            tvStore.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(tvStore);

            // 6. DETAILS (weight 2.6)
            TextView tvDetails = new TextView(this);
            LinearLayout.LayoutParams lpDetails = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.6f);
            tvDetails.setLayoutParams(lpDetails);
            tvDetails.setText(item.details);
            tvDetails.setTextSize(12f);
            tvDetails.setTextColor(0xFF334155);
            tvDetails.setMaxLines(2);
            tvDetails.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(tvDetails);

            llAuditLogRows.addView(row);

            // 1px Subtle Divider
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFE2E8F0);
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
        return userId > 0 && "super_admin".equals(role);
    }

    private void openDestination(Class<?> destination) {
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
