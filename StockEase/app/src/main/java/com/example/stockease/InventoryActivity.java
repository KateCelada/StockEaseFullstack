package com.example.stockease;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class InventoryActivity extends AppCompatActivity {

    private int storeId = -1;
    private static final String GET_CATEGORIES_URL = ApiConfig.getUrl("get_categories.php?store_id=");
    private static final String GET_PRODUCTS_BY_CAT_URL = ApiConfig.getUrl("get_products_by_category.php?store_id=");
    private static final String ADD_CATEGORY_URL = ApiConfig.getUrl("add_category.php");

    private RecyclerView rvCategories, rvFilteredProducts;
    private View emptyCategories;
    private View errorCategories;
    private View loadingCategories;
    private LinearLayout layoutFilteredProducts;
    private TextView tvHeaderTitle, tvHeaderSubtitle;
    private Button btnBackToCategories, btnAddCategory;

    private CategoryAdapter categoryAdapter;
    private ProductAdapter productAdapter;

    private List<Category> categoryList = new ArrayList<>();
    private List<Product> filteredProductList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);
        SidebarNav.attach(this, "inventory");

        storeId = getIntent().getIntExtra("store_id", -1);
        if (storeId <= 0) {
            returnToLogin("Missing store identity. Please sign in again.");
            return;
        }

        // Bind Views
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle);
        rvCategories = findViewById(R.id.rvCategories);
        loadingCategories = findViewById(R.id.loadingCategories);
        emptyCategories = findViewById(R.id.emptyCategories);
        errorCategories = findViewById(R.id.errorCategories);
        StateViews.bindError(errorCategories, null, null, v -> fetchCategories());
        StateViews.bindEmpty(emptyCategories,
                getString(R.string.empty_categories_title),
                getString(R.string.empty_categories_msg),
                getString(R.string.inv_empty_action),
                v -> showAddCategoryDialog());
        rvFilteredProducts = findViewById(R.id.rvFilteredProducts);
        layoutFilteredProducts = findViewById(R.id.layoutFilteredProducts);
        btnBackToCategories = findViewById(R.id.btnBackToCategories);
        btnAddCategory = findViewById(R.id.btnAddCategory);

        // Setup Categories Grid (2 Columns)
        rvCategories.setLayoutManager(new GridLayoutManager(this, 2));
        categoryAdapter = new CategoryAdapter(categoryList, this::onCategorySelected);
        rvCategories.setAdapter(categoryAdapter);

        // Setup Filtered Products List
        rvFilteredProducts.setLayoutManager(new LinearLayoutManager(this));
        productAdapter = new ProductAdapter(filteredProductList);
        rvFilteredProducts.setAdapter(productAdapter);

        if (btnBackToCategories != null) {
            btnBackToCategories.setOnClickListener(v -> showCategoriesView());
        }

        if (btnAddCategory != null) {
            btnAddCategory.setOnClickListener(v -> showAddCategoryDialog());
        }

        fetchCategories();
    }

    private void fetchCategories() {
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                GET_CATEGORIES_URL + storeId,
                null,
                response -> {
                    StateViews.hideLoading(loadingCategories);
                    StateViews.setErrorVisible(errorCategories, false);
                    categoryList.clear();
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            categoryList.add(new Category(
                                    obj.getInt("id"),
                                    obj.getString("name"),
                                    obj.optInt("product_count", 0)
                            ));
                        }
                        categoryAdapter.notifyDataSetChanged();
                        updateCategoryEmptyState();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    StateViews.hideLoading(loadingCategories);
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to load categories", Toast.LENGTH_SHORT).show();
                    showCategoriesError();
                }
        );
        queue.add(request);
    }

    /**
     * Empty state for the categories grid. Respects the drill-down: when a
     * category is open the grid is intentionally hidden, and the empty state
     * must not reappear over the filtered product list.
     */
    private void updateCategoryEmptyState() {
        boolean categoriesVisible = layoutFilteredProducts == null
                || layoutFilteredProducts.getVisibility() != View.VISIBLE;
        boolean empty = categoryList == null || categoryList.isEmpty();
        if (emptyCategories != null) {
            StateViews.setEmptyVisible(emptyCategories, categoriesVisible && empty);
        }
        if (rvCategories != null && categoriesVisible) {
            rvCategories.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void onCategorySelected(Category category) {
        tvHeaderTitle.setText(category.getName());
        tvHeaderSubtitle.setText("Showing products under " + category.getName());

        if (btnAddCategory != null) btnAddCategory.setVisibility(View.GONE);
        rvCategories.setVisibility(View.GONE);
        layoutFilteredProducts.setVisibility(View.VISIBLE);

        fetchProductsByCategory(category.getId());
    }

    private void fetchProductsByCategory(int categoryId) {
        RequestQueue queue = ApiSession.getRequestQueue(this);
        String url = GET_PRODUCTS_BY_CAT_URL + storeId + "&category_id=" + categoryId;

        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                response -> {
                    filteredProductList.clear();
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            filteredProductList.add(new Product(
                                    obj.getInt("id"),
                                    obj.getInt("store_id"),
                                    obj.optInt("category_id", 0),
                                    obj.optString("category", "Uncategorized"),
                                    obj.getString("product_name"),
                                    obj.getDouble("price"),
                                    obj.getInt("stock")
                            ));
                        }
                        productAdapter.notifyDataSetChanged();
                        if (filteredProductList.isEmpty()) {
                            Toast.makeText(this, "No products found in this category.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to load items", Toast.LENGTH_SHORT).show();
                }
        );
        queue.add(request);
    }

    private void showCategoriesView() {
        tvHeaderTitle.setText("Inventory Categories");
        tvHeaderSubtitle.setText("Select a category to view products");

        layoutFilteredProducts.setVisibility(View.GONE);
        rvCategories.setVisibility(View.VISIBLE);
        if (btnAddCategory != null) btnAddCategory.setVisibility(View.VISIBLE);
        fetchCategories(); // Refresh counts
    }

    private void showAddCategoryDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Add New Category");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("e.g. Apparel, Footwear, Electronics");
        input.setSingleLine(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String catName = input.getText().toString().trim();
            if (android.text.TextUtils.isEmpty(catName)) {
                Toast.makeText(this, "Category name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            performAddCategory(catName);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void performAddCategory(String categoryName) {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        org.json.JSONObject payload = new org.json.JSONObject();
        try {
            payload.put("store_id", storeId);
            payload.put("name", categoryName);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        com.android.volley.toolbox.JsonObjectRequest request = new com.android.volley.toolbox.JsonObjectRequest(
                Request.Method.POST,
                ADD_CATEGORY_URL,
                payload,
                response -> {
                    boolean success = response.optBoolean("success", false);
                    String message = response.optString("message", "Category created.");
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    if (success) {
                        fetchCategories();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to create category", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ScreenTransitions.open(InventoryActivity.this, intent);
        ScreenTransitions.finish(InventoryActivity.this);
    }

    /**
     * A load failed. Shows the error state with a working retry instead of
     * leaving a blank screen, which a user cannot tell apart from an empty
     * list or a broken app.
     */
    private void showCategoriesError() {
        if (emptyCategories != null) {
            StateViews.setEmptyVisible(emptyCategories, false);
        }
        StateViews.setErrorVisible(errorCategories, true);
    }

}
