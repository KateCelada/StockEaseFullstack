package com.example.stockease;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import java.util.Locale;

public class LowStockActivity extends AppCompatActivity {

    private static final int PAR_LEVEL = 30;
    private static final String GET_LOW_STOCK_URL = ApiConfig.getUrl("get_low_stock.php?store_id=");
    private static final String RESTOCK_URL = ApiConfig.getUrl("restock_product.php");

    private RecyclerView rvLowStockProducts;
    private TextView tvRestockTotalItems, tvRestockCriticalItems, tvRestockTotalEstCost;
    private LinearLayout layoutRestockEmpty;
    private Button btnLowStockBack;

    private RestockSuggestionAdapter adapter;
    private final List<Product> lowStockList = new ArrayList<>();
    private int storeId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_low_stock);

        storeId = getIntent().getIntExtra("store_id", -1);
        if (storeId <= 0) {
            returnToLogin("Missing store identity. Please sign in again.");
            return;
        }

        btnLowStockBack = findViewById(R.id.btnLowStockBack);
        tvRestockTotalItems = findViewById(R.id.tvRestockTotalItems);
        tvRestockCriticalItems = findViewById(R.id.tvRestockCriticalItems);
        tvRestockTotalEstCost = findViewById(R.id.tvRestockTotalEstCost);
        layoutRestockEmpty = findViewById(R.id.layoutRestockEmpty);
        rvLowStockProducts = findViewById(R.id.rvLowStockProducts);

        if (btnLowStockBack != null) {
            btnLowStockBack.setOnClickListener(v -> finish());
        }

        rvLowStockProducts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RestockSuggestionAdapter(lowStockList, this::showRestockDialog);
        rvLowStockProducts.setAdapter(adapter);

        loadLowStockProducts();
    }

    private void loadLowStockProducts() {
        String url = GET_LOW_STOCK_URL + storeId;
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONArray productsArray = response.getJSONArray("products");
                            lowStockList.clear();

                            int criticalCount = 0;
                            double totalEstCost = 0.0;

                            for (int i = 0; i < productsArray.length(); i++) {
                                JSONObject obj = productsArray.getJSONObject(i);

                                int id = obj.getInt("id");
                                String name = obj.getString("product_name");
                                String category = obj.optString("category", "General");
                                double price = obj.optDouble("price", 0.0);
                                int stock = obj.getInt("stock");

                                if (stock < 5) {
                                    criticalCount++;
                                }

                                int suggested = Math.max(0, PAR_LEVEL - stock);
                                totalEstCost += (suggested * price);

                                Product product = new Product(id, name, category, price, stock);
                                lowStockList.add(product);
                            }

                            updateSummaryStats(lowStockList.size(), criticalCount, totalEstCost);
                            adapter.notifyDataSetChanged();
                        } else {
                            Toast.makeText(this, response.optString("message", "Unable to load stock items"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Failed to parse inventory alerts", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Network error loading restock suggestions", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    private void updateSummaryStats(int totalItems, int criticalItems, double estCost) {
        if (tvRestockTotalItems != null) {
            tvRestockTotalItems.setText(String.valueOf(totalItems));
        }
        if (tvRestockCriticalItems != null) {
            tvRestockCriticalItems.setText(String.valueOf(criticalItems));
        }
        if (tvRestockTotalEstCost != null) {
            tvRestockTotalEstCost.setText(String.format(Locale.US, "₱%.2f", estCost));
        }

        if (totalItems == 0) {
            if (layoutRestockEmpty != null) layoutRestockEmpty.setVisibility(View.VISIBLE);
            if (rvLowStockProducts != null) rvLowStockProducts.setVisibility(View.GONE);
        } else {
            if (layoutRestockEmpty != null) layoutRestockEmpty.setVisibility(View.GONE);
            if (rvLowStockProducts != null) rvLowStockProducts.setVisibility(View.VISIBLE);
        }
    }

    private void showRestockDialog(Product product, int suggestedQty) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_restock_item, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogRestockTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvDialogRestockSubtitle);
        EditText etQuantity = dialogView.findViewById(R.id.etRestockQuantity);
        Button btnCancel = dialogView.findViewById(R.id.btnDialogRestockCancel);
        Button btnConfirm = dialogView.findViewById(R.id.btnDialogRestockConfirm);

        tvTitle.setText("Restock: " + product.getName());
        tvSubtitle.setText("Current Stock: " + product.getStock() + " units | Target Par: " + PAR_LEVEL + " units");
        etQuantity.setText(String.valueOf(suggestedQty > 0 ? suggestedQty : 10));
        etQuantity.selectAll();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String qtyStr = etQuantity.getText().toString().trim();
            if (TextUtils.isEmpty(qtyStr)) {
                etQuantity.setError("Please enter quantity");
                return;
            }

            int qty;
            try {
                qty = Integer.parseInt(qtyStr);
                if (qty <= 0) {
                    etQuantity.setError("Quantity must be greater than zero");
                    return;
                }
            } catch (NumberFormatException e) {
                etQuantity.setError("Invalid number");
                return;
            }

            btnConfirm.setEnabled(false);
            performRestockRequest(dialog, product, qty);
        });

        dialog.show();
    }

    private void performRestockRequest(AlertDialog dialog, Product product, int quantity) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("store_id", storeId);
            payload.put("product_id", product.getId());
            payload.put("quantity", quantity);
        } catch (JSONException e) {
            e.printStackTrace();
            dialog.dismiss();
            return;
        }

        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest restockRequest = new JsonObjectRequest(
                Request.Method.POST,
                RESTOCK_URL,
                payload,
                response -> {
                    dialog.dismiss();
                    if (response.optBoolean("success", false)) {
                        Toast.makeText(this, "Replenished +" + quantity + " units of " + product.getName(), Toast.LENGTH_SHORT).show();
                        loadLowStockProducts();
                    } else {
                        Toast.makeText(this, response.optString("message", "Failed to update stock"), Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    dialog.dismiss();
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Restock network request failed", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(restockRequest);
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
