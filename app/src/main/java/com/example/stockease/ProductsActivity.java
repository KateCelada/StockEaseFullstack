package com.example.stockease;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
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
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ProductsActivity extends AppCompatActivity {

    private int storeId = -1;
    private String role = "";
    private static final String ADD_PRODUCT_URL = ApiConfig.getUrl("add_product.php");
    private static final String GET_CATEGORIES_URL = ApiConfig.getUrl("get_categories.php?store_id=");
    private static final String GET_PRODUCTS_URL = ApiConfig.getUrl("get_products.php?store_id=");

    private List<Category> categoryList = new ArrayList<>();
    private List<Product> productList = new ArrayList<>();
    private ProductAdapter productAdapter;
    private RecyclerView rvProducts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_products);

        storeId = getIntent().getIntExtra("store_id", -1);
        role = getIntent().getStringExtra("role");
        if (storeId <= 0) {
            returnToLogin("Missing store identity. Please sign in again.");
            return;
        }

        rvProducts = findViewById(R.id.rvProducts);
        if (rvProducts != null) {
            rvProducts.setLayoutManager(new LinearLayoutManager(this));
            productAdapter = new ProductAdapter(productList);
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

        fetchCategories();
        fetchProducts();
    }

    private void fetchCategories() {
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                GET_CATEGORIES_URL + storeId,
                null,
                response -> {
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
                    productList.clear();
                    try {
                        if (!response.optBoolean("success", false)) {
                            Toast.makeText(this, response.optString("message", "Failed to load products"), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        org.json.JSONArray products = response.getJSONArray("products");
                        for (int i = 0; i < products.length(); i++) {
                            JSONObject obj = products.getJSONObject(i);
                            productList.add(new Product(
                                    obj.getInt("id"),
                                    obj.getInt("store_id"),
                                    obj.optInt("category_id", 0),
                                    obj.optString("category", "Uncategorized"),
                                    obj.getString("product_name"),
                                    obj.getDouble("price"),
                                    obj.getInt("stock")
                            ));
                        }
                        if (productAdapter != null) {
                            productAdapter.notifyDataSetChanged();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Log.e("FetchProducts", "Failed to load product list");
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
        Spinner spinnerCategory = view.findViewById(R.id.spinnerCategory);
        EditText etName = view.findViewById(R.id.etProductName);
        EditText etPrice = view.findViewById(R.id.etProductPrice);
        EditText etQty = view.findViewById(R.id.etProductQuantity);

        ArrayAdapter<Category> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categoryList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spinnerCategory != null) {
            spinnerCategory.setAdapter(adapter);
        }

        View btnQuickAddCategory = view.findViewById(R.id.btnQuickAddCategory);
        if (btnQuickAddCategory != null) {
            btnQuickAddCategory.setOnClickListener(v -> showQuickAddCategoryDialog(adapter, spinnerCategory));
        }

        builder.setView(view);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = etName != null ? etName.getText().toString().trim() : "";
            String priceStr = etPrice != null ? etPrice.getText().toString().trim() : "0";
            String qtyStr = etQty != null ? etQty.getText().toString().trim() : "0";

            int categoryId = 0;
            if (spinnerCategory != null && spinnerCategory.getSelectedItem() != null) {
                Category selectedCat = (Category) spinnerCategory.getSelectedItem();
                categoryId = selectedCat.getId();
            }

            if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(priceStr)) {
                double price = Double.parseDouble(priceStr);
                int qty = TextUtils.isEmpty(qtyStr) ? 0 : Integer.parseInt(qtyStr);
                performAddProduct(categoryId, name, price, qty);
            } else {
                Toast.makeText(this, "Product name and price are required", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    private void performAddProduct(int categoryId, String name, double price, int quantity) {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JSONObject postData = new JSONObject();
        try {
            postData.put("store_id", storeId);
            postData.put("category_id", categoryId);
            postData.put("product_name", name);
            postData.put("price", price);
            postData.put("stock", quantity);
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
                            fetchProducts(); // Auto-refresh list upon addition
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(ProductsActivity.this, error)) return;
                    Toast.makeText(ProductsActivity.this, "Failed to connect", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    private void showQuickAddCategoryDialog(ArrayAdapter<Category> adapter, Spinner spinnerCategory) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Category");

        final EditText input = new EditText(this);
        input.setHint("e.g. Apparel, Footwear, Electronics");
        input.setSingleLine(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        builder.setView(input);

        builder.setPositiveButton("Create", (dialog, which) -> {
            String catName = input.getText().toString().trim();
            if (TextUtils.isEmpty(catName)) {
                Toast.makeText(this, "Category name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            performQuickAddCategory(catName, adapter, spinnerCategory);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void performQuickAddCategory(String catName, ArrayAdapter<Category> adapter, Spinner spinnerCategory) {
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JSONObject payload = new JSONObject();
        try {
            payload.put("store_id", storeId);
            payload.put("name", catName);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                ApiConfig.getUrl("add_category.php"),
                payload,
                response -> {
                    boolean success = response.optBoolean("success", false);
                    String message = response.optString("message", "Category added.");
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    if (success) {
                        JSONObject catObj = response.optJSONObject("category");
                        int newId = catObj != null ? catObj.optInt("id", 0) : 0;
                        String name = catObj != null ? catObj.optString("name", catName) : catName;
                        Category newCat = new Category(newId, name, 0);
                        categoryList.add(newCat);
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                        if (spinnerCategory != null) {
                            spinnerCategory.setSelection(categoryList.size() - 1);
                        }
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
        startActivity(intent);
        finish();
    }
}
