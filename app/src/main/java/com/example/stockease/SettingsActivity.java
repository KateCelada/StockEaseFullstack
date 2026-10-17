package com.example.stockease;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.pm.PackageInfoCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SettingsActivity extends AppCompatActivity {

    private int userId = -1;
    private String role = "";
    private String fullName = "";

    private static final String USERS_URL = ApiConfig.getUrl("get_users.php");
    private static final String STORES_URL = ApiConfig.getUrl("manage_stores.php?action=fetch");
    private static final String SUBSCRIPTION_URL = ApiConfig.getUrl("manage_subscription.php?action=fetch");
    private static final String UPDATE_PASSWORD_URL = ApiConfig.getUrl("update_password.php");
    private static final String PROFILE_URL = ApiConfig.getUrl("get_user_profile.php");

    private TextView tvSettingsName, tvSettingsUsername, tvSettingsEmail, tvSettingsRole, tvSettingsStatus;
    private TextView tvApplicationName, tvApplicationVersion, tvSettingsTotalStores, tvSettingsTotalUsers, tvSettingsScope;
    private TextView tvSettingsPlan;
    private Button btnSettingsManageSubscription;
    private Button btnSettingsChangePassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        SidebarNav.attach(this, "settings");

        userId = getIntent().getIntExtra("user_id", -1);
        role = getIntent().getStringExtra("role");
        fullName = getIntent().getStringExtra("full_name");

        if (userId <= 0 || (!"super_admin".equals(role) && !"store_admin".equals(role) && !"manager".equals(role) && !"store_owner".equals(role))) {
            returnToLogin("Settings is available only to Administrator and Manager accounts.");
            return;
        }

        tvSettingsName = findViewById(R.id.tvSettingsName);
        tvSettingsUsername = findViewById(R.id.tvSettingsUsername);
        tvSettingsEmail = findViewById(R.id.tvSettingsEmail);
        tvSettingsRole = findViewById(R.id.tvSettingsRole);
        tvSettingsStatus = findViewById(R.id.tvSettingsStatus);

        tvApplicationName = findViewById(R.id.tvApplicationName);
        tvApplicationVersion = findViewById(R.id.tvApplicationVersion);
        tvSettingsTotalStores = findViewById(R.id.tvSettingsTotalStores);
        tvSettingsTotalUsers = findViewById(R.id.tvSettingsTotalUsers);
        tvSettingsScope = findViewById(R.id.tvSettingsScope);

        tvSettingsPlan = findViewById(R.id.tvSettingsPlan);
        btnSettingsManageSubscription = findViewById(R.id.btnSettingsManageSubscription);
        btnSettingsChangePassword = findViewById(R.id.btnSettingsChangePassword);

        if (btnSettingsChangePassword != null) {
            btnSettingsChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        }

        Button btnSettingsBack = findViewById(R.id.btnSettingsBack);

        tvSettingsName.setText(fullName == null || fullName.trim().isEmpty()
                ? ("super_admin".equals(role) ? "Super Administrator" : "Store Administrator")
                : fullName);

        if ("super_admin".equals(role)) {
            tvSettingsRole.setText("Super Admin (Full Access)");
            if (tvSettingsScope != null) {
                tvSettingsScope.setText("Full Platform Administration (All Stores)");
            }
        } else {
            int storeId = getIntent().getIntExtra("store_id", 0);
            String storeName = getIntent().getStringExtra("store_name");
            tvSettingsRole.setText("Store Admin (" + (storeName != null ? storeName : "Branch " + storeId) + ")");
            if (tvSettingsScope != null) {
                tvSettingsScope.setText("Branch Operations & Staff Management");
            }
        }

        if (tvSettingsStatus != null) tvSettingsStatus.setText("Active");

        String usernameExtra = getIntent().getStringExtra("username");
        String emailExtra = getIntent().getStringExtra("email");
        if (usernameExtra != null && !usernameExtra.isEmpty() && tvSettingsUsername != null) {
            tvSettingsUsername.setText(usernameExtra.startsWith("@") ? usernameExtra : "@" + usernameExtra);
        }
        if (emailExtra != null && !emailExtra.isEmpty() && tvSettingsEmail != null) {
            tvSettingsEmail.setText(emailExtra);
        }

        loadApplicationInformation(tvApplicationName, tvApplicationVersion);

        // Fetch live user profile details (email, username, role, scope)
        loadUserProfile();

        if ("super_admin".equals(role)) {
            loadSystemPlatformMetrics();
        } else {
            if (tvSettingsTotalStores != null) tvSettingsTotalStores.setText("1 (Assigned)");
            if (tvSettingsTotalUsers != null) tvSettingsTotalUsers.setText("—");
        }

        if (btnSettingsManageSubscription != null) {
            btnSettingsManageSubscription.setOnClickListener(v -> {
                Intent intent = new Intent(this, SubscriptionActivity.class);
                intent.putExtra("user_id", userId);
                intent.putExtra("role", role);
                intent.putExtra("full_name", fullName);
                intent.putExtra("store_id", getIntent().getIntExtra("store_id", -1));
                intent.putExtra("store_name", getIntent().getStringExtra("store_name"));
                ScreenTransitions.open(SettingsActivity.this, intent);
            });
        }

        btnSettingsBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSubscriptionPlan();
    }

    private void loadSubscriptionPlan() {
        if (tvSettingsPlan == null) return;

        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest subRequest = new JsonObjectRequest(
                Request.Method.GET, SUBSCRIPTION_URL, null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONObject sub = response.getJSONObject("subscription");
                            String displayPlan = sub.optString("display_plan", "StockEase Free");
                            String status = sub.optString("status", "active");
                            tvSettingsPlan.setText(displayPlan + " (" + status.toUpperCase() + ")");
                        }
                    } catch (JSONException ignored) {}
                },
                error -> {}
        );
        queue.add(subRequest);
    }

    private void loadApplicationInformation(TextView appNameView, TextView versionView) {
        CharSequence applicationLabel = getApplicationInfo().loadLabel(getPackageManager());
        if (appNameView != null) appNameView.setText(applicationLabel);

        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            long versionCode = PackageInfoCompat.getLongVersionCode(packageInfo);
            if (versionView != null) {
                versionView.setText("Version " + packageInfo.versionName + " (Build " + versionCode + ")");
            }
        } catch (PackageManager.NameNotFoundException e) {
            if (versionView != null) versionView.setText("Version 1.0.0");
        }
    }

    private void loadUserProfile() {
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                PROFILE_URL,
                null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONObject u = response.getJSONObject("user");
                            String name = u.optString("full_name", fullName);
                            String uname = u.optString("username", "");
                            String email = u.optString("email", "");
                            String urole = u.optString("role", role);
                            String store = u.optString("store_name", "");
                            boolean active = u.optBoolean("is_active", true);

                            if (tvSettingsName != null && !name.isEmpty()) {
                                tvSettingsName.setText(name);
                            }
                            if (tvSettingsUsername != null && !uname.isEmpty()) {
                                tvSettingsUsername.setText(uname.startsWith("@") ? uname : "@" + uname);
                            }
                            if (tvSettingsEmail != null) {
                                tvSettingsEmail.setText(!email.isEmpty() ? email : "No email registered");
                            }
                            if (tvSettingsStatus != null) {
                                tvSettingsStatus.setText(active ? "Active" : "Inactive");
                                tvSettingsStatus.setTextColor(active ? ContextCompat.getColor(this, R.color.profit) : ContextCompat.getColor(this, R.color.danger_strong));
                            }
                            if ("store_admin".equals(urole)) {
                                if (tvSettingsRole != null) {
                                    tvSettingsRole.setText("Store Admin (" + (!store.isEmpty() ? store : "Assigned Branch") + ")");
                                }
                                if (tvSettingsScope != null) {
                                    tvSettingsScope.setText("Branch Operations: " + (!store.isEmpty() ? store : "Single Branch"));
                                }
                            } else if ("super_admin".equals(urole)) {
                                if (tvSettingsRole != null) {
                                    tvSettingsRole.setText("Super Admin (Full Access)");
                                }
                                if (tvSettingsScope != null) {
                                    tvSettingsScope.setText("Full Platform Administration (All Stores)");
                                }
                            }
                        }
                    } catch (JSONException ignored) {}
                },
                error -> ApiSession.handleAuthError(this, error)
        );
        queue.add(request);
    }

    private void loadSystemPlatformMetrics() {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        // 1. Fetch Users count
        JsonObjectRequest usersRequest = new JsonObjectRequest(
                Request.Method.GET, USERS_URL, null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONArray userArray = response.getJSONArray("users");
                            if (tvSettingsTotalUsers != null) {
                                tvSettingsTotalUsers.setText(String.valueOf(userArray.length()));
                            }
                        }
                    } catch (JSONException ignored) {}
                },
                error -> ApiSession.handleAuthError(this, error)
        );

        // 2. Fetch Stores count
        JsonObjectRequest storesRequest = new JsonObjectRequest(
                Request.Method.GET, STORES_URL, null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONArray storesArray = response.getJSONArray("stores");
                            if (tvSettingsTotalStores != null) {
                                tvSettingsTotalStores.setText(String.valueOf(storesArray.length()));
                            }
                        }
                    } catch (JSONException ignored) {}
                },
                error -> ApiSession.handleAuthError(this, error)
        );

        queue.add(usersRequest);
        queue.add(storesRequest);
    }

    private void logout() {
        ApiSession.logout(this);
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        logout();
    }

    private void showChangePasswordDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_change_password, null);

        EditText etCurrent = dialogView.findViewById(R.id.etCurrentPassword);
        EditText etNew = dialogView.findViewById(R.id.etNewPassword);
        EditText etConfirm = dialogView.findViewById(R.id.etConfirmPassword);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelPassword);
        Button btnSubmit = dialogView.findViewById(R.id.btnSubmitPassword);

        // Live strength meter (same verdict as the API policy)
        PasswordStrength.bind(dialogView.findViewById(R.id.pwStrengthMeter), etNew);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {
            String currentPass = etCurrent.getText().toString().trim();
            String newPass = etNew.getText().toString().trim();
            String confirmPass = etConfirm.getText().toString().trim();

            if (currentPass.isEmpty() || newPass.isEmpty()) {
                Toast.makeText(this, "Please enter your current and new password.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Same rule the API enforces (8+ chars, a letter and a number)
            if (!PasswordStrength.isAcceptable(newPass)) {
                Toast.makeText(this, PasswordStrength.requirementText(), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPass.equals(confirmPass)) {
                Toast.makeText(this, "New passwords do not match.", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSubmit.setEnabled(false);
            btnSubmit.setText("Updating...");

            JSONObject payload = new JSONObject();
            try {
                payload.put("current_password", currentPass);
                payload.put("new_password", newPass);
                payload.put("confirm_password", confirmPass);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST, UPDATE_PASSWORD_URL, payload,
                    response -> {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("Update Password");
                        boolean success = response.optBoolean("success", false);
                        String msg = response.optString("message", "Password updated successfully.");
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        if (success) {
                            dialog.dismiss();
                        }
                    },
                    error -> {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("Update Password");
                        if (!ApiSession.handleAuthError(this, error)) {
                            String errorMsg = "Failed to update password.";
                            if (error.networkResponse != null && error.networkResponse.data != null) {
                                try {
                                    String resp = new String(error.networkResponse.data, java.nio.charset.StandardCharsets.UTF_8);
                                    JSONObject errObj = new JSONObject(resp);
                                    errorMsg = errObj.optString("message", errorMsg);
                                } catch (Exception ignored) {}
                            }
                            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
                        }
                    }
            );

            ApiSession.getRequestQueue(this).add(request);
        });

        dialog.show();
    }
}
