package com.example.stockease;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;

import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Dashboard for Inventory staff (inventory_checker). The "Inventory & Products"
 * and "Transfers" management actions live here for the staff tier, together
 * with the mandatory Start/End shift control.
 */
public class InventoryDashboardActivity extends AppCompatActivity {

    private static final String SHIFTS_URL = ApiConfig.getUrl("manage_shifts.php");

    private int storeId = -1;
    private int userId = -1;
    private String role = "";
    private String fullName = "";
    private String storeName = "";

    private TextView tvInvWelcome, tvInvBranchPill, tvInvAvatarInitials;
    private TextView tvInvShiftStatusBadge, tvInvShiftStartedAt;
    private MaterialButton btnInvShiftToggle;
    private boolean shiftActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_dashboard);

        storeId = getIntent().getIntExtra("store_id", -1);
        userId = getIntent().getIntExtra("user_id", -1);
        role = getIntent().getStringExtra("role");
        fullName = getIntent().getStringExtra("full_name");
        storeName = getIntent().getStringExtra("store_name");

        if (storeId <= 0 || userId <= 0 || !"inventory_checker".equals(role)) {
            Toast.makeText(this, "This dashboard is available only to Inventory staff accounts with a valid branch.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            ScreenTransitions.open(InventoryDashboardActivity.this, intent);
            ScreenTransitions.finish(InventoryDashboardActivity.this);
            return;
        }

        tvInvWelcome = findViewById(R.id.tvInvWelcome);
        tvInvBranchPill = findViewById(R.id.tvInvBranchPill);
        tvInvAvatarInitials = findViewById(R.id.tvInvAvatarInitials);
        tvInvShiftStatusBadge = findViewById(R.id.tvInvShiftStatusBadge);
        tvInvShiftStartedAt = findViewById(R.id.tvInvShiftStartedAt);
        btnInvShiftToggle = findViewById(R.id.btnInvShiftToggle);

        String displayName = (fullName != null && !fullName.trim().isEmpty()) ? fullName : "Inventory Staff";
        if (tvInvWelcome != null) tvInvWelcome.setText("Welcome, " + displayName);
        if (tvInvBranchPill != null) {
            tvInvBranchPill.setText((storeName != null && !storeName.trim().isEmpty()) ? storeName : ("Branch " + storeId));
        }
        if (tvInvAvatarInitials != null) {
            String initials = "IN";
            if (displayName.trim().length() >= 2) {
                initials = displayName.trim().substring(0, 2).toUpperCase(Locale.US);
            }
            tvInvAvatarInitials.setText(initials);
        }

        if (btnInvShiftToggle != null) {
            btnInvShiftToggle.setOnClickListener(v -> toggleShift());
        }

        // Sidebar navigation
        View navInvProducts = findViewById(R.id.navInvProducts);
        View navInvTransfers = findViewById(R.id.navInvTransfers);
        View navInvShift = findViewById(R.id.navInvShift);
        View navInvLogout = findViewById(R.id.navInvLogout);

        if (navInvProducts != null) navInvProducts.setOnClickListener(v -> openProducts());
        if (navInvTransfers != null) navInvTransfers.setOnClickListener(v -> openTransfers());
        if (navInvShift != null) navInvShift.setOnClickListener(v -> showShiftDetailsDialog());
        if (navInvLogout != null) navInvLogout.setOnClickListener(v -> ApiSession.logout(this));

        View cardInvProducts = findViewById(R.id.cardInvProducts);
        View cardInvTransfers = findViewById(R.id.cardInvTransfers);
        if (cardInvProducts != null) cardInvProducts.setOnClickListener(v -> openProducts());
        if (cardInvTransfers != null) cardInvTransfers.setOnClickListener(v -> openTransfers());

        // Profile opens when the avatar is tapped
        if (tvInvAvatarInitials != null) {
            tvInvAvatarInitials.setOnClickListener(v -> {
                Intent intent = new Intent(InventoryDashboardActivity.this, ProfileActivity.class);
                addIdentityExtras(intent);
                ScreenTransitions.open(InventoryDashboardActivity.this, intent);
            });
        }

        // Accordion sections: OVERVIEW opens by default
        SidebarSection.attach(this, R.id.navHeaderOverview, R.id.navArrowOverview, R.id.navSectionOverviewItems, true);
        SidebarSection.attach(this, R.id.navHeaderManagement, R.id.navArrowManagement, R.id.navSectionManagementItems, false);

        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));

        fetchShiftStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchShiftStatus();
    }

    private void addIdentityExtras(Intent intent) {
        intent.putExtra("store_id", storeId);
        intent.putExtra("user_id", userId);
        intent.putExtra("role", role);
        intent.putExtra("full_name", fullName);
        intent.putExtra("store_name", storeName);
    }

    private void openProducts() {
        Intent intent = new Intent(this, ProductsActivity.class);
        addIdentityExtras(intent);
        ScreenTransitions.open(InventoryDashboardActivity.this, intent);
    }

    private void openTransfers() {
        Intent intent = new Intent(this, StockTransfersActivity.class);
        addIdentityExtras(intent);
        ScreenTransitions.open(InventoryDashboardActivity.this, intent);
    }

    /** Loads the current shift state and updates the shift control card. */
    private void fetchShiftStatus() {
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                SHIFTS_URL + "?action=current",
                null,
                response -> {
                    try {
                        shiftActive = response.optBoolean("active", false);
                        JSONObject shift = response.optJSONObject("shift");
                        updateShiftUi(shift);
                    } catch (Exception ignored) {}
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                }
        );
        queue.add(request);
    }

    private void updateShiftUi(JSONObject shift) {
        if (tvInvShiftStatusBadge != null) {
            if (shiftActive) {
                tvInvShiftStatusBadge.setText("ON DUTY");
                tvInvShiftStatusBadge.setBackgroundResource(R.drawable.bg_badge_emerald);
                tvInvShiftStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.success_strong));
            } else {
                tvInvShiftStatusBadge.setText("OFF DUTY");
                tvInvShiftStatusBadge.setBackgroundResource(R.drawable.bg_badge_gray);
                tvInvShiftStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.slate_700));
            }
        }
        if (tvInvShiftStartedAt != null) {
            String startedAt = shift != null ? shift.optString("started_at", "") : "";
            tvInvShiftStartedAt.setText(shiftActive && !startedAt.isEmpty()
                    ? "Started at " + startedAt
                    : "No active shift");
        }
        if (btnInvShiftToggle != null) {
            btnInvShiftToggle.setText(shiftActive ? "End Shift" : "Start Shift");
        }
    }

    private void toggleShift() {
        if (btnInvShiftToggle != null) btnInvShiftToggle.setEnabled(false);

        RequestQueue queue = ApiSession.getRequestQueue(this);
        String action = shiftActive ? "end" : "start";

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                SHIFTS_URL + "?action=" + action,
                new JSONObject(),
                response -> {
                    if (btnInvShiftToggle != null) btnInvShiftToggle.setEnabled(true);
                    try {
                        boolean ok = response.optBoolean("success", false);
                        String msg = response.optString("message", "");
                        if (!msg.isEmpty()) {
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        }
                        if (ok) {
                            shiftActive = "start".equals(action);
                            updateShiftUi(response.optJSONObject("shift"));
                        }
                    } catch (Exception ignored) {}
                },
                error -> {
                    if (btnInvShiftToggle != null) btnInvShiftToggle.setEnabled(true);
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Unable to update shift status", Toast.LENGTH_SHORT).show();
                }
        );
        queue.add(request);
    }

    private void showShiftDetailsDialog() {
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                SHIFTS_URL + "?action=summary",
                null,
                response -> {
                    try {
                        shiftActive = response.optBoolean("active", false);
                        JSONObject shift = response.optJSONObject("shift");
                        JSONObject summary = response.optJSONObject("summary");

                        String startedAt = shift != null ? shift.optString("started_at", "—") : "—";
                        String endedAt = shift != null ? shift.optString("ended_at", "") : "";
                        int txCount = summary != null ? summary.optInt("transaction_count", 0) : 0;
                        double totalSales = summary != null ? summary.optDouble("total_sales", 0) : 0.0;

                        String message = "Status: " + (shiftActive ? "On duty" : "Off duty")
                                + "\nShift start: " + startedAt
                                + (endedAt.isEmpty() ? "" : "\nShift end: " + endedAt)
                                + "\nTransactions handled: " + txCount
                                + "\nTotal sales: " + String.format(Locale.US, "₱%.2f", totalSales);

                        updateShiftUi(shift);

                        new AlertDialog.Builder(InventoryDashboardActivity.this)
                                .setTitle("My Shift")
                                .setMessage(message)
                                .setPositiveButton("Close", null)
                                .show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Unable to load shift summary", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Unable to load shift summary", Toast.LENGTH_SHORT).show();
                }
        );
        queue.add(request);
    }
}
