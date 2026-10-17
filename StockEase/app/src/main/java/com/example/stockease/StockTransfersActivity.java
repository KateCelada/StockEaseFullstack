package com.example.stockease;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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

public class StockTransfersActivity extends AppCompatActivity {

    public static final String EXTRA_PREFILL_SOURCE_STORE_ID = "prefill_source_store_id";
    public static final String EXTRA_PREFILL_DEST_STORE_ID = "prefill_dest_store_id";
    public static final String EXTRA_PREFILL_PRODUCT_ID = "prefill_product_id";
    public static final String EXTRA_PREFILL_QTY = "prefill_qty";

    private static final String TRANSFERS_URL = ApiConfig.getUrl("manage_transfers.php");
    private static final String STORES_URL = ApiConfig.getUrl("manage_stores.php?action=fetch");
    private static final String PRODUCTS_URL = ApiConfig.getUrl("get_products.php");

    private int userId = -1;
    private int storeId = -1;
    private String role = "";
    private String fullName = "";
    private String storeName = "";

    private View cardNewTransfer;
    private View llFromStoreColumn;
    private TextView tvFromStoreLabel;
    private Button btnToggleNewTransfer;
    private Spinner spFromStore, spToStore, spTransferProduct;
    private TextView tvAvailableStock, tvTransfersStatus;
    private EditText etTransferQuantity, etTransferNotes;
    private Button btnSubmitTransfer, btnRefreshTransfers;
    private Button btnAddTransferItem;
    private LinearLayout llSelectedItems;
    private TextView tvSelectedItemsLabel;
    private LinearLayout llTransferRows;

    /** Items added to the current multi-select request. */
    private final List<RequestItem> selectedItems = new ArrayList<>();

    /**
     * Request mode: store admins and inventory staff ask for stock for their OWN
     * branch. The manager approves and picks the donor branch afterwards.
     */
    private boolean requestMode = false;

    private static class StoreOption {
        final int id;
        final String name;

        StoreOption(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static class ProductOption {
        final int id;
        final String name;
        final int stock;
        final double basePrice;
        final double sellingPrice;
        final String category;

        ProductOption(int id, String name, int stock, double basePrice, double sellingPrice, String category) {
            this.id = id;
            this.name = name;
            this.stock = stock;
            this.basePrice = basePrice;
            this.sellingPrice = sellingPrice;
            this.category = category;
        }

        @Override
        public String toString() {
            return name + " (" + stock + " in stock)";
        }
    }

    /** One line of a multi-item transfer request. */
    private static class RequestItem {
        final ProductOption product;
        int quantity;

        RequestItem(ProductOption product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }
    }

    private final List<StoreOption> fromStoreList = new ArrayList<>();
    private final List<StoreOption> toStoreList = new ArrayList<>();
    private final List<ProductOption> productList = new ArrayList<>();

    private int prefillSourceStoreId = -1;
    private int prefillDestStoreId = -1;
    private int prefillProductId = -1;
    private int prefillQty = 0;
    private boolean prefillConsumed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_transfers);
        SidebarNav.attach(this, "transfers");

        if (getIntent() != null) {
            userId = getIntent().getIntExtra("user_id", -1);
            storeId = getIntent().getIntExtra("store_id", -1);
            role = getIntent().getStringExtra("role");
            fullName = getIntent().getStringExtra("full_name");
            storeName = getIntent().getStringExtra("store_name");

            prefillSourceStoreId = getIntent().getIntExtra(EXTRA_PREFILL_SOURCE_STORE_ID, -1);
            prefillDestStoreId = getIntent().getIntExtra(EXTRA_PREFILL_DEST_STORE_ID, -1);
            prefillProductId = getIntent().getIntExtra(EXTRA_PREFILL_PRODUCT_ID, -1);
            prefillQty = getIntent().getIntExtra(EXTRA_PREFILL_QTY, 0);
        }

        if (userId <= 0 || (!"super_admin".equals(role) && !"store_admin".equals(role) && !"manager".equals(role) && !"inventory_checker".equals(role))) {
            returnToLogin("Stock Transfers are restricted to Manager, Store Admin, and Inventory accounts.");
            return;
        }

        requestMode = "store_admin".equals(role) || "inventory_checker".equals(role);

        ImageButton btnTransfersBack = findViewById(R.id.btnTransfersBack);
        if (btnTransfersBack != null) {
            btnTransfersBack.setOnClickListener(v -> finish());
        }

        cardNewTransfer = findViewById(R.id.cardNewTransfer);
        btnToggleNewTransfer = findViewById(R.id.btnToggleNewTransfer);
        spFromStore = findViewById(R.id.spFromStore);
        spToStore = findViewById(R.id.spToStore);
        spTransferProduct = findViewById(R.id.spTransferProduct);
        tvAvailableStock = findViewById(R.id.tvAvailableStock);
        etTransferQuantity = findViewById(R.id.etTransferQuantity);
        etTransferNotes = findViewById(R.id.etTransferNotes);
        btnSubmitTransfer = findViewById(R.id.btnSubmitTransfer);
        btnRefreshTransfers = findViewById(R.id.btnRefreshTransfers);
        llTransferRows = findViewById(R.id.llTransferRows);
        tvTransfersStatus = findViewById(R.id.tvTransfersStatus);
        llFromStoreColumn = findViewById(R.id.llFromStoreColumn);
        tvFromStoreLabel = findViewById(R.id.tvFromStoreLabel);
        btnAddTransferItem = findViewById(R.id.btnAddTransferItem);
        llSelectedItems = findViewById(R.id.llSelectedItems);
        tvSelectedItemsLabel = findViewById(R.id.tvSelectedItemsLabel);

        if (requestMode) {
            // The donor branch is chosen by the manager on approval, so the
            // source selector is not shown; the destination is the own branch.
            if (llFromStoreColumn != null) llFromStoreColumn.setVisibility(View.GONE);
            TextView tvToLabel = findViewById(R.id.tvToStoreLabel);
            if (tvToLabel != null) tvToLabel.setText("To Store (My Branch)");
            if (tvAvailableStock != null) {
                tvAvailableStock.setText("The manager will choose which branch fulfills this request.");
            }
        }

        if (btnToggleNewTransfer != null) {
            btnToggleNewTransfer.setOnClickListener(v -> {
                if (cardNewTransfer.getVisibility() == View.VISIBLE) {
                    cardNewTransfer.setVisibility(View.GONE);
                    btnToggleNewTransfer.setText("+ New Transfer Request");
                } else {
                    cardNewTransfer.setVisibility(View.VISIBLE);
                    btnToggleNewTransfer.setText("- Hide Form");
                }
            });
        }

        if (btnToggleNewTransfer != null && requestMode) {
            btnToggleNewTransfer.setText("+ New Transfer Request");
        }

        if (btnRefreshTransfers != null) {
            btnRefreshTransfers.setOnClickListener(v -> loadTransferHistory());
        }

        if (btnSubmitTransfer != null) {
            btnSubmitTransfer.setOnClickListener(v -> submitNewTransfer());
        }

        if (btnAddTransferItem != null) {
            btnAddTransferItem.setOnClickListener(v -> addSelectedItem());
        }

        renderSelectedItems();

        setupStoreSpinners();
        loadTransferHistory();
        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));
    }

    private void setupStoreSpinners() {
        if (requestMode) {
            // Request mode: the destination is always the staff member's own branch
            StoreOption ownStore = new StoreOption(storeId, (storeName != null && !storeName.trim().isEmpty()) ? storeName : ("Branch " + storeId));
            toStoreList.clear();
            toStoreList.add(ownStore);
            ArrayAdapter<StoreOption> adapterTo = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, toStoreList);
            spToStore.setAdapter(adapterTo);
            spToStore.setEnabled(false);
            loadProductsForSourceStore(storeId);

            if (prefillProductId > 0) {
                // Deferred: product adapter is populated asynchronously
            }
            return;
        }

        RequestQueue queue = ApiSession.getRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                STORES_URL,
                null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONArray arr = response.getJSONArray("stores");
                            fromStoreList.clear();
                            toStoreList.clear();

                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject s = arr.getJSONObject(i);
                                int sid = s.getInt("id");
                                String sname = s.optString("store_name", "Store #" + sid);
                                StoreOption opt = new StoreOption(sid, sname);
                                fromStoreList.add(opt);
                                toStoreList.add(opt);
                            }

                            ArrayAdapter<StoreOption> adapterFrom = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fromStoreList);
                            ArrayAdapter<StoreOption> adapterTo = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, toStoreList);

                            spFromStore.setAdapter(adapterFrom);
                            spToStore.setAdapter(adapterTo);

                            // Apply prefill if passed from recommendation
                            if (prefillSourceStoreId > 0) {
                                for (int i = 0; i < fromStoreList.size(); i++) {
                                    if (fromStoreList.get(i).id == prefillSourceStoreId) {
                                        spFromStore.setSelection(i);
                                        break;
                                    }
                                }
                            }

                            if (prefillDestStoreId > 0) {
                                for (int i = 0; i < toStoreList.size(); i++) {
                                    if (toStoreList.get(i).id == prefillDestStoreId) {
                                        spToStore.setSelection(i);
                                        break;
                                    }
                                }
                            } else if (toStoreList.size() > 1) {
                                spToStore.setSelection(1);
                            }

                            if (prefillQty > 0 && etTransferQuantity != null) {
                                etTransferQuantity.setText(String.valueOf(prefillQty));
                            }

                            spFromStore.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                                @Override
                                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                    StoreOption selected = fromStoreList.get(position);
                                    loadProductsForSourceStore(selected.id);
                                }

                                @Override
                                public void onNothingSelected(AdapterView<?> parent) {}
                            });

                            if (!fromStoreList.isEmpty()) {
                                int selectedPos = spFromStore.getSelectedItemPosition();
                                if (selectedPos >= 0 && selectedPos < fromStoreList.size()) {
                                    loadProductsForSourceStore(fromStoreList.get(selectedPos).id);
                                }
                            }
                        }
                    } catch (JSONException e) {
                        Toast.makeText(this, "Failed to parse stores list", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> ApiSession.handleAuthError(this, error)
        );

        queue.add(request);
    }

    private void loadProductsForSourceStore(int sourceStoreId) {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                PRODUCTS_URL + "?store_id=" + sourceStoreId,
                null,
                response -> {
                    productList.clear();
                    JSONArray arr = response.optJSONArray("products");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject p = arr.optJSONObject(i);
                            if (p != null) {
                                productList.add(new ProductOption(
                                        p.optInt("id", 0),
                                        p.optString("product_name", "Product"),
                                        p.optInt("stock", 0),
                                        p.optDouble("cost_price", 0.0),
                                        p.optDouble("price", 0.0),
                                        p.optString("category", "General")
                                ));
                            }
                        }
                    }

                    if (productList.isEmpty()) {
                        productList.add(new ProductOption(-1, "No products available in this store", 0, 0.0, 0.0, "General"));
                    }

                    ArrayAdapter<ProductOption> prodAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, productList);
                    spTransferProduct.setAdapter(prodAdapter);

                    if (prefillProductId > 0) {
                        for (int i = 0; i < productList.size(); i++) {
                            if (productList.get(i).id == prefillProductId) {
                                spTransferProduct.setSelection(i);
                                // Recommendation flow: put the suggested line straight into the request
                                if (!prefillConsumed && prefillQty > 0) {
                                    prefillConsumed = true;
                                    selectedItems.add(new RequestItem(productList.get(i), prefillQty));
                                    renderSelectedItems();
                                }
                                break;
                            }
                        }
                    }

                    spTransferProduct.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (position >= 0 && position < productList.size()) {
                                showProductPrices(productList.get(position));
                            }
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });

                    if (!productList.isEmpty()) {
                        showProductPrices(productList.get(0));
                    }
                },
                error -> {
                    if (tvAvailableStock != null) tvAvailableStock.setText("Stock: —");
                }
        );

        queue.add(request);
    }

    /** Formats a peso amount for the transfer form. */
    private static String peso(double value) {
        return "₱" + String.format(Locale.US, "%.2f", value);
    }

    /** Shows on-hand stock next to the base (cost) and selling price of the selected product. */
    private void showProductPrices(ProductOption opt) {
        if (tvAvailableStock == null) return;
        tvAvailableStock.setText("Stock: " + opt.stock + " • Base: " + peso(opt.basePrice)
                + " • Selling: " + peso(opt.sellingPrice));
    }

    /** Adds the currently picked product and quantity to the multi-select request list. */
    private void addSelectedItem() {
        int prodPos = spTransferProduct.getSelectedItemPosition();
        if (prodPos < 0 || prodPos >= productList.size()) {
            Toast.makeText(this, "Please select a product", Toast.LENGTH_SHORT).show();
            return;
        }

        ProductOption prod = productList.get(prodPos);
        if (prod.id <= 0) {
            Toast.makeText(this, "The selected store has no products available to transfer", Toast.LENGTH_SHORT).show();
            return;
        }

        String qtyStr = etTransferQuantity.getText() != null ? etTransferQuantity.getText().toString().trim() : "";
        if (TextUtils.isEmpty(qtyStr)) {
            etTransferQuantity.setError("Quantity required");
            return;
        }

        int qty;
        try {
            qty = Integer.parseInt(qtyStr);
        } catch (NumberFormatException e) {
            etTransferQuantity.setError("Invalid number");
            return;
        }

        if (qty <= 0) {
            etTransferQuantity.setError("Quantity must be greater than zero");
            return;
        }

        // Same product picked twice: merge the quantities into one line
        for (RequestItem item : selectedItems) {
            if (item.product.id == prod.id) {
                item.quantity += qty;
                etTransferQuantity.setText("");
                renderSelectedItems();
                Toast.makeText(this, "Updated quantity for " + prod.name, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        selectedItems.add(new RequestItem(prod, qty));
        etTransferQuantity.setText("");
        renderSelectedItems();
    }

    /** Draws the requested item lines with quantity, base price and selling price. */
    private void renderSelectedItems() {
        if (llSelectedItems == null) return;
        llSelectedItems.removeAllViews();

        int totalUnits = 0;
        for (RequestItem item : selectedItems) {
            totalUnits += item.quantity;
        }

        if (tvSelectedItemsLabel != null) {
            tvSelectedItemsLabel.setText("Requested items (" + selectedItems.size() + " line(s) • "
                    + totalUnits + " units)");
        }

        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (10 * density);

        if (selectedItems.isEmpty()) {
            TextView tvHint = new TextView(this);
            tvHint.setText("No items yet — pick a product, enter the quantity, then tap \"Add item to request\".");
            tvHint.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            tvHint.setTextSize(12f);
            tvHint.setPadding(0, 0, 0, pad);
            llSelectedItems.addView(tvHint);
            return;
        }

        for (final RequestItem item : selectedItems) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(pad, pad / 2, pad, pad / 2);
            row.setBackgroundColor(ContextCompat.getColor(this, R.color.card_subtle_bg));

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvName = new TextView(this);
            tvName.setText(item.quantity + " × " + item.product.name);
            tvName.setTextSize(12f);
            tvName.setTypeface(tvName.getTypeface(), Typeface.BOLD);
            tvName.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            col.addView(tvName);

            TextView tvPrice = new TextView(this);
            double lineBase = item.product.basePrice * item.quantity;
            double lineSell = item.product.sellingPrice * item.quantity;
            tvPrice.setText("Base " + peso(item.product.basePrice) + " each (₱" + String.format(Locale.US, "%.2f", lineBase)
                    + ") • Selling " + peso(item.product.sellingPrice) + " each (₱" + String.format(Locale.US, "%.2f", lineSell) + ")");
            tvPrice.setTextSize(11f);
            tvPrice.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            col.addView(tvPrice);

            row.addView(col);

            TextView tvRemove = new TextView(this);
            tvRemove.setText("✕");
            tvRemove.setTextSize(16f);
            tvRemove.setTextColor(ContextCompat.getColor(this, R.color.danger_strong));
            tvRemove.setPadding(pad, 0, 0, 0);
            tvRemove.setOnClickListener(v -> {
                selectedItems.remove(item);
                renderSelectedItems();
            });
            row.addView(tvRemove);

            llSelectedItems.addView(row);
        }
    }

    private void submitNewTransfer() {
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "Add at least one item to the request", Toast.LENGTH_SHORT).show();
            return;
        }

        String notes = etTransferNotes.getText() != null ? etTransferNotes.getText().toString().trim() : "";

        RequestQueue queue = ApiSession.getRequestQueue(this);

        JSONObject payload = new JSONObject();
        try {
            payload.put("action", "create");

            JSONArray itemsArray = new JSONArray();
            for (RequestItem item : selectedItems) {
                JSONObject line = new JSONObject();
                line.put("product_id", item.product.id);
                line.put("quantity", item.quantity);
                itemsArray.put(line);
            }
            payload.put("items", itemsArray);
            payload.put("notes", notes);

            if (requestMode) {
                // Pending-donor request for the staff member's own branch
                payload.put("to_store_id", storeId);
            } else {
                int fromPos = spFromStore.getSelectedItemPosition();
                int toPos = spToStore.getSelectedItemPosition();
                if (fromPos < 0 || toPos < 0) {
                    Toast.makeText(this, "Please select source and destination store", Toast.LENGTH_SHORT).show();
                    return;
                }

                StoreOption fromStore = fromStoreList.get(fromPos);
                StoreOption toStore = toStoreList.get(toPos);

                if (fromStore.id == toStore.id) {
                    Toast.makeText(this, "Source and destination store must be different", Toast.LENGTH_SHORT).show();
                    return;
                }

                for (RequestItem item : selectedItems) {
                    if (item.quantity > item.product.stock) {
                        Toast.makeText(this, "Only " + item.product.stock + " of " + item.product.name
                                + " available in the source branch", Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                payload.put("from_store_id", fromStore.id);
                payload.put("to_store_id", toStore.id);
            }
        } catch (JSONException ignored) {}

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                TRANSFERS_URL + "?action=create",
                payload,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            int sentLines = selectedItems.size();
                            int sentUnits = 0;
                            for (RequestItem item : selectedItems) sentUnits += item.quantity;

                            Toast.makeText(this, response.optString("message",
                                    "Transfer request created (" + sentLines + " item(s), " + sentUnits + " units)."),
                                    Toast.LENGTH_LONG).show();
                            selectedItems.clear();
                            renderSelectedItems();
                            etTransferQuantity.setText("");
                            etTransferNotes.setText("");
                            if (cardNewTransfer != null) {
                                cardNewTransfer.setVisibility(View.GONE);
                                if (btnToggleNewTransfer != null) btnToggleNewTransfer.setText("+ New Transfer");
                            }
                            loadTransferHistory();
                        } else {
                            Toast.makeText(this, response.optString("message", "Transfer failed"), Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(this, "Response parse error", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (ApiSession.isPremiumRequired(error)) {
                        ApiSession.showPremiumUpgradeDialog(
                                this,
                                "Stock Transfers",
                                "Stock Transfers is available with StockEase Premium. Move inventory between branches and maintain a complete transfer history."
                        );
                        return;
                    }
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to create transfer request", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    private void loadTransferHistory() {
        if (tvTransfersStatus != null) {
            tvTransfersStatus.setVisibility(View.VISIBLE);
            tvTransfersStatus.setText("Loading transfer history...");
        }

        RequestQueue queue = ApiSession.getRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                TRANSFERS_URL + "?action=list",
                null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            JSONArray arr = response.getJSONArray("transfers");
                            renderTransferRows(arr);
                        } else {
                            if (tvTransfersStatus != null) {
                                tvTransfersStatus.setVisibility(View.VISIBLE);
                                tvTransfersStatus.setText(response.optString("message", "Unable to load transfers"));
                            }
                        }
                    } catch (JSONException e) {
                        if (tvTransfersStatus != null) {
                            tvTransfersStatus.setVisibility(View.VISIBLE);
                            tvTransfersStatus.setText("Malformed transfer data");
                        }
                    }
                },
                error -> {
                    if (ApiSession.isPremiumRequired(error)) {
                        if (tvTransfersStatus != null) {
                            tvTransfersStatus.setVisibility(View.VISIBLE);
                            tvTransfersStatus.setText("Stock Transfers is a StockEase Premium feature.\nUpgrade your subscription to unlock inter-store inventory movement.");
                        }
                        ApiSession.showPremiumUpgradeDialog(
                                this,
                                "Stock Transfers",
                                "Stock Transfers is available with StockEase Premium. Move inventory between branches and maintain a complete transfer history."
                        );
                        return;
                    }
                    if (ApiSession.handleAuthError(this, error)) return;
                    if (tvTransfersStatus != null) {
                        tvTransfersStatus.setVisibility(View.VISIBLE);
                        tvTransfersStatus.setText("Network error loading transfer history");
                    }
                }
        );

        queue.add(request);
    }

    private void renderTransferRows(JSONArray arr) {
        if (llTransferRows == null) return;
        llTransferRows.removeAllViews();

        if (arr.length() == 0) {
            if (tvTransfersStatus != null) {
                tvTransfersStatus.setVisibility(View.VISIBLE);
                tvTransfersStatus.setText("No stock transfers recorded yet.");
            }
            return;
        }

        if (tvTransfersStatus != null) {
            tvTransfersStatus.setVisibility(View.GONE);
        }

        float density = getResources().getDisplayMetrics().density;
        int padH = (int) (12 * density);
        int padV = (int) (10 * density);

        for (int i = 0; i < arr.length(); i++) {
            JSONObject t = arr.optJSONObject(i);
            if (t == null) continue;

            int transferId = t.optInt("id", 0);
            int fromStoreId = t.optInt("from_store_id", 0);
            int toStoreId = t.optInt("to_store_id", 0);
            String fromName = fromStoreId > 0 ? t.optString("from_store_name", "Store") : "Pending donor";
            String toName = t.optString("to_store_name", "Store");
            int totalQty = t.optInt("total_quantity", 0);
            String reqBy = t.optString("requested_by_name", "Admin");
            String date = t.optString("created_at", "—");
            String status = t.optString("status", "requested");
            boolean canApprove = "super_admin".equals(role) || "manager".equals(role);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(padH, padV, padH, padV);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(ContextCompat.getColor(this, R.color.white));
            row.setClickable(true);
            row.setFocusable(true);
            // Tapping the row opens the item breakdown (quantities + prices)
            row.setOnClickListener(v -> showTransferDetail(transferId));

            // 1. ID (0.6f)
            TextView tvId = new TextView(this);
            tvId.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f));
            tvId.setText("#" + transferId);
            tvId.setTextSize(12f);
            tvId.setTypeface(tvId.getTypeface(), Typeface.BOLD);
            tvId.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            row.addView(tvId);

            // 2. ROUTE (2.0f)
            TextView tvRoute = new TextView(this);
            tvRoute.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.0f));
            tvRoute.setText(fromName + " → " + toName);
            tvRoute.setTextSize(12f);
            tvRoute.setTypeface(tvRoute.getTypeface(), Typeface.BOLD);
            tvRoute.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            tvRoute.setSingleLine(true);
            tvRoute.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(tvRoute);

            // 3. QTY (0.8f, end)
            TextView tvQty = new TextView(this);
            tvQty.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f));
            tvQty.setText(String.valueOf(totalQty));
            tvQty.setTextSize(12f);
            tvQty.setTextColor(ContextCompat.getColor(this, R.color.slate_700));
            tvQty.setGravity(Gravity.END);
            row.addView(tvQty);

            // 4. REQUESTED BY (1.2f)
            TextView tvReq = new TextView(this);
            LinearLayout.LayoutParams lpReq = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f);
            lpReq.leftMargin = (int) (8 * density);
            tvReq.setLayoutParams(lpReq);
            tvReq.setText(reqBy);
            tvReq.setTextSize(12f);
            tvReq.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            tvReq.setSingleLine(true);
            row.addView(tvReq);

            // 5. DATE (1.2f)
            TextView tvDate = new TextView(this);
            tvDate.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
            String shortDate = date.length() >= 10 ? date.substring(0, 10) : date;
            tvDate.setText(shortDate);
            tvDate.setTextSize(11f);
            tvDate.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            row.addView(tvDate);

            // 6. STATUS BADGE (1.0f)
            LinearLayout colStatus = new LinearLayout(this);
            colStatus.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            colStatus.setGravity(Gravity.CENTER);

            TextView tvStatus = new TextView(this);
            tvStatus.setTextSize(10f);
            tvStatus.setTypeface(tvStatus.getTypeface(), Typeface.BOLD);
            tvStatus.setPadding((int) (6 * density), (int) (2 * density), (int) (6 * density), (int) (2 * density));

            if ("completed".equalsIgnoreCase(status)) {
                tvStatus.setText("Received");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.success_strong));
                tvStatus.setBackgroundResource(R.drawable.bg_badge_emerald);
            } else if ("in_transit".equalsIgnoreCase(status)) {
                tvStatus.setText("In Transit");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.primary));
                tvStatus.setBackgroundResource(R.drawable.bg_badge_blue);
            } else if ("cancelled".equalsIgnoreCase(status)) {
                tvStatus.setText("Cancelled");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                tvStatus.setBackgroundResource(R.drawable.bg_badge_gray);
            } else if ("declined".equalsIgnoreCase(status)) {
                tvStatus.setText("Declined");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.danger_strong));
                tvStatus.setBackgroundResource(R.drawable.bg_badge_gray);
            } else {
                tvStatus.setText(fromStoreId > 0 ? "Requested" : "Pending Approval");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.warning));
                tvStatus.setBackgroundResource(R.drawable.bg_badge_gray);
            }
            colStatus.addView(tvStatus);
            row.addView(colStatus);

            // 7. ACTIONS (1.6f)
            LinearLayout colActions = new LinearLayout(this);
            colActions.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f));
            colActions.setGravity(Gravity.CENTER);

            if ("requested".equalsIgnoreCase(status)) {
                if (canApprove) {
                    // Manager / Super Admin: approve (choose donor branch) or decline
                    Button btnApprove = new Button(this);
                    btnApprove.setText("Approve");
                    btnApprove.setTextSize(10f);
                    btnApprove.setTextColor(ContextCompat.getColor(this, R.color.white));
                    btnApprove.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success_strong)));
                    btnApprove.setPadding((int) (6 * density), 0, (int) (6 * density), 0);
                    btnApprove.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (28 * density)));
                    btnApprove.setOnClickListener(v -> showApproveDialog(transferId, toStoreId));
                    colActions.addView(btnApprove);

                    Button btnDecline = new Button(this);
                    btnDecline.setText("Decline");
                    btnDecline.setTextSize(10f);
                    btnDecline.setTextColor(ContextCompat.getColor(this, R.color.danger_strong));
                    btnDecline.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.danger_bg)));
                    btnDecline.setPadding((int) (6 * density), 0, (int) (6 * density), 0);
                    LinearLayout.LayoutParams lpDecline = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (28 * density));
                    lpDecline.leftMargin = (int) (4 * density);
                    btnDecline.setLayoutParams(lpDecline);
                    btnDecline.setOnClickListener(v -> showDeclineDialog(transferId));
                    colActions.addView(btnDecline);
                } else {
                    // Dispatch only applies when a donor branch is already assigned
                    boolean canDispatch = fromStoreId > 0 && (storeId > 0 && storeId == fromStoreId);
                    if (canDispatch) {
                        Button btnDispatch = new Button(this);
                        btnDispatch.setText("Dispatch");
                        btnDispatch.setTextSize(10f);
                        btnDispatch.setTextColor(ContextCompat.getColor(this, R.color.white));
                        btnDispatch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary)));
                        btnDispatch.setPadding((int) (6 * density), 0, (int) (6 * density), 0);
                        btnDispatch.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (28 * density)));
                        btnDispatch.setOnClickListener(v -> performTransferAction("dispatch", transferId));
                        colActions.addView(btnDispatch);
                    }

                    boolean canCancel = "super_admin".equals(role) || (storeId > 0 && storeId == toStoreId);
                    if (canCancel) {
                        Button btnCancel = new Button(this);
                        btnCancel.setText("Cancel");
                        btnCancel.setTextSize(10f);
                        btnCancel.setTextColor(ContextCompat.getColor(this, R.color.danger_strong));
                        btnCancel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.danger_bg)));
                        btnCancel.setPadding((int) (6 * density), 0, (int) (6 * density), 0);
                        LinearLayout.LayoutParams lpCancel = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (28 * density));
                        lpCancel.leftMargin = (int) (4 * density);
                        btnCancel.setLayoutParams(lpCancel);
                        btnCancel.setOnClickListener(v -> performTransferAction("cancel", transferId));
                        colActions.addView(btnCancel);
                    } else {
                        TextView tvAwaiting = new TextView(this);
                        tvAwaiting.setText("Awaiting manager");
                        tvAwaiting.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
                        tvAwaiting.setTextSize(10f);
                        colActions.addView(tvAwaiting);
                    }
                }

            } else if ("in_transit".equalsIgnoreCase(status)) {
                // Show Confirm Receipt Action (Allowed for Super Admin or Destination Store Admin)
                boolean canReceive = "super_admin".equals(role) || (storeId > 0 && storeId == toStoreId);
                if (canReceive) {
                    Button btnReceive = new Button(this);
                    btnReceive.setText("Confirm Receipt");
                    btnReceive.setTextSize(10f);
                    btnReceive.setTextColor(ContextCompat.getColor(this, R.color.white));
                    btnReceive.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.success_strong)));
                    btnReceive.setPadding((int) (6 * density), 0, (int) (6 * density), 0);
                    btnReceive.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (28 * density)));
                    btnReceive.setOnClickListener(v -> performTransferAction("complete", transferId));
                    colActions.addView(btnReceive);
                }

                Button btnCancel = new Button(this);
                btnCancel.setText("Cancel");
                btnCancel.setTextSize(10f);
                btnCancel.setTextColor(ContextCompat.getColor(this, R.color.danger_strong));
                btnCancel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.danger_bg)));
                btnCancel.setPadding((int) (6 * density), 0, (int) (6 * density), 0);
                LinearLayout.LayoutParams lpCancel = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (28 * density));
                lpCancel.leftMargin = (int) (4 * density);
                btnCancel.setLayoutParams(lpCancel);
                btnCancel.setOnClickListener(v -> performTransferAction("cancel", transferId));
                colActions.addView(btnCancel);

            } else {
                TextView tvDone = new TextView(this);
                tvDone.setText("—");
                tvDone.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
                tvDone.setTextSize(12f);
                colActions.addView(tvDone);
            }

            row.addView(colActions);

            llTransferRows.addView(row);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.card_border));
            llTransferRows.addView(divider);
        }
    }

    /** Human readable label for a transfer_events row. */
    private String eventLabel(String event) {
        String e = event == null ? "" : event.toLowerCase(Locale.US);
        switch (e) {
            case "requested": return "Requested";
            case "approved": return "Approved";
            case "dispatched": return "Dispatched";
            case "received": return "Received";
            case "declined": return "Declined";
            case "cancelled": return "Cancelled";
            default: return event;
        }
    }

    /** Human readable label for a transfer status. */
    private String statusLabel(String status) {
        if ("completed".equalsIgnoreCase(status)) return "Received";
        if ("in_transit".equalsIgnoreCase(status)) return "In Transit";
        if ("cancelled".equalsIgnoreCase(status)) return "Cancelled";
        if ("declined".equalsIgnoreCase(status)) return "Declined";
        return "Requested";
    }

    /** Opens the requested item breakdown with base and selling prices. */
    private void showTransferDetail(int transferId) {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                TRANSFERS_URL + "?action=detail&id=" + transferId,
                null,
                response -> {
                    try {
                        if (!response.getBoolean("success")) {
                            Toast.makeText(this, response.optString("message", "Unable to load transfer"),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        JSONObject t = response.optJSONObject("transfer");
                        if (t == null) return;

                        LinearLayout layout = new LinearLayout(this);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setPadding(48, 32, 48, 16);

                        TextView tvHead = new TextView(this);
                        tvHead.setText("Transfer #" + transferId + " • " + statusLabel(t.optString("status", "requested")));
                        tvHead.setTextSize(14f);
                        tvHead.setTypeface(tvHead.getTypeface(), Typeface.BOLD);
                        tvHead.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                        layout.addView(tvHead);

                        TextView tvRoute = new TextView(this);
                        tvRoute.setText("From: " + t.optString("from_store_name", "Pending donor")
                                + "\nTo: " + t.optString("to_store_name", "Store")
                                + "\nRequested by: " + t.optString("requested_by_name", "—"));
                        tvRoute.setTextSize(13f);
                        tvRoute.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                        LinearLayout.LayoutParams lpRoute = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        lpRoute.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
                        tvRoute.setLayoutParams(lpRoute);
                        layout.addView(tvRoute);

                        JSONArray items = t.optJSONArray("items");
                        int lineCount = items != null ? items.length() : 0;
                        int totalUnits = 0;
                        double totalBase = 0.0;
                        double totalSell = 0.0;

                        for (int i = 0; i < lineCount; i++) {
                            JSONObject it = items.optJSONObject(i);
                            if (it == null) continue;

                            int qty = it.optInt("quantity", 0);
                            double base = it.optDouble("base_price", 0.0);
                            double sell = it.optDouble("selling_price", 0.0);
                            totalUnits += qty;
                            totalBase += base * qty;
                            totalSell += sell * qty;

                            TextView tvLine = new TextView(this);
                            tvLine.setText(qty + " × " + it.optString("product_name", "Item")
                                    + "\n     Base " + peso(base) + " each • Selling " + peso(sell) + " each");
                            tvLine.setTextSize(13f);
                            tvLine.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                            LinearLayout.LayoutParams lpLine = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                            lpLine.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
                            tvLine.setLayoutParams(lpLine);
                            layout.addView(tvLine);
                        }

                        TextView tvTotals = new TextView(this);
                        tvTotals.setText("Total: " + lineCount + " item line(s), " + totalUnits + " unit(s)\n"
                                + "Base value: " + peso(totalBase) + " • Selling value: " + peso(totalSell));
                        tvTotals.setTextSize(13f);
                        tvTotals.setTypeface(tvTotals.getTypeface(), Typeface.BOLD);
                        tvTotals.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                        LinearLayout.LayoutParams lpTotals = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        lpTotals.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
                        tvTotals.setLayoutParams(lpTotals);
                        layout.addView(tvTotals);

                        String notes = t.optString("notes", "");
                        if (!TextUtils.isEmpty(notes)) {
                            TextView tvNotes = new TextView(this);
                            tvNotes.setText("Notes: " + notes);
                            tvNotes.setTextSize(12f);
                            tvNotes.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                            LinearLayout.LayoutParams lpNotes = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                            lpNotes.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
                            tvNotes.setLayoutParams(lpNotes);
                            layout.addView(tvNotes);
                        }

                        // Status timeline: every status change (requested, approved,
                        // dispatched, received, declined, cancelled) logged by the API
                        JSONArray events = t.optJSONArray("events");
                        if (events != null && events.length() > 0) {
                            float d = getResources().getDisplayMetrics().density;

                            TextView tvTimelineHead = new TextView(this);
                            tvTimelineHead.setText("Status timeline");
                            tvTimelineHead.setTextSize(13f);
                            tvTimelineHead.setTypeface(tvTimelineHead.getTypeface(), Typeface.BOLD);
                            tvTimelineHead.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                            LinearLayout.LayoutParams lpHead = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                            lpHead.topMargin = (int) (16 * d);
                            tvTimelineHead.setLayoutParams(lpHead);
                            layout.addView(tvTimelineHead);

                            for (int i = 0; i < events.length(); i++) {
                                JSONObject ev = events.optJSONObject(i);
                                if (ev == null) continue;

                                String when = ev.optString("created_at", "");
                                if (when.length() >= 16) {
                                    // "2026-10-17 18:43:49" -> "Oct 17, 18:43"
                                    when = when.substring(5, 10).replace('-', '/') + ", " + when.substring(11, 16);
                                }

                                TextView tvEvent = new TextView(this);
                                tvEvent.setText("● " + eventLabel(ev.optString("event", ""))
                                        + "  •  " + when
                                        + "\n    " + ev.optString("actor_name", "System")
                                        + " — " + ev.optString("note", ""));
                                tvEvent.setTextSize(12f);
                                tvEvent.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                                LinearLayout.LayoutParams lpEv = new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                                lpEv.topMargin = (int) (10 * d);
                                tvEvent.setLayoutParams(lpEv);
                                layout.addView(tvEvent);
                            }
                        }

                        new AlertDialog.Builder(this)
                                .setTitle("Transfer details")
                                .setView(layout)
                                .setPositiveButton("Close", null)
                                .show();
                    } catch (JSONException e) {
                        Toast.makeText(this, "Failed to load transfer details", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (ApiSession.isPremiumRequired(error)) return;
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to load transfer details", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    /**
     * Manager approval: the manager chooses which branch will transfer stock
     * into the requesting branch, then the transfer is dispatched.
     */
    private void showApproveDialog(int transferId, int requestingStoreId) {
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest storesRequest = new JsonObjectRequest(
                Request.Method.GET,
                TRANSFERS_URL + "?action=donor_options&id=" + transferId,
                null,
                response -> {
                    try {
                        if (!response.getBoolean("success")) return;
                        JSONArray arr = response.optJSONArray("donors");
                        String destinationName = toStoreNameFor(requestingStoreId);
                        JSONObject destination = response.optJSONObject("destination_store");
                        if (destination != null && !destination.optString("store_name").isEmpty()) {
                            destinationName = destination.optString("store_name");
                        }

                        final List<StoreOption> donorOptions = new ArrayList<>();
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject s = arr.optJSONObject(i);
                                if (s == null) continue;
                                int sid = s.optInt("id", 0);
                                if (sid <= 0 || sid == requestingStoreId) continue;
                                donorOptions.add(new StoreOption(sid, s.optString("store_name", "Branch #" + sid)));
                            }
                        }

                        if (donorOptions.isEmpty()) {
                            Toast.makeText(this, "No other branches available as donor", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_approve_transfer, null);
                        Spinner spDonorStore = dialogView.findViewById(R.id.spDonorStore);
                        TextView tvApproveInfo = dialogView.findViewById(R.id.tvApproveInfo);
                        spDonorStore.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, donorOptions));
                        if (tvApproveInfo != null) {
                            tvApproveInfo.setText("Transfer #" + transferId + " → destination: " + destinationName
                                    + "\nChoose the branch that will send the stock:");
                        }

                        new AlertDialog.Builder(this)
                                .setView(dialogView)
                                .setPositiveButton("Approve & Dispatch", (dialog, which) -> {
                                    int pos = spDonorStore.getSelectedItemPosition();
                                    if (pos >= 0 && pos < donorOptions.size()) {
                                        executeManagerApprove(transferId, donorOptions.get(pos).id);
                                    }
                                })
                                .setNegativeButton("Back", null)
                                .show();
                    } catch (JSONException e) {
                        Toast.makeText(this, "Failed to load branches", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to load branches", Toast.LENGTH_SHORT).show();
                }
        );
        queue.add(storesRequest);
    }

    private String toStoreNameFor(int storeIdValue) {
        for (StoreOption opt : toStoreList) {
            if (opt.id == storeIdValue) return opt.name;
        }
        for (StoreOption opt : fromStoreList) {
            if (opt.id == storeIdValue) return opt.name;
        }
        return "Branch #" + storeIdValue;
    }

    private void executeManagerApprove(int transferId, int donorStoreId) {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JSONObject payload = new JSONObject();
        try {
            payload.put("action", "manager_approve");
            payload.put("transfer_id", transferId);
            payload.put("from_store_id", donorStoreId);
        } catch (JSONException ignored) {}

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                TRANSFERS_URL + "?action=manager_approve",
                payload,
                response -> {
                    try {
                        Toast.makeText(this, response.optString("message", "Transfer approved"), Toast.LENGTH_SHORT).show();
                        if (response.optBoolean("success", false)) {
                            loadTransferHistory();
                        }
                    } catch (Exception ignored) {}
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to approve transfer", Toast.LENGTH_SHORT).show();
                }
        );
        queue.add(request);
    }

    /** Manager decline: optionally records a reason for rejecting the request. */
    private void showDeclineDialog(int transferId) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_decline_transfer, null);
        EditText etDeclineReason = dialogView.findViewById(R.id.etDeclineReason);

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Decline Request", (dialog, which) -> {
                    String reason = etDeclineReason.getText() != null ? etDeclineReason.getText().toString().trim() : "";
                    executeManagerDecline(transferId, reason);
                })
                .setNegativeButton("Back", null)
                .show();
    }

    private void executeManagerDecline(int transferId, String reason) {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JSONObject payload = new JSONObject();
        try {
            payload.put("action", "manager_decline");
            payload.put("transfer_id", transferId);
            payload.put("reason", reason);
        } catch (JSONException ignored) {}

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                TRANSFERS_URL + "?action=manager_decline",
                payload,
                response -> {
                    try {
                        Toast.makeText(this, response.optString("message", "Transfer declined"), Toast.LENGTH_SHORT).show();
                        if (response.optBoolean("success", false)) {
                            loadTransferHistory();
                        }
                    } catch (Exception ignored) {}
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to decline transfer", Toast.LENGTH_SHORT).show();
                }
        );
        queue.add(request);
    }

    private void performTransferAction(String action, int transferId) {
        String confirmMsg = "dispatch".equals(action)
                ? "Dispatch this transfer into transit? Source inventory will be deducted."
                : ("complete".equals(action)
                ? "Confirm receipt of items? Destination inventory will be replenished."
                : "Cancel this transfer? Deducted inventory will be restored to source.");

        new AlertDialog.Builder(this)
                .setTitle("Confirm Action")
                .setMessage(confirmMsg)
                .setPositiveButton("Confirm", (dialog, which) -> executeTransferApi(action, transferId))
                .setNegativeButton("Back", null)
                .show();
    }

    private void executeTransferApi(String action, int transferId) {
        RequestQueue queue = ApiSession.getRequestQueue(this);

        JSONObject payload = new JSONObject();
        try {
            payload.put("action", action);
            payload.put("transfer_id", transferId);
        } catch (JSONException ignored) {}

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                TRANSFERS_URL + "?action=" + action,
                payload,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            Toast.makeText(this, response.optString("message", "Success"), Toast.LENGTH_SHORT).show();
                            loadTransferHistory();
                        } else {
                            Toast.makeText(this, response.optString("message", "Action failed"), Toast.LENGTH_LONG).show();
                        }
                    } catch (JSONException e) {
                        Toast.makeText(this, "Response parse error", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (ApiSession.isPremiumRequired(error)) {
                        ApiSession.showPremiumUpgradeDialog(
                                this,
                                "Stock Transfers",
                                "Stock Transfers is available with StockEase Premium. Move inventory between branches and maintain a complete transfer history."
                        );
                        return;
                    }
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to update transfer status", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ScreenTransitions.open(StockTransfersActivity.this, intent);
        ScreenTransitions.finish(StockTransfersActivity.this);
    }
}
