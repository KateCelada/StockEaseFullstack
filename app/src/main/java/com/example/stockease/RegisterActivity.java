package com.example.stockease;

import android.os.Bundle;
import android.util.Patterns;
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

public class RegisterActivity extends AppCompatActivity {

    private EditText etFullName, etUsername, etEmail, etPassword;
    private TextInputLayout tilFullName, tilUsername, tilEmail, tilPassword;
    private Button btnRegister;
    private TextView tvLoginLink;
    private String registerLabel;
    private int storeId = -1;

    // 10.0.2.2 maps to local host server from the Android Emulator
    private static final String REGISTER_URL = ApiConfig.getUrl("register.php");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        storeId = getIntent().getIntExtra("store_id", -1);

        etFullName = findViewById(R.id.etFullName);
        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tilFullName = findViewById(R.id.tilFullName);
        tilUsername = findViewById(R.id.tilUsername);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvLoginLink = findViewById(R.id.tvLoginLink);
        registerLabel = getString(R.string.register_action);

        btnRegister.setOnClickListener(v -> handleRegistration());
        tvLoginLink.setOnClickListener(v -> finish());
    }

    /** Inline field error: shows under the field instead of a toast. */
    private void fieldError(TextInputLayout layout, EditText field, int msgRes) {
        if (layout != null) layout.setError(getString(msgRes));
        else if (field != null) field.setError(getString(msgRes));
        if (field != null) field.requestFocus();
    }

    private void setCreating(boolean inFlight) {
        if (btnRegister == null) return;
        btnRegister.setEnabled(!inFlight);
        btnRegister.setText(inFlight ? getString(R.string.action_creating_account) : registerLabel);
    }

    private void handleRegistration() {
        if (storeId <= 0) {
            Toast.makeText(this, R.string.err_store_missing, Toast.LENGTH_LONG).show();
            return;
        }

        String fullName = etFullName.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Clear previous errors
        if (tilFullName != null) tilFullName.setError(null);
        if (tilUsername != null) tilUsername.setError(null);
        if (tilEmail != null) tilEmail.setError(null);
        if (tilPassword != null) tilPassword.setError(null);

        // Validate empty fields — first problem wins, inline under its field
        if (fullName.isEmpty()) {
            fieldError(tilFullName, etFullName, R.string.err_full_name_required);
            return;
        }
        if (username.isEmpty()) {
            fieldError(tilUsername, etUsername, R.string.err_username_required);
            return;
        }
        if (email.isEmpty()) {
            fieldError(tilEmail, etEmail, R.string.err_email_required);
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            fieldError(tilEmail, etEmail, R.string.err_email_invalid);
            return;
        }
        if (password.isEmpty()) {
            fieldError(tilPassword, etPassword, R.string.err_password_required);
            return;
        }
        if (password.length() < 6) {
            fieldError(tilPassword, etPassword, R.string.err_password_short);
            return;
        }

        setCreating(true);

        // Construct JSON payload
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("full_name", fullName);
            jsonBody.put("username", username);
            jsonBody.put("email", email);
            jsonBody.put("password", password);
            jsonBody.put("store_id", storeId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Send HTTP request via Volley
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, REGISTER_URL, jsonBody,
                response -> {
                    setCreating(false);
                    try {
                        boolean success = response.getBoolean("success");
                        String message = response.getString("message");
                        if (success) {
                            Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show();
                            ScreenTransitions.finish(RegisterActivity.this); // Return to previous activity upon success
                        } else {
                            // Server-side rejection (duplicate username/email): inline under username
                            if (tilUsername != null) {
                                tilUsername.setError(message);
                                etUsername.requestFocus();
                            } else {
                                Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    setCreating(false);
                    if (ApiSession.handleAuthError(RegisterActivity.this, error)) return;
                    Toast.makeText(RegisterActivity.this, "Network error. Check connection.", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }
}
