package com.example.stockease;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReceiptActivity extends AppCompatActivity {

    private TextView tvReceiptTxId, tvReceiptDate, tvReceiptItems, tvReceiptTotal;
    private Button btnNewSale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt);

        tvReceiptTxId = findViewById(R.id.tvReceiptTxId);
        tvReceiptDate = findViewById(R.id.tvReceiptDate);
        tvReceiptItems = findViewById(R.id.tvReceiptItems);
        tvReceiptTotal = findViewById(R.id.tvReceiptTotal);
        btnNewSale = findViewById(R.id.btnNewSale);

        int txId = getIntent().getIntExtra("transaction_id", -1);
        double total = getIntent().getDoubleExtra("total_amount", 0.0);
        String summary = getIntent().getStringExtra("items_summary");

        if (summary == null || summary.isEmpty()) {
            summary = "No items recorded.";
        }

        String currentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        tvReceiptTxId.setText(String.format("TRANS #: %06d", (txId != -1 ? txId : 0)));
        tvReceiptDate.setText("DATE   : " + currentDate);
        tvReceiptItems.setText(summary);
        tvReceiptTotal.setText(String.format("₱%.2f", total));

        btnNewSale.setOnClickListener(v -> finish());
    }
}