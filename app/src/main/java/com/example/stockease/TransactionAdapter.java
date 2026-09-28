package com.example.stockease;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    private Context context;
    private List<Transaction> transactionList;

    public TransactionAdapter(Context context, List<Transaction> transactionList) {
        this.context = context;
        this.transactionList = transactionList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Transaction transaction = transactionList.get(position);

        holder.tvTxId.setText("Transaction #" + transaction.getId());
        holder.tvTxMeta.setText("User: " + transaction.getUserName() + " | " + transaction.getPaymentType());
        holder.tvTxDate.setText(transaction.getTransactionDate());
        holder.tvTxTotal.setText(String.format("₱%.2f", transaction.getTotalAmount()));
        holder.tvTxSummary.setText(transaction.getItemsSummary() != null ? transaction.getItemsSummary() : "No item details");

        // Tap card to view thermal paper receipt layout
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ReceiptActivity.class);
            intent.putExtra("transaction_id", transaction.getId());
            intent.putExtra("total_amount", transaction.getTotalAmount());
            intent.putExtra("items_summary", transaction.getItemsSummary());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return transactionList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTxId, tvTxTotal, tvTxMeta, tvTxDate, tvTxSummary;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTxId = itemView.findViewById(R.id.tvTxId);
            tvTxTotal = itemView.findViewById(R.id.tvTxTotal);
            tvTxMeta = itemView.findViewById(R.id.tvTxMeta);
            tvTxDate = itemView.findViewById(R.id.tvTxDate);
            tvTxSummary = itemView.findViewById(R.id.tvTxSummary);
        }
    }
}
