package com.example.stockease;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StaffActivity extends AppCompatActivity {

    private RecyclerView rvStaff;
    private View emptyStaff;
    private View errorStaff;
    private View loadingStaff;
    private Button btnAddStaff;
    private StaffAdapter adapter;
    private List<Staff> staffList;
    private int storeId = -1;
    private int userId = -1;
    private String role = "";

    private static final String GET_STAFF_URL = ApiConfig.getUrl("get_staff.php?store_id=");
    private static final String ADD_STAFF_URL = ApiConfig.getUrl("add_staff.php");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff);
        SidebarNav.attach(this, "staff");

        storeId = getIntent().getIntExtra("store_id", -1);
        userId = getIntent().getIntExtra("user_id", -1);
        role = getIntent().getStringExtra("role");

        if (storeId <= 0) {
            returnToLogin("Missing store identity. Please sign in again.");
            return;
        }

        rvStaff = findViewById(R.id.rvStaff);
        loadingStaff = findViewById(R.id.loadingStaff);
        emptyStaff = findViewById(R.id.emptyStaff);
        errorStaff = findViewById(R.id.errorStaff);
        StateViews.bindError(errorStaff, null, null, v -> loadStaff());
        StateViews.bindEmpty(emptyStaff,
                getString(R.string.empty_staff_title),
                getString(R.string.empty_staff_msg), null, null);
        btnAddStaff = findViewById(R.id.btnAddStaff);

        rvStaff.setLayoutManager(new LinearLayoutManager(this));
        staffList = new ArrayList<>();
        adapter = new StaffAdapter(this, staffList);
        rvStaff.setAdapter(adapter);

        btnAddStaff.setOnClickListener(v -> showAddStaffDialog());

        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));

        loadStaff();
    }

    private void loadStaff() {
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, GET_STAFF_URL + storeId, null,
                response -> {
                    StateViews.hideLoading(loadingStaff);
                    StateViews.setErrorVisible(errorStaff, false);
                    try {
                        if (response.getBoolean("success")) {
                            staffList.clear();
                            JSONArray array = response.getJSONArray("staff");
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject obj = array.getJSONObject(i);
                                String itemRole = obj.optString("role", "staff");
                                if ("super_admin".equalsIgnoreCase(itemRole) && !"super_admin".equalsIgnoreCase(role)) {
                                    continue;
                                }
                                staffList.add(new Staff(
                                        obj.getInt("id"),
                                        obj.getString("full_name"),
                                        obj.getString("username"),
                                        obj.optString("email", ""),
                                        itemRole,
                                        obj.optBoolean("is_active", false)
                                ));
                            }
                            adapter.notifyDataSetChanged();
                            updateEmptyState();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    StateViews.hideLoading(loadingStaff);
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to fetch staff list", Toast.LENGTH_SHORT).show();
                    showStaffError();
                }
        );

        queue.add(request);
    }

    /** Swaps list ↔ empty state so a branch with no staff yet reads as intentional. */
    private void updateEmptyState() {
        boolean empty = staffList == null || staffList.isEmpty();
        if (emptyStaff != null) {
            StateViews.setEmptyVisible(emptyStaff, empty);
        }
        if (rvStaff != null) {
            rvStaff.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void showAddStaffDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_staff, null);
        builder.setView(dialogView);

        EditText etFullName = dialogView.findViewById(R.id.etFullName);
        EditText etUsername = dialogView.findViewById(R.id.etUsername);
        EditText etEmail = dialogView.findViewById(R.id.etEmail);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        Spinner spinnerRole = dialogView.findViewById(R.id.spinnerStaffRole);
        Button btnSaveStaff = dialogView.findViewById(R.id.btnSaveStaff);

        // Live strength meter (same verdict as the API policy)
        PasswordStrength.bind(dialogView.findViewById(R.id.pwStrengthMeter), etPassword);

        if (spinnerRole != null) {
            String[] roles = new String[]{"Cashier", "Inventory Staff"};
            ArrayAdapter<String> roleAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, roles) {
                @Override
                public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                    android.view.View v = super.getView(position, convertView, parent);
                    if (v instanceof android.widget.TextView) {
                        ((android.widget.TextView) v).setTextColor(android.graphics.Color.WHITE);
                    }
                    return v;
                }

                @Override
                public android.view.View getDropDownView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                    android.view.View v = super.getDropDownView(position, convertView, parent);
                    v.setBackgroundColor(0xFF2A3040);
                    if (v instanceof android.widget.TextView) {
                        android.widget.TextView tv = (android.widget.TextView) v;
                        tv.setTextColor(android.graphics.Color.WHITE);
                        tv.setPadding(32, 24, 32, 24);
                    }
                    return v;
                }
            };
            spinnerRole.setAdapter(roleAdapter);
            spinnerRole.setSelection(0, true);
        }

        AlertDialog dialog = builder.create();

        btnSaveStaff.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String selectedRole = "staff";
            if (spinnerRole != null && spinnerRole.getSelectedItemPosition() == 1) {
                selectedRole = "inventory_checker";
            }

            if (fullName.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Same rule the API enforces (8+ chars, a letter and a number)
            if (!PasswordStrength.isAcceptable(password)) {
                Toast.makeText(this, PasswordStrength.requirementText(), Toast.LENGTH_SHORT).show();
                return;
            }

            saveStaffToDB(fullName, username, email, password, selectedRole, dialog);
        });

        dialog.show();
    }

    private void saveStaffToDB(String fullName, String username, String email, String password, String selectedRole, AlertDialog dialog) {
        JSONObject body = new JSONObject();
        try {
            body.put("store_id", storeId);
            body.put("caller_user_id", userId);
            body.put("caller_role", role);
            body.put("full_name", fullName);
            body.put("username", username);
            body.put("email", email);
            body.put("password", password);
            body.put("role", selectedRole);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, ADD_STAFF_URL, body,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            String msg = response.optString("message", "Staff added successfully!");
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            loadStaff(); // Refresh list
                        } else {
                            Toast.makeText(this, response.getString("message"), Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    String errMsg = "Error adding staff";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String resp = new String(error.networkResponse.data);
                            JSONObject obj = new JSONObject(resp);
                            errMsg = obj.optString("message", errMsg);
                        } catch (Exception ignored) {}
                    }
                    Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
                }
        );

        queue.add(request);
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ScreenTransitions.open(StaffActivity.this, intent);
        ScreenTransitions.finish(StaffActivity.this);
    }

    /**
     * A load failed. Shows the error state with a working retry instead of
     * leaving a blank screen, which a user cannot tell apart from an empty
     * list or a broken app.
     */
    private void showStaffError() {
        if (emptyStaff != null) {
            StateViews.setEmptyVisible(emptyStaff, false);
        }
        StateViews.setErrorVisible(errorStaff, true);
    }

}
