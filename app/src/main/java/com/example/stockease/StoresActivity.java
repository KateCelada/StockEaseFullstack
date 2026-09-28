package com.example.stockease;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
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

public class StoresActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTION_MODE = "selection_mode";
    public static final String EXTRA_SELECTION_TARGET = "selection_target";
    public static final String TARGET_PRODUCTS = "products";
    public static final String TARGET_TRANSACTIONS = "transactions";
    public static final String TARGET_REPORTS = "reports";

    private RecyclerView rvStores;
    private Button btnAddStore;
    private TextView tvStoresTitle, tvStoresSubtitle;
    private StoreAdapter adapter;
    private List<StoreAdapter.StoreItem> storeList;
    private boolean selectionMode;
    private String selectionTarget = "";
    private int userId = -1;
    private String role = "";
    private String fullName = "";

    private static final String FETCH_STORES_URL = ApiConfig.getUrl("manage_stores.php?action=fetch");
    private static final String ADD_STORE_URL = ApiConfig.getUrl("manage_stores.php?action=add");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stores);

        selectionMode = getIntent().getBooleanExtra(EXTRA_SELECTION_MODE, false);
        selectionTarget = getIntent().getStringExtra(EXTRA_SELECTION_TARGET);
        userId = getIntent().getIntExtra("user_id", -1);
        role = getIntent().getStringExtra("role");
        fullName = getIntent().getStringExtra("full_name");

        if (userId <= 0 || !"super_admin".equals(role)) {
            returnToLogin("Store management and selection are available only to Super Admin accounts.");
            return;
        }

        if (selectionMode
                && !TARGET_PRODUCTS.equals(selectionTarget)
                && !TARGET_TRANSACTIONS.equals(selectionTarget)
                && !TARGET_REPORTS.equals(selectionTarget)) {
            Toast.makeText(this, "Invalid store-selection destination", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        rvStores = findViewById(R.id.rvStores);
        btnAddStore = findViewById(R.id.btnAddStore);
        tvStoresTitle = findViewById(R.id.tvStoresTitle);
        tvStoresSubtitle = findViewById(R.id.tvStoresSubtitle);

        storeList = new ArrayList<>();
        adapter = new StoreAdapter(storeList, selectionMode ? this::handleStoreSelected : null);

        if (rvStores != null) {
            rvStores.setLayoutManager(new LinearLayoutManager(this));
            rvStores.setAdapter(adapter);
        }

        if (selectionMode) {
            if (btnAddStore != null) btnAddStore.setVisibility(View.GONE);
            if (tvStoresTitle != null) tvStoresTitle.setText("Select Store");
            if (tvStoresSubtitle != null) {
                if (TARGET_PRODUCTS.equals(selectionTarget)) {
                    tvStoresSubtitle.setText("Choose a store to view its products");
                } else if (TARGET_REPORTS.equals(selectionTarget)) {
                    tvStoresSubtitle.setText("Choose a store to view performance reports");
                } else {
                    tvStoresSubtitle.setText("Choose a store to view its sales history");
                }
            }
        } else if (btnAddStore != null) {
            btnAddStore.setOnClickListener(v -> showAddStoreDialog());
        }

        loadStores();
    }

    private void showAddStoreDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_store, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        EditText etDialogStoreName = dialogView.findViewById(R.id.etDialogStoreName);
        EditText etDialogLocation = dialogView.findViewById(R.id.etDialogLocation);
        Button btnSaveStore = dialogView.findViewById(R.id.btnSaveStore);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSaveStore.setOnClickListener(v -> {
            String storeName = etDialogStoreName.getText().toString().trim();
            String location = etDialogLocation.getText().toString().trim();

            if (TextUtils.isEmpty(storeName)) {
                etDialogStoreName.setError("Store name is required");
                return;
            }

            saveStoreToDatabase(storeName, location, dialog);
        });

        dialog.show();
    }

    private void saveStoreToDatabase(String storeName, String location, AlertDialog dialog) {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JSONObject postData = new JSONObject();
        try {
            postData.put("store_name", storeName);
            postData.put("location", location);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                ADD_STORE_URL,
                postData,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            Toast.makeText(StoresActivity.this, "Store added successfully!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            loadStores(); // Refresh stores list dynamically
                        } else {
                            Toast.makeText(StoresActivity.this, response.optString("message", "Failed to add store"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(StoresActivity.this, error)) return;
                    Toast.makeText(StoresActivity.this, "Network error. Try again.", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    private void loadStores() {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, FETCH_STORES_URL, null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            storeList.clear();
                            JSONArray arr = response.getJSONArray("stores");
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                storeList.add(new StoreAdapter.StoreItem(
                                        obj.getInt("id"),
                                        obj.optString("store_name", "Unnamed Store"),
                                        obj.optString("location", "No Location"),
                                        obj.optString("status", "Active"),
                                        obj.optInt("admin_count", 0)
                                    ));
                            }
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> ApiSession.handleAuthError(StoresActivity.this, error)
        );

        queue.add(request);
    }

    private void handleStoreSelected(StoreAdapter.StoreItem store) {
        if (!selectionMode || store == null || store.getId() <= 0) {
            Toast.makeText(this, "Invalid store selection", Toast.LENGTH_SHORT).show();
            return;
        }

        Class<?> destination;
        if (TARGET_PRODUCTS.equals(selectionTarget)) {
            destination = ProductsActivity.class;
        } else if (TARGET_REPORTS.equals(selectionTarget)) {
            destination = ReportsActivity.class;
        } else {
            destination = TransactionHistoryActivity.class;
        }

        Intent intent = new Intent(this, destination);
        intent.putExtra("store_id", store.getId());
        intent.putExtra("selected_store_name", store.getStoreName());
        intent.putExtra("user_id", userId);
        intent.putExtra("role", role);
        intent.putExtra("full_name", fullName);
        startActivity(intent);
        finish();
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
