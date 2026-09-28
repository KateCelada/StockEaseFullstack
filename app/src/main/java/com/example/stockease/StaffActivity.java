package com.example.stockease;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StaffActivity extends AppCompatActivity {

    private RecyclerView rvStaff;
    private Button btnAddStaff;
    private StaffAdapter adapter;
    private List<Staff> staffList;
    private int storeId = -1;

    private static final String GET_STAFF_URL = ApiConfig.getUrl("get_staff.php?store_id=");
    private static final String ADD_STAFF_URL = ApiConfig.getUrl("add_staff.php");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff);

        storeId = getIntent().getIntExtra("store_id", -1);
        if (storeId <= 0) {
            returnToLogin("Missing store identity. Please sign in again.");
            return;
        }

        rvStaff = findViewById(R.id.rvStaff);
        btnAddStaff = findViewById(R.id.btnAddStaff);

        rvStaff.setLayoutManager(new LinearLayoutManager(this));
        staffList = new ArrayList<>();
        adapter = new StaffAdapter(this, staffList);
        rvStaff.setAdapter(adapter);

        btnAddStaff.setOnClickListener(v -> showAddStaffDialog());

        loadStaff();
    }

    private void loadStaff() {
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, GET_STAFF_URL + storeId, null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            staffList.clear();
                            JSONArray array = response.getJSONArray("staff");
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject obj = array.getJSONObject(i);
                                staffList.add(new Staff(
                                        obj.getInt("id"),
                                        obj.getString("full_name"),
                                        obj.getString("username"),
                                        obj.optString("email", ""),
                                        obj.optBoolean("is_active", false)
                                ));
                            }
                            adapter.notifyDataSetChanged();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to fetch staff list", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    private void showAddStaffDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_staff, null);
        builder.setView(dialogView);

        EditText etFullName = dialogView.findViewById(R.id.etFullName);
        EditText etUsername = dialogView.findViewById(R.id.etUsername);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        Button btnSaveStaff = dialogView.findViewById(R.id.btnSaveStaff);

        AlertDialog dialog = builder.create();

        btnSaveStaff.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            saveStaffToDB(fullName, username, email, password, dialog);
        });

        dialog.show();
    }

    private void saveStaffToDB(String fullName, String username, String email, String password, AlertDialog dialog) {
        JSONObject body = new JSONObject();
        try {
            body.put("store_id", storeId);
            body.put("full_name", fullName);
            body.put("username", username);
            body.put("email", email);
            body.put("password", password);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, ADD_STAFF_URL, body,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            Toast.makeText(this, "Staff added successfully!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            loadStaff(); // Refresh list
                        } else {
                            Toast.makeText(this, response.getString("message"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Network error adding staff", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
