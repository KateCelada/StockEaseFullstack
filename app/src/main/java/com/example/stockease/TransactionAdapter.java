package com.example.stockease;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    public interface OnTransactionVoidListener {
        void onVoidRequested(Transaction transaction);
    }

    private Context context;
    private List<Transaction> transactionList;
    private OnTransactionVoidListener voidListener;

    public TransactionAdapter(Context context, List<Transaction> transactionList) {
        this(context, transactionList, null);
    }

    public TransactionAdapter(Context context, List<Transaction> transactionList, OnTransactionVoidListener voidListener) {
        this.context = context;
        this.transactionList = transactionList;
        this.voidListener = voidListener;
    }

    public void setOnTransactionVoidListener(OnTransactionVoidListener listener) {
        this.voidListener = listener;
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
        String dutyInfo = "Cashier on duty: " + transaction.getUserName();
        if (!transaction.getCashierUsername().isEmpty()) {
            dutyInfo += " (@" + transaction.getCashierUsername() + ")";
        }
        dutyInfo += " • " + transaction.getPaymentType();
        holder.tvTxMeta.setText(dutyInfo);
        holder.tvTxDate.setText(transaction.getTransactionDate());
        holder.tvTxSummary.setText(transaction.getItemsSummary() != null ? transaction.getItemsSummary() : "No item details");

        boolean isVoided = transaction.isVoided();
        if (isVoided) {
            holder.tvTxStatusBadge.setText("VOIDED");
            holder.tvTxStatusBadge.setTextColor(Color.parseColor("#991B1B"));
            holder.tvTxStatusBadge.setBackgroundColor(Color.parseColor("#FEE2E2"));
            holder.tvTxTotal.setText(String.format("₱%.2f", transaction.getTotalAmount()));
            holder.tvTxTotal.setPaintFlags(holder.tvTxTotal.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.tvTxTotal.setTextColor(Color.parseColor("#94A3B8"));
            holder.btnVoidTx.setVisibility(View.GONE);
        } else {
            holder.tvTxStatusBadge.setText("COMPLETED");
            holder.tvTxStatusBadge.setTextColor(Color.parseColor("#166534"));
            holder.tvTxStatusBadge.setBackgroundColor(Color.parseColor("#DCFCE7"));
            holder.tvTxTotal.setText(String.format("₱%.2f", transaction.getTotalAmount()));
            holder.tvTxTotal.setPaintFlags(holder.tvTxTotal.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.tvTxTotal.setTextColor(Color.parseColor("#16A34A"));
            holder.btnVoidTx.setVisibility(View.VISIBLE);
        }

        holder.btnVoidTx.setOnClickListener(v -> {
            if (voidListener != null) {
                voidListener.onVoidRequested(transaction);
            }
        });

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
        TextView tvTxId, tvTxStatusBadge, tvTxTotal, tvTxMeta, tvTxDate, tvTxSummary;
        LinearLayout layoutTxActions;
        Button btnVoidTx;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTxId = itemView.findViewById(R.id.tvTxId);
            tvTxStatusBadge = itemView.findViewById(R.id.tvTxStatusBadge);
            tvTxTotal = itemView.findViewById(R.id.tvTxTotal);
            tvTxMeta = itemView.findViewById(R.id.tvTxMeta);
            tvTxDate = itemView.findViewById(R.id.tvTxDate);
            tvTxSummary = itemView.findViewById(R.id.tvTxSummary);
            layoutTxActions = itemView.findViewById(R.id.layoutTxActions);
            btnVoidTx = itemView.findViewById(R.id.btnVoidTx);
        }
    }
}
