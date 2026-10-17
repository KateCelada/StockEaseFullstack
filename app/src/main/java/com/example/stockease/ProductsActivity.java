package com.example.stockease;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductsActivity extends AppCompatActivity {

    private int storeId = -1;
    private int userId = -1;
    private String role = "";
    private static final String ADD_PRODUCT_URL = ApiConfig.getUrl("add_product.php");
    private static final String GET_CATEGORIES_URL = ApiConfig.getUrl("get_categories.php?store_id=");
    private static final String GET_PRODUCTS_URL = ApiConfig.getUrl("get_products.php?store_id=");

    private List<Category> categoryList = new ArrayList<>();
    private List<String> categoryNames = new ArrayList<>();
    private List<Product> allProducts = new ArrayList<>();
    private List<Product> displayedProducts = new ArrayList<>();

    private ProductAdapter productAdapter;
    private RecyclerView rvProducts;
    private View emptyProducts;
    private View errorProducts;
    private View loadingProducts;
    private EditText etSearchProduct;
    private Spinner spinnerFilterCategory;
    private TextView tvProductCountSubtitle;

    private String currentSearchQuery = "";
    private String currentCategoryFilter = "All Categories";

    /** Table pagination: rows shown per page of the product table. */
    private static final int PAGE_SIZE = 10;
    private int currentPage = 1;
    private int totalPages = 1;
    private final List<Product> filteredProducts = new ArrayList<>();
    private Button btnProductsPrev, btnProductsNext;
    private TextView tvProductsPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_products);
        SidebarNav.attach(this, "products");

        storeId = getIntent().getIntExtra("store_id", -1);
        userId = getIntent().getIntExtra("user_id", -1);
        role = getIntent().getStringExtra("role");
        if (storeId <= 0) {
            returnToLogin("Missing store identity. Please sign in again.");
            return;
        }

        tvProductCountSubtitle = findViewById(R.id.tvProductCountSubtitle);
        etSearchProduct = findViewById(R.id.etSearchProduct);
        spinnerFilterCategory = findViewById(R.id.spinnerFilterCategory);
        rvProducts = findViewById(R.id.rvProducts);
        emptyProducts = findViewById(R.id.emptyProducts);
        errorProducts = findViewById(R.id.errorProducts);
        loadingProducts = findViewById(R.id.loadingProducts);
        StateViews.bindError(errorProducts, null, null, v -> fetchProducts());
        StateViews.bindEmpty(emptyProducts,
                getString(R.string.empty_products_title),
                getString(R.string.empty_products_msg), null, null);

        if (rvProducts != null) {
            rvProducts.setLayoutManager(new LinearLayoutManager(this));
            productAdapter = new ProductAdapter(displayedProducts, this::showProductAnalyticsDialog);
            rvProducts.setAdapter(productAdapter);
        }

        Button btnAddProduct = findViewById(R.id.btnAddProduct);
        if (btnAddProduct != null) {
            if ("super_admin".equals(role)) {
                btnAddProduct.setVisibility(View.GONE);
            } else {
                btnAddProduct.setOnClickListener(v -> showAddProductDialog());
            }
        }

        btnProductsPrev = findViewById(R.id.btnProductsPrev);
        btnProductsNext = findViewById(R.id.btnProductsNext);
        tvProductsPage = findViewById(R.id.tvProductsPage);
        if (btnProductsPrev != null) {
            btnProductsPrev.setOnClickListener(v -> {
                if (currentPage > 1) {
                    currentPage--;
                    renderPage();
                }
            });
        }
        if (btnProductsNext != null) {
            btnProductsNext.setOnClickListener(v -> {
                if (currentPage < totalPages) {
                    currentPage++;
                    renderPage();
                }
            });
        }

        setupSearchAndFilter();
        fetchCategories();
        fetchProducts();

        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));
    }

    private void setupSearchAndFilter() {
        if (etSearchProduct != null) {
            etSearchProduct.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentSearchQuery = s.toString().trim().toLowerCase(Locale.getDefault());
                    currentPage = 1;
                    applyFilters();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (spinnerFilterCategory != null) {
            spinnerFilterCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    currentCategoryFilter = (String) parent.getItemAtPosition(position);
                    currentPage = 1;
                    applyFilters();
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private void applyFilters() {
        filteredProducts.clear();
        for (Product p : allProducts) {
            boolean matchesSearch = currentSearchQuery.isEmpty()
                    || p.getName().toLowerCase(Locale.getDefault()).contains(currentSearchQuery)
                    || p.getCategoryName().toLowerCase(Locale.getDefault()).contains(currentSearchQuery);

            boolean matchesCategory = "All Categories".equalsIgnoreCase(currentCategoryFilter)
                    || p.getCategoryName().equalsIgnoreCase(currentCategoryFilter);

            if (matchesSearch && matchesCategory) {
                filteredProducts.add(p);
            }
        }

        totalPages = Math.max(1, (int) Math.ceil(filteredProducts.size() / (double) PAGE_SIZE));
        if (currentPage > totalPages) {
            currentPage = totalPages;
        }
        if (currentPage < 1) {
            currentPage = 1;
        }

        renderPage();
    }

    /** Renders the slice of the filtered table that belongs to the current page. */
    private void renderPage() {
        displayedProducts.clear();
        int from = (currentPage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, filteredProducts.size());
        for (int i = from; i < to; i++) {
            displayedProducts.add(filteredProducts.get(i));
        }

        if (tvProductCountSubtitle != null) {
            int firstRow = filteredProducts.isEmpty() ? 0 : from + 1;
            tvProductCountSubtitle.setText("Showing " + firstRow + "-" + to + " of "
                    + filteredProducts.size() + " products • Page " + currentPage + " of " + totalPages
                    + " • Tap a row for profit & charts");
        }

        if (tvProductsPage != null) {
            tvProductsPage.setText("Page " + currentPage + " of " + totalPages);
        }
        if (btnProductsPrev != null) {
            btnProductsPrev.setEnabled(currentPage > 1);
        }
        if (btnProductsNext != null) {
            btnProductsNext.setEnabled(currentPage < totalPages);
        }

        if (productAdapter != null) {
            productAdapter.notifyDataSetChanged();
        }
        updateEmptyState();
    }

    /**
     * Two different empty states: no products at all, versus a filter or search
     * that matches nothing. The second offers a way out (clear the filter).
     */
    /**
     * A load failed. Shows the error state with a working retry instead of
     * leaving a blank screen, which a user cannot tell apart from an empty
     * catalog or a broken app.
     */
    private void showProductsError() {
        if (emptyProducts != null) {
            StateViews.setEmptyVisible(emptyProducts, false);
        }
        if (rvProducts != null) {
            rvProducts.setVisibility(View.INVISIBLE);
        }
        StateViews.setErrorVisible(errorProducts, true);
    }

    private void updateEmptyState() {
        boolean nothingAtAll = allProducts == null || allProducts.isEmpty();
        boolean nothingShown = displayedProducts == null || displayedProducts.isEmpty();
        if (emptyProducts == null) return;

        if (nothingShown) {
            if (nothingAtAll) {
                StateViews.bindEmpty(emptyProducts,
                        getString(R.string.empty_products_title),
                        getString(R.string.empty_products_msg), null, null);
            } else {
                StateViews.bindEmpty(emptyProducts,
                        getString(R.string.empty_products_no_match_title),
                        getString(R.string.empty_products_no_match_msg),
                        getString(R.string.state_action_clear),
                        v -> clearFilters());
            }
        }
        StateViews.setEmptyVisible(emptyProducts, nothingShown);
        if (rvProducts != null) {
            rvProducts.setVisibility(nothingShown ? View.INVISIBLE : View.VISIBLE);
        }
    }

    /** Drops the active search and category filter so the full catalog returns. */
    private void clearFilters() {
        currentSearchQuery = "";
        currentCategoryFilter = "All Categories";
        currentPage = 1;
        if (etSearchProduct != null) {
            etSearchProduct.setText("");
        }
        if (spinnerFilterCategory != null) {
            spinnerFilterCategory.setSelection(0);
        }
        applyFilters();
    }

    private void fetchCategories() {
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                GET_CATEGORIES_URL + storeId,
                null,
                response -> {
                    categoryList.clear();
                    categoryNames.clear();
                    categoryNames.add("All Categories");

                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            Category cat = new Category(
                                    obj.getInt("id"),
                                    obj.getString("name"),
                                    obj.optInt("product_count", 0)
                            );
                            categoryList.add(cat);
                            categoryNames.add(cat.getName());
                        }

                        if (spinnerFilterCategory != null) {
                            ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(
                                    this, android.R.layout.simple_spinner_item, categoryNames);
                            filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            spinnerFilterCategory.setAdapter(filterAdapter);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Log.e("FetchCategories", "Failed to load categories");
                }
        );
        queue.add(request);
    }

    private void fetchProducts() {
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                GET_PRODUCTS_URL + storeId,
                null,
                response -> {
                    StateViews.hideLoading(loadingProducts);
                    StateViews.setErrorVisible(errorProducts, false);
                    allProducts.clear();
                    try {
                        if (!response.optBoolean("success", false)) {
                            Toast.makeText(this, response.optString("message", "Failed to load products"), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        org.json.JSONArray products = response.getJSONArray("products");
                        for (int i = 0; i < products.length(); i++) {
                            JSONObject obj = products.getJSONObject(i);
                            int soldUnits = obj.optInt("total_sold_units", 0);
                            double progGross = obj.optDouble("progressive_gross", 0.0);
                            double progNet = obj.optDouble("progressive_net", 0.0);
                            allProducts.add(new Product(
                                    obj.getInt("id"),
                                    obj.getInt("store_id"),
                                    obj.optInt("category_id", 0),
                                    obj.optString("category", "General"),
                                    obj.getString("product_name"),
                                    obj.getDouble("price"),
                                    obj.optDouble("cost_price", 0.0),
                                    obj.getInt("stock"),
                                    soldUnits,
                                    progGross,
                                    progNet,
                                    obj.optInt("min_stock", Product.DEFAULT_MIN_STOCK)
                            ));
                        }
                        applyFilters();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    StateViews.hideLoading(loadingProducts);
                    if (ApiSession.handleAuthError(this, error)) return;
                    Log.e("FetchProducts", "Failed to load product list");
                    showProductsError();
                }
        );
        queue.add(request);
    }

    private void showAddProductDialog() {
        if ("super_admin".equals(role)) {
            Toast.makeText(this, "Super Admin cannot add products", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Product");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_product, null);
        AutoCompleteTextView actvCategory = view.findViewById(R.id.actvCategory);
        EditText etName = view.findViewById(R.id.etProductName);
        EditText etCostPrice = view.findViewById(R.id.etProductCostPrice);
        EditText etPrice = view.findViewById(R.id.etProductPrice);
        EditText etQty = view.findViewById(R.id.etProductQuantity);
        EditText etMinStock = view.findViewById(R.id.etProductMinStock);

        // Populate category dropdown and autofill suggestions
        List<String> suggestionList = new ArrayList<>();
        for (Category c : categoryList) {
            suggestionList.add(c.getName());
        }

        ArrayAdapter<String> autoCatAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, suggestionList);
        if (actvCategory != null) {
            actvCategory.setAdapter(autoCatAdapter);
            // Click to show entire list as dropdown
            actvCategory.setOnClickListener(v -> actvCategory.showDropDown());
        }

        builder.setView(view);
        builder.setPositiveButton("Add Product", (dialog, which) -> {
            String catNameInput = actvCategory != null ? actvCategory.getText().toString().trim() : "";
            String name = etName != null ? etName.getText().toString().trim() : "";
            String costStr = etCostPrice != null ? etCostPrice.getText().toString().trim() : "0";
            String priceStr = etPrice != null ? etPrice.getText().toString().trim() : "0";
            String qtyStr = etQty != null ? etQty.getText().toString().trim() : "0";
            String minStockStr = etMinStock != null ? etMinStock.getText().toString().trim() : "";

            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(priceStr)) {
                Toast.makeText(this, "Product name and selling price are required", Toast.LENGTH_SHORT).show();
                return;
            }

            double price = Double.parseDouble(priceStr);
            double cost = TextUtils.isEmpty(costStr) ? 0.0 : Double.parseDouble(costStr);
            int qty = TextUtils.isEmpty(qtyStr) ? 0 : Integer.parseInt(qtyStr);
            int minStock = TextUtils.isEmpty(minStockStr)
                    ? Product.DEFAULT_MIN_STOCK
                    : Math.max(0, Integer.parseInt(minStockStr));

            // Find matching category id or resolve
            int matchedCategoryId = 0;
            for (Category c : categoryList) {
                if (c.getName().equalsIgnoreCase(catNameInput)) {
                    matchedCategoryId = c.getId();
                    break;
                }
            }

            performAddProduct(matchedCategoryId, catNameInput, name, price, cost, qty, minStock);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    private void performAddProduct(int categoryId, String catName, String name, double price, double costPrice, int quantity, int minStock) {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JSONObject postData = new JSONObject();
        try {
            postData.put("store_id", storeId);
            if (categoryId > 0) {
                postData.put("category_id", categoryId);
            }
            postData.put("category_name", catName);
            postData.put("category", catName);
            postData.put("product_name", name);
            postData.put("price", price);
            postData.put("cost_price", costPrice);
            postData.put("stock", quantity);
            postData.put("min_stock", minStock);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                ADD_PRODUCT_URL,
                postData,
                response -> {
                    try {
                        boolean success = response.getBoolean("success");
                        String message = response.optString("message", "Product operation complete.");
                        Toast.makeText(ProductsActivity.this, message, Toast.LENGTH_SHORT).show();
                        if (success) {
                            fetchCategories();
                            fetchProducts();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(ProductsActivity.this, error)) return;
                    String errMsg = "Failed to add product";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String errBody = new String(error.networkResponse.data);
                            JSONObject obj = new JSONObject(errBody);
                            errMsg = obj.optString("message", errMsg);
                            if ("SUBSCRIPTION_LIMIT_REACHED".equals(obj.optString("code"))) {
                                new AlertDialog.Builder(this)
                                        .setTitle("★ Product Limit Reached (Free Plan)")
                                        .setMessage(errMsg + "\n\nUpgrade to StockEase Premium to unlock unlimited products catalog, multi-store stock transfers, and real-time velocity analytics.")
                                        .setPositiveButton("Upgrade Plan", (d, w) -> {
                                            Intent subIntent = new Intent(this, SubscriptionActivity.class);
                                            subIntent.putExtra("store_id", storeId);
                                            subIntent.putExtra("user_id", userId);
                                            subIntent.putExtra("role", role);
                                            ScreenTransitions.open(ProductsActivity.this, subIntent);
                                        })
                                        .setNegativeButton("Maybe Later", null)
                                        .show();
                                return;
                            }
                        } catch (Exception ignored) {}
                    }
                    Toast.makeText(ProductsActivity.this, errMsg, Toast.LENGTH_LONG).show();
                }
        );

        queue.add(request);
    }

    private void showProductAnalyticsDialog(Product product) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_product_analytics, null);

        TextView tvName = dialogView.findViewById(R.id.tvAnalyticsProductName);
        TextView tvCategory = dialogView.findViewById(R.id.tvAnalyticsCategory);
        TextView tvProgGross = dialogView.findViewById(R.id.tvAnalyticsProgressiveGross);
        TextView tvProgNet = dialogView.findViewById(R.id.tvAnalyticsProgressiveNet);
        TextView tvTotalSold = dialogView.findViewById(R.id.tvAnalyticsTotalSold);
        TextView tvCost = dialogView.findViewById(R.id.tvAnalyticsCost);
        TextView tvPrice = dialogView.findViewById(R.id.tvAnalyticsPrice);
        TextView tvProfit = dialogView.findViewById(R.id.tvAnalyticsProfit);
        TextView tvMargin = dialogView.findViewById(R.id.tvAnalyticsMargin);
        SimpleLineChartView chart = dialogView.findViewById(R.id.chartProductTrend);
        Button btnClose = dialogView.findViewById(R.id.btnCloseAnalytics);

        View btnDownloadTrendChart = dialogView.findViewById(R.id.btnDownloadTrendChart);
        if (btnDownloadTrendChart != null) {
            btnDownloadTrendChart.setOnClickListener(v -> ExportUtil.saveChartPng(
                    this, chart, "stockease_product_trend_" + product.getName()));
        }

        tvName.setText(product.getName());
        tvCategory.setText("Category: " + product.getCategoryName() + " • Current Stock: " + product.getStock());
        tvCost.setText(String.format(Locale.getDefault(), "₱%.2f", product.getCostPrice()));
        tvPrice.setText(String.format(Locale.getDefault(), "₱%.2f", product.getPrice()));

        double unitProfit = product.getGrossProfit();
        tvProfit.setText(String.format(Locale.getDefault(), "%s₱%.2f", unitProfit >= 0 ? "+" : "-", Math.abs(unitProfit)));

        double margin = product.getPrice() > 0 ? (unitProfit / product.getPrice()) * 100 : 0;
        tvMargin.setText(String.format(Locale.getDefault(), "%.1f%%", margin));

        if (tvProgGross != null) {
            tvProgGross.setText(String.format(Locale.getDefault(), "₱%.2f", product.getProgressiveGross()));
        }
        if (tvProgNet != null) {
            tvProgNet.setText(String.format(Locale.getDefault(), "₱%.2f", product.getProgressiveNet()));
        }
        if (tvTotalSold != null) {
            tvTotalSold.setText("Lifetime Sold: " + product.getTotalSoldUnits() + " units • Continually increments with sales");
        }

        // Fetch live product analytics and 7-day progressive earnings trajectory
        RequestQueue queue = ApiSession.getRequestQueue(this);
        String analyticsUrl = ApiConfig.getUrl("get_product_analytics.php?product_id=" + product.getId());
        JsonObjectRequest req = new JsonObjectRequest(
                Request.Method.GET,
                analyticsUrl,
                null,
                resp -> {
                    if (resp.optBoolean("success", false)) {
                        double pg = resp.optDouble("progressive_gross", product.getProgressiveGross());
                        double pn = resp.optDouble("progressive_net", product.getProgressiveNet());
                        int sold = resp.optInt("total_sold", product.getTotalSoldUnits());

                        if (tvProgGross != null) tvProgGross.setText(String.format(Locale.getDefault(), "₱%.2f", pg));
                        if (tvProgNet != null) tvProgNet.setText(String.format(Locale.getDefault(), "₱%.2f", pn));
                        if (tvTotalSold != null) tvTotalSold.setText("Lifetime Sold: " + sold + " units • Continually increments with sales");

                        JSONArray history = resp.optJSONArray("earnings_history");
                        if (history != null && chart != null) {
                            List<Float> pts = new ArrayList<>();
                            List<String> lbls = new ArrayList<>();
                            for (int i = 0; i < history.length(); i++) {
                                JSONObject hObj = history.optJSONObject(i);
                                if (hObj != null) {
                                    pts.add((float) hObj.optDouble("daily_gross", 0.0));
                                    lbls.add(hObj.optString("label", "Day"));
                                }
                            }
                            chart.setData(pts, lbls);
                        }
                    }
                },
                err -> {
                    // Fallback to static baseline trajectory if offline
                    if (chart != null) {
                        List<Float> pts = new ArrayList<>();
                        List<String> lbls = new ArrayList<>();
                        float base = (float) Math.max(0, product.getProgressiveGross());
                        pts.add(base * 0.1f); pts.add(base * 0.25f); pts.add(base * 0.4f);
                        pts.add(base * 0.6f); pts.add(base * 0.75f); pts.add(base * 0.9f);
                        pts.add(base * 1.0f);
                        lbls.add("D-6"); lbls.add("D-5"); lbls.add("D-4"); lbls.add("D-3");
                        lbls.add("D-2"); lbls.add("D-1"); lbls.add("Today");
                        chart.setData(pts, lbls);
                    }
                }
        );
        queue.add(req);

        Button btnEdit = dialogView.findViewById(R.id.btnEditProductDetails);
        Button btnRestock = dialogView.findViewById(R.id.btnRestockProduct);

        AlertDialog dialog = builder.setView(dialogView).create();
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnEdit != null) {
            btnEdit.setOnClickListener(v -> showEditProductDialog(product, dialog));
        }

        if (btnRestock != null) {
            btnRestock.setOnClickListener(v -> showRestockProductDialog(product, dialog));
        }

        dialog.show();
    }

    private void showEditProductDialog(Product product, AlertDialog parentDialog) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Edit Product & Prices");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 24, 40, 16);

        EditText etName = new EditText(this);
        etName.setHint("Product Name");
        etName.setText(product.getName());
        layout.addView(etName);

        EditText etCost = new EditText(this);
        etCost.setHint("Cost Price (Buying Price)");
        etCost.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etCost.setText(String.format(Locale.US, "%.2f", product.getCostPrice()));
        layout.addView(etCost);

        EditText etPrice = new EditText(this);
        etPrice.setHint("Selling Price");
        etPrice.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etPrice.setText(String.format(Locale.US, "%.2f", product.getPrice()));
        layout.addView(etPrice);

        EditText etStock = new EditText(this);
        etStock.setHint("Stock Quantity");
        etStock.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etStock.setText(String.valueOf(product.getStock()));
        layout.addView(etStock);

        EditText etMinStock = new EditText(this);
        etMinStock.setHint("Minimum Stock (low stock alert)");
        etMinStock.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etMinStock.setText(String.valueOf(product.getMinStock()));
        layout.addView(etMinStock);

        b.setView(layout);
        b.setPositiveButton("Save Changes", (d, w) -> {
            String newName = etName.getText().toString().trim();
            String costStr = etCost.getText().toString().trim();
            String priceStr = etPrice.getText().toString().trim();
            String stockStr = etStock.getText().toString().trim();
            String minStockStr = etMinStock.getText().toString().trim();

            if (TextUtils.isEmpty(newName) || TextUtils.isEmpty(priceStr)) {
                Toast.makeText(this, "Product name and price are required", Toast.LENGTH_SHORT).show();
                return;
            }

            double p = Double.parseDouble(priceStr);
            double c = TextUtils.isEmpty(costStr) ? 0.0 : Double.parseDouble(costStr);
            int s = TextUtils.isEmpty(stockStr) ? product.getStock() : Integer.parseInt(stockStr);
            int minStock = TextUtils.isEmpty(minStockStr)
                    ? product.getMinStock()
                    : Math.max(0, Integer.parseInt(minStockStr));

            JSONObject payload = new JSONObject();
            try {
                payload.put("product_id", product.getId());
                payload.put("product_name", newName);
                payload.put("price", p);
                payload.put("cost_price", c);
                payload.put("stock", s);
                payload.put("min_stock", minStock);
                payload.put("category_name", product.getCategoryName());
            } catch (JSONException ignored) {}

            RequestQueue q = ApiSession.getRequestQueue(this);
            JsonObjectRequest req = new JsonObjectRequest(
                    Request.Method.POST,
                    ApiConfig.getUrl("update_product.php"),
                    payload,
                    res -> {
                        Toast.makeText(this, res.optString("message", "Product updated!"), Toast.LENGTH_SHORT).show();
                        if (parentDialog != null) parentDialog.dismiss();
                        fetchProducts();
                    },
                    err -> Toast.makeText(this, "Failed to update product details", Toast.LENGTH_SHORT).show()
            );
            q.add(req);
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void showRestockProductDialog(Product product, AlertDialog parentDialog) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Add Incoming Stock");
        b.setMessage("Current stock on hand for \"" + product.getName() + "\": " + product.getStock() + " units.\n\nEnter number of units arriving to add:");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 16, 40, 16);

        EditText etQty = new EditText(this);
        etQty.setHint("Quantity arriving (e.g. 20)");
        etQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(etQty);

        b.setView(layout);
        b.setPositiveButton("Add to Stock", (d, w) -> {
            String qtyStr = etQty.getText().toString().trim();
            if (TextUtils.isEmpty(qtyStr)) return;
            int addQty = Integer.parseInt(qtyStr);
            if (addQty <= 0) {
                Toast.makeText(this, "Please enter a positive quantity", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject payload = new JSONObject();
            try {
                payload.put("store_id", storeId);
                payload.put("product_id", product.getId());
                payload.put("quantity", addQty);
            } catch (JSONException ignored) {}

            RequestQueue q = ApiSession.getRequestQueue(this);
            JsonObjectRequest req = new JsonObjectRequest(
                    Request.Method.POST,
                    ApiConfig.getUrl("restock_product.php"),
                    payload,
                    res -> {
                        Toast.makeText(this, "Stock added successfully! Total is now " + (product.getStock() + addQty), Toast.LENGTH_LONG).show();
                        if (parentDialog != null) parentDialog.dismiss();
                        fetchProducts();
                    },
                    err -> Toast.makeText(this, "Failed to add arriving stock", Toast.LENGTH_SHORT).show()
            );
            q.add(req);
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ScreenTransitions.open(ProductsActivity.this, intent);
        ScreenTransitions.finish(ProductsActivity.this);
    }
}
