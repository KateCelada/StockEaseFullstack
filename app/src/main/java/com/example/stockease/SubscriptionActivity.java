package com.example.stockease;

import android.os.Bundle;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class SubscriptionActivity extends AppCompatActivity {

    private static final String SUBSCRIPTION_URL = ApiConfig.getUrl("manage_subscription.php");

    private int userId = -1;
    private String role = "";

    private TextView tvSubHeaderBadge;
    private TextView tvCurrentPlanTitle, tvCurrentStatusBadge;
    private TextView tvBillingPeriod, tvExpirationDate, tvPlanAccessSummary;
    private Button btnApplyMonthly, btnApplyYearly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscription);

        userId = getIntent().getIntExtra("user_id", -1);
        role = getIntent().getStringExtra("role");

        if (role == null || role.isEmpty()) {
            role = "store_admin"; // Default fallback for mobile app users
        }

        ImageButton btnSubBack = findViewById(R.id.btnSubBack);
        if (btnSubBack != null) {
            btnSubBack.setOnClickListener(v -> finish());
        }

        tvSubHeaderBadge = findViewById(R.id.tvSubHeaderBadge);
        tvCurrentPlanTitle = findViewById(R.id.tvCurrentPlanTitle);
        tvCurrentStatusBadge = findViewById(R.id.tvCurrentStatusBadge);
        tvBillingPeriod = findViewById(R.id.tvBillingPeriod);
        tvExpirationDate = findViewById(R.id.tvExpirationDate);
        tvPlanAccessSummary = findViewById(R.id.tvPlanAccessSummary);

        btnApplyMonthly = findViewById(R.id.btnApplyMonthly);
        btnApplyYearly = findViewById(R.id.btnApplyYearly);

        if (btnApplyMonthly != null) {
            btnApplyMonthly.setOnClickListener(v -> submitSubscriptionApplication("monthly"));
        }
        if (btnApplyYearly != null) {
            btnApplyYearly.setOnClickListener(v -> submitSubscriptionApplication("yearly"));
        }

        fetchSubscriptionDetails();
    }

    private void fetchSubscriptionDetails() {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                SUBSCRIPTION_URL + "?action=fetch",
                null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONObject sub = response.getJSONObject("subscription");
                            JSONObject pending = response.optJSONObject("pending_request");
                            displaySubscription(sub, pending);
                        } else {
                            Toast.makeText(this, response.optString("message", "Failed to load subscription"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(this, "Malformed subscription response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Network error retrieving subscription", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    private void displaySubscription(JSONObject sub, JSONObject pendingRequest) {
        String plan = sub.optString("plan", "free");
        String period = sub.optString("billing_period", "");
        String status = sub.optString("status", "active");
        boolean isPremium = sub.optBoolean("is_premium", false);
        String expiresAt = sub.optString("expires_at", "");

        if (isPremium) {
            if (tvCurrentPlanTitle != null) tvCurrentPlanTitle.setText("StockEase Premium");
            if (tvSubHeaderBadge != null) {
                tvSubHeaderBadge.setText("PREMIUM ACTIVE");
                tvSubHeaderBadge.setBackgroundResource(R.drawable.bg_badge_emerald);
                tvSubHeaderBadge.setTextColor(0xFF059669);
            }
            if (tvCurrentStatusBadge != null) {
                tvCurrentStatusBadge.setText(status.toUpperCase(Locale.US));
                tvCurrentStatusBadge.setBackgroundResource(R.drawable.bg_badge_emerald);
                tvCurrentStatusBadge.setTextColor(0xFF059669);
            }
            if (tvBillingPeriod != null) {
                tvBillingPeriod.setText("yearly".equalsIgnoreCase(period) ? "Yearly (₱6,990 / yr)" : "Monthly (₱699 / mo)");
            }
            if (tvExpirationDate != null) {
                tvExpirationDate.setText(!expiresAt.isEmpty() ? expiresAt : "Active");
            }
            if (tvPlanAccessSummary != null) {
                tvPlanAccessSummary.setText("Full enterprise access active: Multi-store stock transfers, smart inventory insights, transfer recommendations, and advanced multi-branch comparison.");
            }

            if (btnApplyMonthly != null) {
                if ("monthly".equalsIgnoreCase(period)) {
                    btnApplyMonthly.setText("Current Plan");
                    btnApplyMonthly.setEnabled(false);
                } else {
                    btnApplyMonthly.setText("Switch to Monthly");
                    btnApplyMonthly.setEnabled(true);
                }
            }
            if (btnApplyYearly != null) {
                if ("yearly".equalsIgnoreCase(period)) {
                    btnApplyYearly.setText("Current Plan");
                    btnApplyYearly.setEnabled(false);
                } else {
                    btnApplyYearly.setText("Switch to Yearly");
                    btnApplyYearly.setEnabled(true);
                }
            }
        } else if (pendingRequest != null) {
            // Store Admin has a pending subscription request awaiting Super Admin approval
            String reqPeriod = pendingRequest.optString("billing_period", "monthly");
            String reqDate = pendingRequest.optString("created_at", "");
            double price = pendingRequest.optDouble("price", 699.00);

            if (tvCurrentPlanTitle != null) tvCurrentPlanTitle.setText("Application Pending: Premium (" + reqPeriod.toUpperCase(Locale.US) + ")");
            if (tvSubHeaderBadge != null) {
                tvSubHeaderBadge.setText("PENDING APPROVAL");
                tvSubHeaderBadge.setBackgroundResource(R.drawable.bg_badge_amber);
                tvSubHeaderBadge.setTextColor(0xFFD97706);
            }
            if (tvCurrentStatusBadge != null) {
                tvCurrentStatusBadge.setText("AWAITING APPROVAL");
                tvCurrentStatusBadge.setBackgroundResource(R.drawable.bg_badge_amber);
                tvCurrentStatusBadge.setTextColor(0xFFD97706);
            }
            if (tvBillingPeriod != null) {
                tvBillingPeriod.setText(reqPeriod.toUpperCase(Locale.US) + " (₱" + (int)price + ")");
            }
            if (tvExpirationDate != null) {
                tvExpirationDate.setText("Submitted: " + reqDate);
            }
            if (tvPlanAccessSummary != null) {
                tvPlanAccessSummary.setText("Your application for StockEase Premium is awaiting Super Admin review and approval. Premium features will unlock immediately once approved.");
            }

            if (btnApplyMonthly != null) {
                if ("monthly".equalsIgnoreCase(reqPeriod)) {
                    btnApplyMonthly.setText("Pending Approval");
                    btnApplyMonthly.setEnabled(false);
                } else {
                    btnApplyMonthly.setText("Change to Monthly");
                    btnApplyMonthly.setEnabled(true);
                }
            }
            if (btnApplyYearly != null) {
                if ("yearly".equalsIgnoreCase(reqPeriod)) {
                    btnApplyYearly.setText("Pending Approval");
                    btnApplyYearly.setEnabled(false);
                } else {
                    btnApplyYearly.setText("Change to Yearly");
                    btnApplyYearly.setEnabled(true);
                }
            }
        } else {
            if (tvCurrentPlanTitle != null) tvCurrentPlanTitle.setText("StockEase Free");
            if (tvSubHeaderBadge != null) {
                tvSubHeaderBadge.setText("FREE TIER");
                tvSubHeaderBadge.setBackgroundResource(R.drawable.bg_badge_gray);
                tvSubHeaderBadge.setTextColor(0xFF64748B);
            }
            if (tvCurrentStatusBadge != null) {
                if ("expired".equalsIgnoreCase(status)) {
                    tvCurrentStatusBadge.setText("EXPIRED");
                    tvCurrentStatusBadge.setBackgroundResource(R.drawable.bg_badge_gray);
                    tvCurrentStatusBadge.setTextColor(0xFFDC2626);
                } else {
                    tvCurrentStatusBadge.setText("ACTIVE");
                    tvCurrentStatusBadge.setBackgroundResource(R.drawable.bg_badge_gray);
                    tvCurrentStatusBadge.setTextColor(0xFF334155);
                }
            }
            if (tvBillingPeriod != null) {
                tvBillingPeriod.setText("Standard (Free)");
            }
            if (tvExpirationDate != null) {
                tvExpirationDate.setText(!expiresAt.isEmpty() ? "Expired on: " + expiresAt : "No expiration");
            }
            if (tvPlanAccessSummary != null) {
                tvPlanAccessSummary.setText("Standard operational access: POS checkout, local product and inventory tracking, low-stock monitoring, and sales history.");
            }

            if (btnApplyMonthly != null) {
                btnApplyMonthly.setText("Apply (₱699/mo)");
                btnApplyMonthly.setEnabled(true);
            }
            if (btnApplyYearly != null) {
                btnApplyYearly.setText("Apply (₱6,990/yr)");
                btnApplyYearly.setEnabled(true);
            }
        }
    }

    private void submitSubscriptionApplication(String period) {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JSONObject payload = new JSONObject();
        try {
            payload.put("action", "apply");
            payload.put("billing_period", period != null ? period : "monthly");
        } catch (JSONException ignored) {}

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                SUBSCRIPTION_URL + "?action=apply",
                payload,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            String msg = response.optString("message", "Application submitted successfully!");
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                            fetchSubscriptionDetails();
                        } else {
                            Toast.makeText(this, response.optString("message", "Application failed"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(this, "Response parse error", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Network error submitting application", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }
}
