package com.example.stockease;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
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

public class POSActivity extends AppCompatActivity {

    private static final String GET_PRODUCTS_URL = ApiConfig.getUrl("get_products.php?store_id=");
    private static final String PROCESS_TRANSACTION_URL = ApiConfig.getUrl("process_transaction.php");

    private RecyclerView rvPosProducts, rvCart;
    private TextView tvCartTotal;
    private Button btnCheckout, btnTransactionHistory; // Added button variable

    private List<Product> productList = new ArrayList<>();
    private List<CartItem> cartItemList = new ArrayList<>();

    private POSProductAdapter productAdapter;
    private CartAdapter cartAdapter;

    private int storeId = -1;
    private int userId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pos);

        storeId = getIntent().getIntExtra("store_id", -1);
        userId = getIntent().getIntExtra("user_id", -1);

        if (storeId <= 0 || userId <= 0) {
            returnToLogin("Missing account or store identity. Please sign in again.");
            return;
        }

        rvPosProducts = findViewById(R.id.rvPosProducts);
        rvCart = findViewById(R.id.rvCart);
        tvCartTotal = findViewById(R.id.tvCartTotal);
        btnCheckout = findViewById(R.id.btnCheckout);

        // Find the Transaction History button from your activity_pos.xml layout
        btnTransactionHistory = findViewById(R.id.btnTransactionHistory);

        rvPosProducts.setLayoutManager(new LinearLayoutManager(this));
        productAdapter = new POSProductAdapter(this, productList, this::addToCart);
        rvPosProducts.setAdapter(productAdapter);

        rvCart.setLayoutManager(new LinearLayoutManager(this));
        cartAdapter = new CartAdapter(this, cartItemList, () -> calculateTotal());
        rvCart.setAdapter(cartAdapter);

        btnCheckout.setOnClickListener(v -> processCheckout());

        // Navigate to TransactionHistoryActivity
        if (btnTransactionHistory != null) {
            btnTransactionHistory.setOnClickListener(v -> {
                Intent intent = new Intent(POSActivity.this, TransactionHistoryActivity.class);
                intent.putExtra("store_id", storeId);
                intent.putExtra("user_id", userId);
                startActivity(intent);
            });
        }

        loadProducts();
    }

    private void loadProducts() {
        String url = GET_PRODUCTS_URL + storeId;

        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> {
                    productList.clear();
                    try {
                        if (response.optBoolean("success", false)) {
                            JSONArray array = response.getJSONArray("products");

                            for (int i = 0; i < array.length(); i++) {
                                JSONObject obj = array.getJSONObject(i);

                                Product product = new Product(
                                        obj.getInt("id"),
                                        obj.getString("product_name"),
                                        obj.optString("category", "General"),
                                        obj.getDouble("price"),
                                        obj.getInt("stock")
                                );
                                productList.add(product);
                            }
                            productAdapter.notifyDataSetChanged();
                        } else {
                            Toast.makeText(this, "Failed to load catalog", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Network Error: Could not load products", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    private void addToCart(Product product) {
        if (product.getStock() <= 0) {
            Toast.makeText(this, "Item is out of stock", Toast.LENGTH_SHORT).show();
            return;
        }

        for (CartItem item : cartItemList) {
            if (item.getProduct().getId() == product.getId()) {
                if (item.getQuantity() < product.getStock()) {
                    item.setQuantity(item.getQuantity() + 1);
                    cartAdapter.notifyDataSetChanged();
                    calculateTotal();
                } else {
                    Toast.makeText(this, "Maximum available stock reached", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }

        cartItemList.add(new CartItem(product, 1));
        cartAdapter.notifyDataSetChanged();
        calculateTotal();
    }

    private void calculateTotal() {
        double total = 0.0;
        for (CartItem item : cartItemList) {
            total += item.getTotalPrice();
        }
        tvCartTotal.setText(String.format("₱%.2f", total));
    }

    private void processCheckout() {
        if (storeId <= 0 || userId <= 0) {
            returnToLogin("Missing account or store identity. Please sign in again.");
            return;
        }

        if (cartItemList.isEmpty()) {
            Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        double totalAmount = 0.0;
        JSONArray itemsArray = new JSONArray();
        StringBuilder summaryBuilder = new StringBuilder();

        try {
            for (CartItem item : cartItemList) {
                if (item.getProduct() == null
                        || item.getProduct().getId() <= 0
                        || item.getQuantity() <= 0
                        || item.getQuantity() > item.getProduct().getStock()) {
                    Toast.makeText(this, "Cart contains an invalid product quantity", Toast.LENGTH_LONG).show();
                    return;
                }

                totalAmount += item.getTotalPrice();

                JSONObject itemObj = new JSONObject();
                itemObj.put("product_id", item.getProduct().getId());
                itemObj.put("quantity", item.getQuantity());
                itemObj.put("unit_price", item.getProduct().getPrice());
                itemsArray.put(itemObj);

                summaryBuilder.append(item.getQuantity())
                        .append("x ")
                        .append(item.getProduct().getName())
                        .append(" @ ₱")
                        .append(String.format("%.2f", item.getProduct().getPrice()))
                        .append(" = ₱")
                        .append(String.format("%.2f", item.getTotalPrice()))
                        .append("\n");
            }

            final double finalTotal = totalAmount;
            final String itemsSummary = summaryBuilder.toString();

            JSONObject requestData = new JSONObject();
            requestData.put("store_id", storeId);
            requestData.put("user_id", userId);
            requestData.put("total_amount", finalTotal);
            requestData.put("payment_type", "Cash");
            requestData.put("items", itemsArray);

            RequestQueue queue = ApiSession.getRequestQueue(this);
            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST, PROCESS_TRANSACTION_URL, requestData,
                    response -> {
                        try {
                            if (response.getBoolean("success")) {
                                int transactionId = response.optInt("transaction_id", -1);

                                if (!response.has("total_amount") || response.isNull("total_amount")) {
                                    Toast.makeText(
                                            POSActivity.this,
                                            "Transaction saved, but the server receipt total is missing. Check transaction history before retrying.",
                                            Toast.LENGTH_LONG
                                    ).show();
                                    cartItemList.clear();
                                    cartAdapter.notifyDataSetChanged();
                                    calculateTotal();
                                    loadProducts();
                                    return;
                                }

                                double serverTotal = response.getDouble("total_amount");
                                if (Double.isNaN(serverTotal) || Double.isInfinite(serverTotal) || serverTotal < 0) {
                                    Toast.makeText(
                                            POSActivity.this,
                                            "Transaction saved, but the server receipt total is invalid. Check transaction history before retrying.",
                                            Toast.LENGTH_LONG
                                    ).show();
                                    cartItemList.clear();
                                    cartAdapter.notifyDataSetChanged();
                                    calculateTotal();
                                    loadProducts();
                                    return;
                                }

                                Intent intent = new Intent(POSActivity.this, ReceiptActivity.class);
                                intent.putExtra("transaction_id", transactionId);
                                intent.putExtra("total_amount", serverTotal);
                                intent.putExtra("items_summary", itemsSummary);
                                startActivity(intent);

                                cartItemList.clear();
                                cartAdapter.notifyDataSetChanged();
                                calculateTotal();
                                loadProducts();
                            } else {
                                // Direct PHP database error message displayed to Toast
                                String serverMsg = response.optString("message", "Checkout failed");
                                Toast.makeText(POSActivity.this, "Server Error: " + serverMsg, Toast.LENGTH_LONG).show();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    },
                    error -> {
                        if (ApiSession.handleAuthError(this, error)) return;
                        String errDetails = "Checkout Failed";
                        if (error.networkResponse != null && error.networkResponse.data != null) {
                            errDetails += ": " + new String(error.networkResponse.data);
                        } else if (error.getMessage() != null) {
                            errDetails += ": " + error.getMessage();
                        }
                        Toast.makeText(POSActivity.this, errDetails, Toast.LENGTH_LONG).show();
                    }
            );

            queue.add(request);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private final android.os.Handler syncHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable syncRunnable = new Runnable() {
        @Override
        public void run() {
            loadProducts();
            syncHandler.postDelayed(this, 10000); // Poll every 10 seconds for real-time staff sync
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        loadProducts();
        syncHandler.postDelayed(syncRunnable, 10000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        syncHandler.removeCallbacks(syncRunnable);
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
