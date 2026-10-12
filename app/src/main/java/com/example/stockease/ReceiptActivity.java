package com.example.stockease;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReceiptActivity extends AppCompatActivity {

    private TextView tvReceiptTxId, tvReceiptDate, tvReceiptItems, tvReceiptTotal;
    private Button btnNewSale, btnVoidFromReceipt;
    private int transactionId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt);

        tvReceiptTxId = findViewById(R.id.tvReceiptTxId);
        tvReceiptDate = findViewById(R.id.tvReceiptDate);
        tvReceiptItems = findViewById(R.id.tvReceiptItems);
        tvReceiptTotal = findViewById(R.id.tvReceiptTotal);
        btnNewSale = findViewById(R.id.btnNewSale);
        btnVoidFromReceipt = findViewById(R.id.btnVoidFromReceipt);

        transactionId = getIntent().getIntExtra("transaction_id", -1);
        double total = getIntent().getDoubleExtra("total_amount", 0.0);
        String summary = getIntent().getStringExtra("items_summary");

        if (summary == null || summary.isEmpty()) {
            summary = "No items recorded.";
        }

        String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        tvReceiptTxId.setText(String.format("TRANS #: %06d", (transactionId != -1 ? transactionId : 0)));
        tvReceiptDate.setText("DATE   : " + currentDate);
        tvReceiptItems.setText(summary);
        tvReceiptTotal.setText(String.format("₱%.2f", total));

        btnNewSale.setOnClickListener(v -> finish());

        if (btnVoidFromReceipt != null) {
            btnVoidFromReceipt.setOnClickListener(v -> showVoidPrompt());
        }
    }

    private void showVoidPrompt() {
        if (transactionId <= 0) {
            Toast.makeText(this, "Invalid transaction identifier", Toast.LENGTH_SHORT).show();
            return;
        }

        final EditText inputReason = new EditText(this);
        inputReason.setHint("e.g. Customer changed mind, wrong item punched");
        inputReason.setPadding(40, 20, 40, 20);

        new AlertDialog.Builder(this)
                .setTitle("Void Transaction #" + transactionId + "?")
                .setMessage("This will immediately cancel the transaction, return all punched items to store inventory, and mark the receipt as VOIDED.")
                .setView(inputReason)
                .setPositiveButton("Void Now", (dialog, which) -> {
                    String reason = inputReason.getText().toString().trim();
                    if (reason.isEmpty()) {
                        reason = "Customer changed mind at counter";
                    }
                    performVoid(reason);
                })
                .setNegativeButton("Keep Sale", null)
                .show();
    }

    private void performVoid(String reason) {
        String url = ApiConfig.getUrl("void_transaction.php");
        JSONObject payload = new JSONObject();
        try {
            payload.put("transaction_id", transactionId);
            payload.put("reason", reason);
        } catch (JSONException ignored) {}

        btnVoidFromReceipt.setEnabled(false);
        btnVoidFromReceipt.setText("Voiding...");

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, url, payload,
                response -> {
                    boolean success = response.optBoolean("success", false);
                    String msg = response.optString("message", "Transaction voided");
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    if (success) {
                        tvReceiptTxId.setText(String.format("TRANS #: %06d (VOIDED)", transactionId));
                        tvReceiptTxId.setTextColor(0xFFDC2626);
                        btnVoidFromReceipt.setEnabled(false);
                        btnVoidFromReceipt.setText("VOIDED");
                    } else {
                        btnVoidFromReceipt.setEnabled(true);
                        btnVoidFromReceipt.setText("Void Transaction");
                    }
                },
                error -> {
                    btnVoidFromReceipt.setEnabled(true);
                    btnVoidFromReceipt.setText("Void Transaction");
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to void transaction", Toast.LENGTH_SHORT).show();
                }
        );

        ApiSession.getRequestQueue(this).add(request);
    }
}