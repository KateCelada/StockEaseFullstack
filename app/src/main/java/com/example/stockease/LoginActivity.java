package com.example.stockease;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword;
    private TextInputLayout tilUsername, tilPassword;
    private Button btnLogin;
    private String loginLabel;

    private static final String LOGIN_URL = ApiConfig.getUrl("login.php");
    private static final String UPDATE_PASSWORD_URL = ApiConfig.getUrl("update_password.php");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Bind Views
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        tilUsername = findViewById(R.id.tilUsername);
        tilPassword = findViewById(R.id.tilPassword);
        btnLogin = findViewById(R.id.btnLogin);
        loginLabel = getString(R.string.login_action);

        // Login Button Click Listener
        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> performLogin());
        }
    }

    /** Inline field error: shows under the field instead of the legacy popup. */
    private void showFieldError(TextInputLayout layout, EditText field, int msgRes) {
        if (layout != null) {
            layout.setError(getString(msgRes));
        } else if (field != null) {
            field.setError(getString(msgRes));
        }
        if (field != null) field.requestFocus();
    }

    private void clearFieldError(TextInputLayout layout, EditText field) {
        if (layout != null) layout.setError(null);
        if (field != null) field.setError(null);
    }

    private void setSigningIn(boolean inFlight) {
        if (btnLogin == null) return;
        btnLogin.setEnabled(!inFlight);
        btnLogin.setText(inFlight ? getString(R.string.action_signing_in) : loginLabel);
    }

    private void performLogin() {
        if (etUsername == null || etPassword == null) return;

        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        clearFieldError(tilUsername, etUsername);
        clearFieldError(tilPassword, etPassword);

        if (TextUtils.isEmpty(username)) {
            showFieldError(tilUsername, etUsername, R.string.err_username_required);
            return;
        }

        if (TextUtils.isEmpty(password)) {
            showFieldError(tilPassword, etPassword, R.string.err_password_required);
            return;
        }

        setSigningIn(true);

        RequestQueue queue = ApiSession.getRequestQueue(this);

        // Prepare JSON Payload
        JSONObject postData = new JSONObject();
        try {
            postData.put("username", username);
            postData.put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST,
                LOGIN_URL,
                postData,
                response -> {
                    setSigningIn(false);
                    try {
                        boolean success = response.getBoolean("success");
                        String message = response.optString("message", "Login response received");

                        if (success) {
                            JSONObject userObject = response.optJSONObject("user");
                            if (userObject == null) {
                                Toast.makeText(LoginActivity.this, "Login response is missing user details", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            int userId = userObject.optInt("id", -1);
                            int storeId = userObject.isNull("store_id")
                                    ? -1
                                    : userObject.optInt("store_id", -1);
                            String role = userObject.optString("role", "");
                            String fullName = userObject.optString("full_name", "User");
                            String authUsername = userObject.optString("username", username);
                            String email = userObject.optString("email", "");
                            String storeName = userObject.optString("store_name", "");

                            if (userId <= 0) {
                                Toast.makeText(LoginActivity.this, "Login response contains an invalid user ID", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            boolean mustChangePassword = response.optBoolean("must_change_password", false)
                                    || userObject.optBoolean("must_change_password", false);

                            if (mustChangePassword) {
                                showMandatoryPasswordResetDialog(userId, role, fullName, storeId, authUsername, email, storeName, password);
                                return;
                            }

                            proceedToDashboard(userId, role, fullName, storeId, authUsername, email, storeName);

                        } else {
                            // Credentials rejected: show it under the field, not as a toast
                            if (tilPassword != null) {
                                tilPassword.setError(message);
                                etPassword.requestFocus();
                            } else {
                                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(LoginActivity.this, "Invalid server response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    setSigningIn(false);
                    String detail = "Connection failed";
                    if (error.networkResponse != null) {
                        detail = "HTTP Status: " + error.networkResponse.statusCode;
                    } else if (error.getMessage() != null) {
                        detail = error.getMessage();
                    }
                    Log.e("LoginActivityError", detail);
                    Toast.makeText(LoginActivity.this, "Login error: " + detail, Toast.LENGTH_LONG).show();
                }
        );

        queue.add(jsonObjectRequest);
    }

    private void showMandatoryPasswordResetDialog(int userId, String role, String fullName, int storeId,
                                                  String username, String email, String storeName, String currentPassword) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_change_password, null);

        EditText etCurrent = dialogView.findViewById(R.id.etCurrentPassword);
        EditText etNew = dialogView.findViewById(R.id.etNewPassword);
        EditText etConfirm = dialogView.findViewById(R.id.etConfirmPassword);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelPassword);
        Button btnSubmit = dialogView.findViewById(R.id.btnSubmitPassword);

        // Pre-fill current password from login attempt for convenient mandatory reset
        if (etCurrent != null && currentPassword != null) {
            etCurrent.setText(currentPassword);
        }

        // Live strength meter (same verdict as the API policy)
        PasswordStrength.bind(dialogView.findViewById(R.id.pwStrengthMeter), etNew);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("First-Time Login Security")
                .setMessage("Your administrator requires you to set a new personal password before accessing the system.")
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (btnCancel != null) {
            btnCancel.setText("Sign Out");
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnSubmit != null) {
            btnSubmit.setText("Save & Continue");
            btnSubmit.setOnClickListener(v -> {
                String curPass = etCurrent != null ? etCurrent.getText().toString().trim() : "";
                String newPass = etNew != null ? etNew.getText().toString().trim() : "";
                String confPass = etConfirm != null ? etConfirm.getText().toString().trim() : "";

                if (curPass.isEmpty() || newPass.isEmpty()) {
                    Toast.makeText(this, "Please provide current and new passwords.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!PasswordStrength.isAcceptable(newPass)) {
                    Toast.makeText(this, PasswordStrength.requirementText(), Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!newPass.equals(confPass)) {
                    Toast.makeText(this, "New passwords do not match.", Toast.LENGTH_SHORT).show();
                    return;
                }

                btnSubmit.setEnabled(false);
                btnSubmit.setText("Saving...");

                JSONObject payload = new JSONObject();
                try {
                    payload.put("user_id", userId);
                    payload.put("username", username);
                    payload.put("current_password", curPass);
                    payload.put("new_password", newPass);
                    payload.put("confirm_password", confPass);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                JsonObjectRequest req = new JsonObjectRequest(
                        Request.Method.POST, UPDATE_PASSWORD_URL, payload,
                        res -> {
                            btnSubmit.setEnabled(true);
                            btnSubmit.setText("Save & Continue");
                            boolean ok = res.optBoolean("success", false);
                            String msg = res.optString("message", "Password updated successfully.");
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                            if (ok) {
                                dialog.dismiss();
                                proceedToDashboard(userId, role, fullName, storeId, username, email, storeName);
                            }
                        },
                        err -> {
                            btnSubmit.setEnabled(true);
                            btnSubmit.setText("Save & Continue");
                            String errorMsg = "Failed to update password.";
                            if (err.networkResponse != null && err.networkResponse.data != null) {
                                try {
                                    String resp = new String(err.networkResponse.data);
                                    JSONObject errObj = new JSONObject(resp);
                                    errorMsg = errObj.optString("message", errorMsg);
                                } catch (Exception ignored) {}
                            }
                            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
                        }
                );

                ApiSession.getRequestQueue(this).add(req);
            });
        }

        dialog.show();
    }

    private void proceedToDashboard(int userId, String role, String fullName, int storeId,
                                    String username, String email, String storeName) {
        Toast.makeText(LoginActivity.this, "Welcome, " + fullName + "!", Toast.LENGTH_SHORT).show();

        Intent intent;
        switch (role) {
            case "super_admin":
                intent = new Intent(LoginActivity.this, SuperAdminDashboardActivity.class);
                break;
            case "manager":
            case "store_admin":
                // A manager may sign in before their first branch exists: the
                // dashboard opens in "no branch yet" mode with the Add Branch button.
                if (storeId <= 0 && !"manager".equals(role)) {
                    Toast.makeText(LoginActivity.this, "Store administrator has no valid store assignment", Toast.LENGTH_SHORT).show();
                    return;
                }
                intent = new Intent(LoginActivity.this, StoreAdminDashboardActivity.class);
                break;
            case "store_owner":
                intent = new Intent(LoginActivity.this, SuperAdminDashboardActivity.class);
                break;
            case "inventory_checker":
                if (storeId <= 0) {
                    Toast.makeText(LoginActivity.this, "Inventory staff account has no valid store assignment", Toast.LENGTH_SHORT).show();
                    return;
                }
                intent = new Intent(LoginActivity.this, InventoryDashboardActivity.class);
                break;
            case "staff":
                if (storeId <= 0) {
                    Toast.makeText(LoginActivity.this, "Staff account has no valid store assignment", Toast.LENGTH_SHORT).show();
                    return;
                }
                intent = new Intent(LoginActivity.this, CashierDashboardActivity.class);
                break;
            default:
                Toast.makeText(LoginActivity.this, "Unsupported account role", Toast.LENGTH_SHORT).show();
                return;
        }

        intent.putExtra("user_id", userId);
        intent.putExtra("role", role);
        intent.putExtra("full_name", fullName);
        if (storeId > 0) intent.putExtra("store_id", storeId);
        intent.putExtra("username", username);
        intent.putExtra("email", email);
        intent.putExtra("store_name", storeName);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ScreenTransitions.open(LoginActivity.this, intent);
        ScreenTransitions.finish(LoginActivity.this);
    }
}
