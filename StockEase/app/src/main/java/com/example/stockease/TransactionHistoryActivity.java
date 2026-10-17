package com.example.stockease;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TransactionHistoryActivity extends AppCompatActivity {

    private RecyclerView rvTransactions;
    private TextView tvTotalRevenue;
    private EditText etSearchTransactions;
    private Spinner spinnerFilterStatus;
    private Button btnExportCsv, btnFilterAll, btnFilterToday, btnFilterMonth, btnFilterCustom;

    private TransactionAdapter adapter;
    private View emptyTransactions;
    private View errorTransactions;
    private View loadingTransactions;
    private List<Transaction> allTransactions = new ArrayList<>();
    private List<Transaction> displayedTransactions = new ArrayList<>();
    private int storeId = -1;

    private String startDateFilter = null;
    private String endDateFilter = null;
    private String currentSearchQuery = "";
    private String currentStatusFilter = "All Statuses";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_history);
        SidebarNav.attach(this, "transactions");

        storeId = getIntent().getIntExtra("store_id", -1);
        if (storeId <= 0) {
            returnToLogin("Missing store identity. Please sign in again.");
            return;
        }

        rvTransactions = findViewById(R.id.rvTransactions);
        loadingTransactions = findViewById(R.id.loadingTransactions);
        emptyTransactions = findViewById(R.id.emptyTransactions);
        errorTransactions = findViewById(R.id.errorTransactions);
        StateViews.bindError(errorTransactions, null, null, v -> loadTransactions());
        StateViews.bindEmpty(emptyTransactions,
                getString(R.string.empty_transactions_title),
                getString(R.string.empty_transactions_msg), null, null);
        tvTotalRevenue = findViewById(R.id.tvTotalRevenue);
        etSearchTransactions = findViewById(R.id.etSearchTransactions);
        spinnerFilterStatus = findViewById(R.id.spinnerFilterStatus);
        btnExportCsv = findViewById(R.id.btnExportCsv);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterToday = findViewById(R.id.btnFilterToday);
        btnFilterMonth = findViewById(R.id.btnFilterMonth);
        btnFilterCustom = findViewById(R.id.btnFilterCustom);

        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter(this, displayedTransactions, this::showVoidConfirmationDialog);
        rvTransactions.setAdapter(adapter);

        setupSearchAndStatusFilter();

        btnExportCsv.setOnClickListener(v -> exportToCSV());

        // Quick Filter Click Listeners
        btnFilterAll.setOnClickListener(v -> {
            startDateFilter = null;
            endDateFilter = null;
            highlightActiveFilter(btnFilterAll);
            loadTransactions();
        });

        btnFilterToday.setOnClickListener(v -> {
            filterToday();
            highlightActiveFilter(btnFilterToday);
        });

        btnFilterMonth.setOnClickListener(v -> {
            filterThisMonth();
            highlightActiveFilter(btnFilterMonth);
        });

        btnFilterCustom.setOnClickListener(v -> showCustomDateRangePicker());

        loadTransactions();

        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));
    }

    private void setupSearchAndStatusFilter() {
        if (etSearchTransactions != null) {
            etSearchTransactions.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentSearchQuery = s.toString().trim().toLowerCase(Locale.getDefault());
                    applyFilters();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (spinnerFilterStatus != null) {
            String[] statusOptions = new String[]{"All Statuses", "Completed", "Voided"};
            ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, statusOptions);
            statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerFilterStatus.setAdapter(statusAdapter);

            spinnerFilterStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    currentStatusFilter = statusOptions[position];
                    applyFilters();
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private void applyFilters() {
        displayedTransactions.clear();
        double filteredRevenue = 0.0;

        for (Transaction tx : allTransactions) {
            boolean matchesSearch = currentSearchQuery.isEmpty()
                    || String.valueOf(tx.getId()).contains(currentSearchQuery)
                    || tx.getUserName().toLowerCase(Locale.getDefault()).contains(currentSearchQuery)
                    || tx.getCashierUsername().toLowerCase(Locale.getDefault()).contains(currentSearchQuery)
                    || (tx.getItemsSummary() != null && tx.getItemsSummary().toLowerCase(Locale.getDefault()).contains(currentSearchQuery));

            boolean matchesStatus = "All Statuses".equalsIgnoreCase(currentStatusFilter)
                    || ("Completed".equalsIgnoreCase(currentStatusFilter) && !tx.isVoided())
                    || ("Voided".equalsIgnoreCase(currentStatusFilter) && tx.isVoided());

            if (matchesSearch && matchesStatus) {
                displayedTransactions.add(tx);
                if (!tx.isVoided()) {
                    filteredRevenue += tx.getTotalAmount();
                }
            }
        }

        if (tvTotalRevenue != null) {
            tvTotalRevenue.setText(String.format("₱%.2f", filteredRevenue));
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateEmptyState();
    }

    /**
     * Two empty states: no transactions recorded yet, versus a search or status
     * filter that matches nothing. The second offers a way out.
     */
    private void updateEmptyState() {
        boolean nothingAtAll = allTransactions == null || allTransactions.isEmpty();
        boolean nothingShown = displayedTransactions == null || displayedTransactions.isEmpty();
        if (emptyTransactions == null) return;

        if (nothingShown) {
            if (nothingAtAll) {
                StateViews.bindEmpty(emptyTransactions,
                        getString(R.string.empty_transactions_title),
                        getString(R.string.empty_transactions_msg), null, null);
            } else {
                StateViews.bindEmpty(emptyTransactions,
                        getString(R.string.empty_products_no_match_title),
                        getString(R.string.empty_tx_no_match_msg),
                        getString(R.string.state_action_clear),
                        v -> clearFilters());
            }
        }
        StateViews.setEmptyVisible(emptyTransactions, nothingShown);
        if (rvTransactions != null) {
            rvTransactions.setVisibility(nothingShown ? View.INVISIBLE : View.VISIBLE);
        }
    }

    /** Drops the active search and status filter so the full history returns. */
    private void clearFilters() {
        currentSearchQuery = "";
        currentStatusFilter = "All Statuses";
        if (etSearchTransactions != null) {
            etSearchTransactions.setText("");
        }
        if (spinnerFilterStatus != null) {
            spinnerFilterStatus.setSelection(0);
        }
        applyFilters();
    }

    private void highlightActiveFilter(Button activeBtn) {
        btnFilterAll.setBackgroundColor(ContextCompat.getColor(this, R.color.slate_600));
        btnFilterToday.setBackgroundColor(ContextCompat.getColor(this, R.color.slate_600));
        btnFilterMonth.setBackgroundColor(ContextCompat.getColor(this, R.color.slate_600));
        btnFilterCustom.setBackgroundColor(ContextCompat.getColor(this, R.color.slate_600));

        activeBtn.setBackgroundColor(ContextCompat.getColor(this, R.color.text_primary));
    }

    private void filterToday() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = sdf.format(Calendar.getInstance().getTime());
        startDateFilter = today;
        endDateFilter = today;
        loadTransactions();
    }

    private void filterThisMonth() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        cal.set(Calendar.DAY_OF_MONTH, 1);
        startDateFilter = sdf.format(cal.getTime());

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        endDateFilter = sdf.format(cal.getTime());

        loadTransactions();
    }

    private void showCustomDateRangePicker() {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog startDialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String start = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth);

            DatePickerDialog endDialog = new DatePickerDialog(this, (view1, yearEnd, monthEnd, dayOfMonthEnd) -> {
                String end = String.format(Locale.getDefault(), "%04d-%02d-%02d", yearEnd, monthEnd + 1, dayOfMonthEnd);
                startDateFilter = start;
                endDateFilter = end;
                highlightActiveFilter(btnFilterCustom);
                loadTransactions();
            }, year, month, dayOfMonth);

            endDialog.setTitle("Select End Date");
            endDialog.show();

        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));

        startDialog.setTitle("Select Start Date");
        startDialog.show();
    }

    private void loadTransactions() {
        String url = ApiConfig.getUrl("get_transactions.php?store_id=" + storeId);
        if (startDateFilter != null && endDateFilter != null) {
            url += "&start_date=" + startDateFilter + "&end_date=" + endDateFilter;
        }

        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET, url, null,
                response -> {
                    StateViews.hideLoading(loadingTransactions);
                    StateViews.setErrorVisible(errorTransactions, false);
                    allTransactions.clear();
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            double amount = obj.getDouble("total_amount");
                            String status = obj.optString("status", "completed");

                            Transaction tx = new Transaction(
                                    obj.optInt("id", obj.optInt("transaction_id", 0)),
                                    amount,
                                    obj.optString("payment_type", "Cash"),
                                    obj.optString("transaction_date", obj.optString("created_at", "N/A")),
                                    obj.optString("user_name", "Unknown Staff"),
                                    obj.optString("cashier_username", ""),
                                    obj.optString("cashier_role", "staff"),
                                    obj.optString("items_summary", "No item details"),
                                    status
                            );
                            allTransactions.add(tx);
                        }
                        applyFilters();
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Data parsing error", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    StateViews.hideLoading(loadingTransactions);
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to load transaction history", Toast.LENGTH_SHORT).show();
                    showTransactionsError();
                }
        );

        queue.add(request);
    }

    private void showVoidConfirmationDialog(Transaction transaction) {
        if (transaction.isVoided()) {
            Toast.makeText(this, "Transaction is already voided", Toast.LENGTH_SHORT).show();
            return;
        }

        final EditText inputReason = new EditText(this);
        inputReason.setHint("Reason for voiding (e.g. customer changed mind, wrong punch)");
        inputReason.setPadding(40, 20, 40, 20);

        new AlertDialog.Builder(this)
                .setTitle("Void Transaction #" + transaction.getId() + "?")
                .setMessage("Transaction punched by: " + transaction.getDutyShiftInfo() +
                        "\n\nVoiding will immediately restore all items to stock and reverse sales totals.")
                .setView(inputReason)
                .setPositiveButton("Confirm Void", (dialog, which) -> {
                    String reason = inputReason.getText().toString().trim();
                    if (reason.isEmpty()) {
                        reason = "Customer changed mind / Cashier void";
                    }
                    voidTransactionApi(transaction.getId(), reason);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void voidTransactionApi(int transactionId, String reason) {
        String url = ApiConfig.getUrl("void_transaction.php");
        JSONObject payload = new JSONObject();
        try {
            payload.put("transaction_id", transactionId);
            payload.put("reason", reason);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, url, payload,
                response -> {
                    boolean success = response.optBoolean("success", false);
                    String message = response.optString("message", "Transaction voided");
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    if (success) {
                        loadTransactions(); // Reload and re-filter
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    String errorMsg = "Failed to void transaction";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String resp = new String(error.networkResponse.data);
                            JSONObject errObj = new JSONObject(resp);
                            errorMsg = errObj.optString("message", errorMsg);
                        } catch (Exception ignored) {}
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
                }
        );

        ApiSession.getRequestQueue(this).add(request);
    }

    private void exportToCSV() {
        if (displayedTransactions.isEmpty()) {
            Toast.makeText(this, "No transactions available to export", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csvData = new StringBuilder();
        csvData.append("Transaction ID,Status,On-Duty Cashier,Cashier Username,Payment Type,Total Amount,Date,Items\n");

        for (Transaction tx : displayedTransactions) {
            String sanitizedSummary = tx.getItemsSummary() != null ? tx.getItemsSummary().replace("\n", "; ") : "";
            csvData.append(tx.getId()).append(",")
                    .append(tx.getStatus()).append(",")
                    .append("\"").append(tx.getUserName()).append("\",")
                    .append("\"").append(tx.getCashierUsername()).append("\",")
                    .append(tx.getPaymentType()).append(",")
                    .append(tx.getTotalAmount()).append(",")
                    .append("\"").append(tx.getTransactionDate()).append("\",")
                    .append("\"").append(sanitizedSummary).append("\"\n");
        }

        try {
            File exportDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (exportDir != null && !exportDir.exists()) {
                exportDir.mkdirs();
            }

            File file = new File(exportDir, "StockEase_Transactions_Store_" + storeId + ".csv");
            FileWriter writer = new FileWriter(file);
            writer.write(csvData.toString());
            writer.flush();
            writer.close();

            Toast.makeText(this, "CSV exported successfully to Documents!", Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to export CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ScreenTransitions.open(TransactionHistoryActivity.this, intent);
        ScreenTransitions.finish(TransactionHistoryActivity.this);
    }

    /**
     * A load failed. Shows the error state with a working retry instead of
     * leaving a blank screen, which a user cannot tell apart from an empty
     * list or a broken app.
     */
    private void showTransactionsError() {
        if (emptyTransactions != null) {
            StateViews.setEmptyVisible(emptyTransactions, false);
        }
        StateViews.setErrorVisible(errorTransactions, true);
    }

}
