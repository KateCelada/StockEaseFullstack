package com.example.stockease;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

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
    private Button btnExportCsv, btnFilterAll, btnFilterToday, btnFilterMonth, btnFilterCustom;
    private TransactionAdapter adapter;
    private List<Transaction> transactionList = new ArrayList<>();
    private int storeId = -1;

    private String startDateFilter = null;
    private String endDateFilter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_history);

        storeId = getIntent().getIntExtra("store_id", -1);
        if (storeId <= 0) {
            returnToLogin("Missing store identity. Please sign in again.");
            return;
        }

        rvTransactions = findViewById(R.id.rvTransactions);
        tvTotalRevenue = findViewById(R.id.tvTotalRevenue);
        btnExportCsv = findViewById(R.id.btnExportCsv);
        btnFilterAll = findViewById(R.id.btnFilterAll);
        btnFilterToday = findViewById(R.id.btnFilterToday);
        btnFilterMonth = findViewById(R.id.btnFilterMonth);
        btnFilterCustom = findViewById(R.id.btnFilterCustom);

        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter(this, transactionList);
        rvTransactions.setAdapter(adapter);

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
    }

    private void highlightActiveFilter(Button activeBtn) {
        btnFilterAll.setBackgroundColor(0xFF555555);
        btnFilterToday.setBackgroundColor(0xFF555555);
        btnFilterMonth.setBackgroundColor(0xFF555555);
        btnFilterCustom.setBackgroundColor(0xFF555555);

        activeBtn.setBackgroundColor(0xFF000000);
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
                    transactionList.clear();
                    double totalRevenue = 0.0;
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject obj = response.getJSONObject(i);
                            double amount = obj.getDouble("total_amount");
                            totalRevenue += amount;

                            Transaction tx = new Transaction(
                                    obj.optInt("id", obj.optInt("transaction_id", 0)),
                                    amount,
                                    obj.optString("payment_type", "Cash"),
                                    obj.optString("transaction_date", obj.optString("created_at", "N/A")),
                                    obj.optString("user_name", "N/A"),
                                    obj.optString("items_summary", "No item details")
                            );
                            transactionList.add(tx);
                        }
                        tvTotalRevenue.setText(String.format("₱%.2f", totalRevenue));
                        adapter.notifyDataSetChanged();
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "Data parsing error", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to load transaction history", Toast.LENGTH_SHORT).show();
                }
        );

        queue.add(request);
    }

    private void exportToCSV() {
        if (transactionList.isEmpty()) {
            Toast.makeText(this, "No transactions available to export", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csvData = new StringBuilder();
        csvData.append("Transaction ID,User,Payment Type,Total Amount,Date,Items\n");

        for (Transaction tx : transactionList) {
            String sanitizedSummary = tx.getItemsSummary() != null ? tx.getItemsSummary().replace("\n", "; ") : "";
            csvData.append(tx.getId()).append(",")
                    .append("\"").append(tx.getUserName()).append("\",")
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
        startActivity(intent);
        finish();
    }
}
