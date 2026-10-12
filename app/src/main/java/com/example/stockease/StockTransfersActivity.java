package com.example.stockease;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
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
    private Button btnToggleNewTransfer;
    private Spinner spFromStore, spToStore, spTransferProduct;
    private TextView tvAvailableStock, tvTransfersStatus;
    private EditText etTransferQuantity, etTransferNotes;
    private Button btnSubmitTransfer, btnRefreshTransfers;
    private LinearLayout llTransferRows;

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

        ProductOption(int id, String name, int stock) {
            this.id = id;
            this.name = name;
            this.stock = stock;
        }

        @Override
        public String toString() {
            return name + " (" + stock + " in stock)";
        }
    }

    private final List<StoreOption> fromStoreList = new ArrayList<>();
    private final List<StoreOption> toStoreList = new ArrayList<>();
    private final List<ProductOption> productList = new ArrayList<>();

    private int prefillSourceStoreId = -1;
    private int prefillDestStoreId = -1;
    private int prefillProductId = -1;
    private int prefillQty = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_transfers);

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

        if (userId <= 0 || (!"super_admin".equals(role) && !"store_admin".equals(role) && !"manager".equals(role))) {
            returnToLogin("Stock Transfers are restricted to Manager and Store Admin accounts.");
            return;
        }

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

        if (btnToggleNewTransfer != null) {
            btnToggleNewTransfer.setOnClickListener(v -> {
                if (cardNewTransfer.getVisibility() == View.VISIBLE) {
                    cardNewTransfer.setVisibility(View.GONE);
                    btnToggleNewTransfer.setText("+ New Transfer");
                } else {
                    cardNewTransfer.setVisibility(View.VISIBLE);
                    btnToggleNewTransfer.setText("− Hide Form");
                }
            });
        }

        if (btnRefreshTransfers != null) {
            btnRefreshTransfers.setOnClickListener(v -> loadTransferHistory());
        }

        if (btnSubmitTransfer != null) {
            btnSubmitTransfer.setOnClickListener(v -> submitNewTransfer());
        }

        setupStoreSpinners();
        loadTransferHistory();
        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));
    }

    private void setupStoreSpinners() {
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
                                        p.optInt("stock", 0)
                                ));
                            }
                        }
                    }

                    if (productList.isEmpty()) {
                        productList.add(new ProductOption(-1, "No products available in this store", 0));
                    }

                    ArrayAdapter<ProductOption> prodAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, productList);
                    spTransferProduct.setAdapter(prodAdapter);

                    if (prefillProductId > 0) {
                        for (int i = 0; i < productList.size(); i++) {
                            if (productList.get(i).id == prefillProductId) {
                                spTransferProduct.setSelection(i);
                                break;
                            }
                        }
                    }

                    spTransferProduct.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (position >= 0 && position < productList.size()) {
                                ProductOption opt = productList.get(position);
                                if (tvAvailableStock != null) {
                                    tvAvailableStock.setText("Stock: " + opt.stock + " units");
                                }
                            }
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });

                    if (!productList.isEmpty() && tvAvailableStock != null) {
                        tvAvailableStock.setText("Stock: " + productList.get(0).stock + " units");
                    }
                },
                error -> {
                    if (tvAvailableStock != null) tvAvailableStock.setText("Stock: —");
                }
        );

        queue.add(request);
    }

    private void submitNewTransfer() {
        int fromPos = spFromStore.getSelectedItemPosition();
        int toPos = spToStore.getSelectedItemPosition();
        int prodPos = spTransferProduct.getSelectedItemPosition();

        if (fromPos < 0 || toPos < 0 || prodPos < 0 || productList.isEmpty()) {
            Toast.makeText(this, "Please select source, destination, and product", Toast.LENGTH_SHORT).show();
            return;
        }

        StoreOption fromStore = fromStoreList.get(fromPos);
        StoreOption toStore = toStoreList.get(toPos);
        ProductOption prod = productList.get(prodPos);
        if (prod.id <= 0) {
            Toast.makeText(this, "The selected store has no products available to transfer", Toast.LENGTH_SHORT).show();
            return;
        }

        if (fromStore.id == toStore.id) {
            Toast.makeText(this, "Source and destination store must be different", Toast.LENGTH_SHORT).show();
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

        if (qty > prod.stock) {
            etTransferQuantity.setError("Cannot transfer more than available stock (" + prod.stock + ")");
            return;
        }

        String notes = etTransferNotes.getText() != null ? etTransferNotes.getText().toString().trim() : "";

        RequestQueue queue = ApiSession.getRequestQueue(this);

        JSONObject payload = new JSONObject();
        try {
            payload.put("action", "create");
            payload.put("from_store_id", fromStore.id);
            payload.put("to_store_id", toStore.id);
            payload.put("product_id", prod.id);
            payload.put("quantity", qty);
            payload.put("notes", notes);
        } catch (JSONException ignored) {}

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                TRANSFERS_URL + "?action=create",
                payload,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            Toast.makeText(this, "Transfer created successfully!", Toast.LENGTH_SHORT).show();
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
            String fromName = t.optString("from_store_name", "Store");
            String toName = t.optString("to_store_name", "Store");
            int totalQty = t.optInt("total_quantity", 0);
            String reqBy = t.optString("requested_by_name", "Admin");
            String date = t.optString("created_at", "—");
            String status = t.optString("status", "requested");
            int fromStoreId = t.optInt("from_store_id", 0);
            int toStoreId = t.optInt("to_store_id", 0);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(padH, padV, padH, padV);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(0xFFFFFFFF);

            // 1. ID (0.6f)
            TextView tvId = new TextView(this);
            tvId.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f));
            tvId.setText("#" + transferId);
            tvId.setTextSize(12f);
            tvId.setTypeface(null, Typeface.BOLD);
            tvId.setTextColor(0xFF0F172A);
            row.addView(tvId);

            // 2. ROUTE (2.0f)
            TextView tvRoute = new TextView(this);
            tvRoute.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.0f));
            tvRoute.setText(fromName + " → " + toName);
            tvRoute.setTextSize(12f);
            tvRoute.setTypeface(null, Typeface.BOLD);
            tvRoute.setTextColor(0xFF0F172A);
            tvRoute.setSingleLine(true);
            tvRoute.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(tvRoute);

            // 3. QTY (0.8f, end)
            TextView tvQty = new TextView(this);
            tvQty.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f));
            tvQty.setText(String.valueOf(totalQty));
            tvQty.setTextSize(12f);
            tvQty.setTextColor(0xFF334155);
            tvQty.setGravity(Gravity.END);
            row.addView(tvQty);

            // 4. REQUESTED BY (1.2f)
            TextView tvReq = new TextView(this);
            LinearLayout.LayoutParams lpReq = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f);
            lpReq.leftMargin = (int) (8 * density);
            tvReq.setLayoutParams(lpReq);
            tvReq.setText(reqBy);
            tvReq.setTextSize(12f);
            tvReq.setTextColor(0xFF64748B);
            tvReq.setSingleLine(true);
            row.addView(tvReq);

            // 5. DATE (1.2f)
            TextView tvDate = new TextView(this);
            tvDate.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
            String shortDate = date.length() >= 10 ? date.substring(0, 10) : date;
            tvDate.setText(shortDate);
            tvDate.setTextSize(11f);
            tvDate.setTextColor(0xFF64748B);
            row.addView(tvDate);

            // 6. STATUS BADGE (1.0f)
            LinearLayout colStatus = new LinearLayout(this);
            colStatus.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            colStatus.setGravity(Gravity.CENTER);

            TextView tvStatus = new TextView(this);
            tvStatus.setTextSize(10f);
            tvStatus.setTypeface(null, Typeface.BOLD);
            tvStatus.setPadding((int) (6 * density), (int) (2 * density), (int) (6 * density), (int) (2 * density));

            if ("completed".equalsIgnoreCase(status)) {
                tvStatus.setText("Received");
                tvStatus.setTextColor(0xFF059669);
                tvStatus.setBackgroundResource(R.drawable.bg_badge_emerald);
            } else if ("in_transit".equalsIgnoreCase(status)) {
                tvStatus.setText("In Transit");
                tvStatus.setTextColor(0xFF2563EB);
                tvStatus.setBackgroundResource(R.drawable.bg_badge_blue);
            } else if ("cancelled".equalsIgnoreCase(status)) {
                tvStatus.setText("Cancelled");
                tvStatus.setTextColor(0xFF64748B);
                tvStatus.setBackgroundResource(R.drawable.bg_badge_gray);
            } else {
                tvStatus.setText("Requested");
                tvStatus.setTextColor(0xFFD97706);
                tvStatus.setBackgroundResource(R.drawable.bg_badge_gray);
            }
            colStatus.addView(tvStatus);
            row.addView(colStatus);

            // 7. ACTIONS (1.6f)
            LinearLayout colActions = new LinearLayout(this);
            colActions.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f));
            colActions.setGravity(Gravity.CENTER);

            if ("requested".equalsIgnoreCase(status)) {
                // Show Dispatch Action (Allowed for Super Admin or Source Store Admin)
                boolean canDispatch = "super_admin".equals(role) || (storeId > 0 && storeId == fromStoreId);
                if (canDispatch) {
                    Button btnDispatch = new Button(this);
                    btnDispatch.setText("Dispatch");
                    btnDispatch.setTextSize(10f);
                    btnDispatch.setTextColor(0xFFFFFFFF);
                    btnDispatch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2563EB));
                    btnDispatch.setPadding((int) (6 * density), 0, (int) (6 * density), 0);
                    btnDispatch.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (28 * density)));
                    btnDispatch.setOnClickListener(v -> performTransferAction("dispatch", transferId));
                    colActions.addView(btnDispatch);
                }

                Button btnCancel = new Button(this);
                btnCancel.setText("Cancel");
                btnCancel.setTextSize(10f);
                btnCancel.setTextColor(0xFFDC2626);
                btnCancel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFEE2E2));
                btnCancel.setPadding((int) (6 * density), 0, (int) (6 * density), 0);
                LinearLayout.LayoutParams lpCancel = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (28 * density));
                lpCancel.leftMargin = (int) (4 * density);
                btnCancel.setLayoutParams(lpCancel);
                btnCancel.setOnClickListener(v -> performTransferAction("cancel", transferId));
                colActions.addView(btnCancel);

            } else if ("in_transit".equalsIgnoreCase(status)) {
                // Show Confirm Receipt Action (Allowed for Super Admin or Destination Store Admin)
                boolean canReceive = "super_admin".equals(role) || (storeId > 0 && storeId == toStoreId);
                if (canReceive) {
                    Button btnReceive = new Button(this);
                    btnReceive.setText("Confirm Receipt");
                    btnReceive.setTextSize(10f);
                    btnReceive.setTextColor(0xFFFFFFFF);
                    btnReceive.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF059669));
                    btnReceive.setPadding((int) (6 * density), 0, (int) (6 * density), 0);
                    btnReceive.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (28 * density)));
                    btnReceive.setOnClickListener(v -> performTransferAction("complete", transferId));
                    colActions.addView(btnReceive);
                }

                Button btnCancel = new Button(this);
                btnCancel.setText("Cancel");
                btnCancel.setTextSize(10f);
                btnCancel.setTextColor(0xFFDC2626);
                btnCancel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFEE2E2));
                btnCancel.setPadding((int) (6 * density), 0, (int) (6 * density), 0);
                LinearLayout.LayoutParams lpCancel = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (int) (28 * density));
                lpCancel.leftMargin = (int) (4 * density);
                btnCancel.setLayoutParams(lpCancel);
                btnCancel.setOnClickListener(v -> performTransferAction("cancel", transferId));
                colActions.addView(btnCancel);

            } else {
                TextView tvDone = new TextView(this);
                tvDone.setText("—");
                tvDone.setTextColor(0xFF94A3B8);
                tvDone.setTextSize(12f);
                colActions.addView(tvDone);
            }

            row.addView(colActions);

            llTransferRows.addView(row);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFE2E8F0);
            llTransferRows.addView(divider);
        }
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
        startActivity(intent);
        finish();
    }
}
