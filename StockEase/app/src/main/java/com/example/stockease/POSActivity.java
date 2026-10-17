package com.example.stockease;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
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
    private static final String ADD_CATEGORY_URL = ApiConfig.getUrl("add_category.php");

    private RecyclerView rvPosProducts, rvCart;
    private TextView tvCartTotal;
    private Button btnCheckout, btnTransactionHistory; // Added button variable
    private View emptyProducts, emptyCart;
    private LinearLayout llCategoryChips;

    /** Full catalog as loaded from the API; productList is the filtered view. */
    private List<Product> allProducts = new ArrayList<>();
    private List<Product> productList = new ArrayList<>();
    private List<CartItem> cartItemList = new ArrayList<>();
    private final List<String> categoryList = new ArrayList<>();
    private String selectedCategory = null;

    private POSProductAdapter productAdapter;
    private CartAdapter cartAdapter;

    private int storeId = -1;
    private int userId = -1;
    private String role = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pos);
        SidebarNav.attach(this, "pos");

        storeId = getIntent().getIntExtra("store_id", -1);
        userId = getIntent().getIntExtra("user_id", -1);
        role = getIntent().getStringExtra("role") != null ? getIntent().getStringExtra("role") : "";

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

        emptyProducts = findViewById(R.id.emptyProducts);
        emptyCart = findViewById(R.id.emptyCart);
        llCategoryChips = findViewById(R.id.llCategoryChips);
        StateViews.bindEmpty(emptyProducts,
                getString(R.string.pos_empty_products),
                getString(R.string.pos_empty_products_msg), null, null);
        StateViews.bindEmpty(emptyCart,
                getString(R.string.pos_empty_cart),
                getString(R.string.pos_empty_cart_msg), null, null);
        updateEmptyStates();

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
                ScreenTransitions.open(POSActivity.this, intent);
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
                    allProducts.clear();
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
                                allProducts.add(product);
                            }
                            renderCategoryChips();
                            applyCategoryFilter();
                        } else {
                            Toast.makeText(this, "Failed to load catalog", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    updateEmptyStates();
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Network Error: Could not load products", Toast.LENGTH_SHORT).show();
                    updateEmptyStates();
                }
        );

        queue.add(request);
    }

    // ---- Category filter + in-POS category creation (panelist request) ----

    /** True when this account may create categories (the API enforces the same roles). */
    private boolean canCreateCategory() {
        return role.isEmpty() || "store_admin".equals(role) || "super_admin".equals(role);
    }

    /** Rebuilds the chip row: All + every catalog category + "+ New" for permitted roles. */
    private void renderCategoryChips() {
        if (llCategoryChips == null) return;
        llCategoryChips.removeAllViews();

        // Distinct categories in catalog order; a just-created (still empty)
        // category is kept visible so the selection never silently vanishes.
        categoryList.clear();
        for (Product p : allProducts) {
            String c = (p.getCategory() == null || p.getCategory().trim().isEmpty()) ? "General" : p.getCategory();
            if (!categoryList.contains(c)) categoryList.add(c);
        }
        if (selectedCategory != null && !categoryList.contains(selectedCategory)) {
            categoryList.add(selectedCategory);
        }

        llCategoryChips.addView(makeChip("All", selectedCategory == null, v -> {
            selectedCategory = null;
            renderCategoryChips();
            applyCategoryFilter();
        }));

        for (final String cat : categoryList) {
            llCategoryChips.addView(makeChip(cat, cat.equals(selectedCategory), v -> {
                selectedCategory = cat;
                renderCategoryChips();
                applyCategoryFilter();
            }));
        }

        if (canCreateCategory()) {
            TextView addChip = makeChip("+ New", false, v -> showCreateCategoryDialog());
            addChip.setTextColor(ContextCompat.getColor(this, R.color.primary));
            llCategoryChips.addView(addChip);
        }
    }

    /** Builds one filter chip. Selected chips use the filled (blue) badge style. */
    private TextView makeChip(String label, boolean selected, View.OnClickListener onClick) {
        float density = getResources().getDisplayMetrics().density;
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(12f);
        chip.setPadding((int) (12 * density), (int) (6 * density), (int) (12 * density), (int) (6 * density));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = (int) (8 * density);
        chip.setLayoutParams(lp);
        chip.setClickable(true);
        chip.setFocusable(true);
        if (selected) {
            chip.setTypeface(chip.getTypeface(), Typeface.BOLD);
            chip.setTextColor(ContextCompat.getColor(this, R.color.white));
            chip.setBackgroundResource(R.drawable.bg_badge_blue);
        } else {
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            chip.setBackgroundResource(R.drawable.bg_input_field);
        }
        chip.setOnClickListener(onClick);
        return chip;
    }

    /** Applies the selected category to the visible product list. */
    private void applyCategoryFilter() {
        productList.clear();
        for (Product p : allProducts) {
            String c = (p.getCategory() == null || p.getCategory().trim().isEmpty()) ? "General" : p.getCategory();
            if (selectedCategory == null || c.equals(selectedCategory)) {
                productList.add(p);
            }
        }
        if (productAdapter != null) productAdapter.notifyDataSetChanged();
        updateEmptyStates();
    }

    /** Small dialog to create a category without leaving the POS screen. */
    private void showCreateCategoryDialog() {
        final EditText input = new EditText(this);
        input.setHint("Category name (e.g. Beverages)");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        LinearLayout wrap = new LinearLayout(this);
        float density = getResources().getDisplayMetrics().density;
        wrap.setPadding((int) (24 * density), 0, (int) (24 * density), 0);
        wrap.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("New category")
                .setView(wrap)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText() != null ? input.getText().toString().trim() : "";
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Category name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createCategory(name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createCategory(String name) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("store_id", storeId);
            payload.put("name", name);
        } catch (JSONException ignored) {}

        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, ADD_CATEGORY_URL, payload,
                response -> {
                    // opt* accessors never throw, so no parse try/catch is needed
                    if (response.optBoolean("success", false)) {
                        Toast.makeText(this, response.optString("message", "Category created"),
                                Toast.LENGTH_SHORT).show();
                        JSONObject cat = response.optJSONObject("category");
                        // Select the new category so the cashier can add items to it right away
                        selectedCategory = cat != null ? cat.optString("name", name) : name;
                        renderCategoryChips();
                        applyCategoryFilter();
                    } else {
                        Toast.makeText(this, response.optString("message", "Could not create category"),
                                Toast.LENGTH_LONG).show();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Network error creating category", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    /** Swaps list ↔ empty state so a quiet store or empty cart reads as intentional. */
    private void updateEmptyStates() {
        if (emptyProducts != null) {
            boolean noProducts = productList.isEmpty();
            emptyProducts.setVisibility(noProducts ? View.VISIBLE : View.GONE);
            rvPosProducts.setVisibility(noProducts ? View.INVISIBLE : View.VISIBLE);
        }
        if (emptyCart != null) {
            boolean noCart = cartItemList.isEmpty();
            emptyCart.setVisibility(noCart ? View.VISIBLE : View.GONE);
            rvCart.setVisibility(noCart ? View.INVISIBLE : View.VISIBLE);
        }
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
        updateEmptyStates();
    }

    private void calculateTotal() {
        double total = 0.0;
        for (CartItem item : cartItemList) {
            total += item.getTotalPrice();
        }
        tvCartTotal.setText(String.format("₱%.2f", total));
        updateEmptyStates();
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
            if (btnCheckout != null) {
                btnCheckout.setEnabled(false);
                btnCheckout.setText(R.string.action_processing);
            }
            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST, PROCESS_TRANSACTION_URL, requestData,
                    response -> {
                        setCheckoutInFlight(false);
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
                                // Real store/cashier identity for the receipt header —
                                // forwarded from the dashboard that launched POS.
                                intent.putExtra("store_name", getIntent().getStringExtra("store_name"));
                                intent.putExtra("cashier_name", getIntent().getStringExtra("full_name"));
                                ScreenTransitions.open(POSActivity.this, intent);

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
                        setCheckoutInFlight(false);
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

    /** Checkout button in-flight state: disabled + "Processing…" while the POST runs. */
    private void setCheckoutInFlight(boolean inFlight) {
        if (btnCheckout == null) return;
        btnCheckout.setEnabled(!inFlight);
        btnCheckout.setText(inFlight ? R.string.action_processing : R.string.pos_checkout_action);
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
        ScreenTransitions.open(POSActivity.this, intent);
        ScreenTransitions.finish(POSActivity.this);
    }
}
