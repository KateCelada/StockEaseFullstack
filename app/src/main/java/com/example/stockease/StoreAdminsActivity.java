package com.example.stockease;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

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

public class StoreAdminsActivity extends AppCompatActivity {

    private RecyclerView rvStoreAdmins;
    private View emptyAdmins;
    private View loadingAdmins;
    private Button btnAddAdmin;
    private StoreAdminAdapter adapter;
    private List<StoreAdminAdapter.AdminItem> adminList;

    private final List<String> storeNames = new ArrayList<>();
    private final List<Integer> storeIds = new ArrayList<>();

    private static final String FETCH_ADMINS_URL = ApiConfig.getUrl("manage_store_admins.php?action=fetch");
    private static final String ADD_ADMIN_URL = ApiConfig.getUrl("manage_store_admins.php?action=add");
    private static final String FETCH_STORES_URL = ApiConfig.getUrl("manage_stores.php?action=fetch");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store_admins);

        rvStoreAdmins = findViewById(R.id.rvStoreAdmins);
        emptyAdmins = findViewById(R.id.emptyAdmins);
        loadingAdmins = findViewById(R.id.loadingAdmins);
        StateViews.bindEmpty(emptyAdmins,
                getString(R.string.empty_admins_title),
                getString(R.string.empty_admins_msg), null, null);
        btnAddAdmin = findViewById(R.id.btnAddAdmin);

        adminList = new ArrayList<>();
        adapter = new StoreAdminAdapter(adminList);

        if (rvStoreAdmins != null) {
            rvStoreAdmins.setLayoutManager(new LinearLayoutManager(this));
            rvStoreAdmins.setAdapter(adapter);
        }

        if (btnAddAdmin != null) {
            btnAddAdmin.setOnClickListener(v -> showAddAdminDialog());
        }

        loadStoreAdmins();
        loadStoresForSpinner();
        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));
    }

    private void showAddAdminDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_store_admin, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        EditText etAdminFullName = dialogView.findViewById(R.id.etAdminFullName);
        EditText etAdminUsername = dialogView.findViewById(R.id.etAdminUsername);
        EditText etAdminPassword = dialogView.findViewById(R.id.etAdminPassword);
        Spinner spAssignStore = dialogView.findViewById(R.id.spAssignStore);
        Button btnSaveAdmin = dialogView.findViewById(R.id.btnSaveAdmin);
        Button btnCancelAdmin = dialogView.findViewById(R.id.btnCancelAdmin);

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, storeNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAssignStore.setAdapter(spinnerAdapter);

        btnCancelAdmin.setOnClickListener(v -> dialog.dismiss());

        btnSaveAdmin.setOnClickListener(v -> {
            String fullName = etAdminFullName.getText().toString().trim();
            String username = etAdminUsername.getText().toString().trim();
            String password = etAdminPassword.getText().toString().trim();
            int selectedIndex = spAssignStore.getSelectedItemPosition();

            if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (storeIds.isEmpty() || selectedIndex < 0) {
                Toast.makeText(this, "Please select a valid store", Toast.LENGTH_SHORT).show();
                return;
            }

            int storeId = storeIds.get(selectedIndex);
            saveAdminToDatabase(fullName, username, password, storeId, dialog);
        });

        dialog.show();
    }

    private void saveAdminToDatabase(String fullName, String username, String password, int storeId, AlertDialog dialog) {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JSONObject postData = new JSONObject();
        try {
            postData.put("full_name", fullName);
            postData.put("username", username);
            postData.put("password", password);
            postData.put("store_id", storeId);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                ADD_ADMIN_URL,
                postData,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            Toast.makeText(StoreAdminsActivity.this, "Store Admin created successfully!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            loadStoreAdmins();
                        } else {
                            Toast.makeText(StoreAdminsActivity.this, response.optString("message", "Failed to create admin"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(StoreAdminsActivity.this, error)) return;
                    Toast.makeText(StoreAdminsActivity.this, "Network error. Try again.", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    private void loadStoreAdmins() {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, FETCH_ADMINS_URL, null,
                response -> {
                    StateViews.hideLoading(loadingAdmins);
                    try {
                        if (response.getBoolean("success")) {
                            adminList.clear();
                            JSONArray arr = response.getJSONArray("admins");
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                adminList.add(new StoreAdminAdapter.AdminItem(
                                        obj.getInt("id"),
                                        obj.optString("full_name", "Admin"),
                                        obj.optString("username", "user"),
                                        obj.optString("store_name", "Unassigned")
                                ));
                            }
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                            updateEmptyState();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    StateViews.hideLoading(loadingAdmins);
                    ApiSession.handleAuthError(StoreAdminsActivity.this, error);
                }
        );

        queue.add(request);
    }

    /** Swaps list ↔ empty state so a branch with no assigned admins reads as intentional. */
    private void updateEmptyState() {
        boolean empty = adminList == null || adminList.isEmpty();
        if (emptyAdmins != null) {
            StateViews.setEmptyVisible(emptyAdmins, empty);
        }
        if (rvStoreAdmins != null) {
            rvStoreAdmins.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void loadStoresForSpinner() {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, FETCH_STORES_URL, null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            storeNames.clear();
                            storeIds.clear();
                            JSONArray arr = response.getJSONArray("stores");
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                storeNames.add(obj.getString("store_name"));
                                storeIds.add(obj.getInt("id"));
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> ApiSession.handleAuthError(StoreAdminsActivity.this, error)
        );

        queue.add(request);
    }
}
