package com.example.stockease;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword;
    private Button btnLogin;

    private static final String LOGIN_URL = ApiConfig.getUrl("login.php");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Bind Views
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);

        // Login Button Click Listener
        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> performLogin());
        }
    }

    private void performLogin() {
        if (etUsername == null || etPassword == null) return;

        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(username)) {
            etUsername.setError("Username is required");
            etUsername.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            etPassword.requestFocus();
            return;
        }

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
                    try {
                        boolean success = response.getBoolean("success");
                        String message = response.optString("message", "Login response received");

                        if (success) {
                            Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();

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

                            Intent intent;
                            switch (role) {
                                case "super_admin":
                                    intent = new Intent(LoginActivity.this, SuperAdminDashboardActivity.class);
                                    break;
                                case "store_admin":
                                    if (storeId <= 0) {
                                        Toast.makeText(LoginActivity.this, "Store administrator has no valid store assignment", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    intent = new Intent(LoginActivity.this, StoreAdminDashboardActivity.class);
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
                            intent.putExtra("username", authUsername);
                            intent.putExtra("email", email);
                            intent.putExtra("store_name", storeName);

                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();

                        } else {
                            Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(LoginActivity.this, "Invalid server response", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
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
}
