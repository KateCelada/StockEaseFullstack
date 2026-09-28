package com.example.stockease;
import com.example.stockease.R;
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
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

public class RegisterActivity extends AppCompatActivity {

    private EditText etFullName, etUsername, etEmail, etPassword;
    private Button btnRegister;
    private TextView tvLoginLink;
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
        btnRegister = findViewById(R.id.btnRegister);
        tvLoginLink = findViewById(R.id.tvLoginLink);

        btnRegister.setOnClickListener(v -> handleRegistration());
        tvLoginLink.setOnClickListener(v -> finish());
    }

    private void handleRegistration() {
        if (storeId <= 0) {
            Toast.makeText(this, "A valid store assignment is required to register staff", Toast.LENGTH_LONG).show();
            return;
        }

        String fullName = etFullName.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Validate empty fields
        if (fullName.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate email structure
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
            return;
        }

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
                    try {
                        boolean success = response.getBoolean("success");
                        String message = response.getString("message");
                        Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show();

                        if (success) {
                            finish(); // Return to previous activity upon success
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(RegisterActivity.this, error)) return;
                    Toast.makeText(RegisterActivity.this, "Network error. Check connection.", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }
}
