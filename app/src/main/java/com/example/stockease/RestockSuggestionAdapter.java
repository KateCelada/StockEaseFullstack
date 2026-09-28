package com.example.stockease;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class RestockSuggestionAdapter extends RecyclerView.Adapter<RestockSuggestionAdapter.RestockViewHolder> {

    public interface OnRestockClickListener {
        void onRestockClick(Product product, int suggestedQty);
    }

    private static final int DEFAULT_PAR_LEVEL = 30;
    private final List<Product> productList;
    private final OnRestockClickListener listener;

    public RestockSuggestionAdapter(List<Product> productList, OnRestockClickListener listener) {
        this.productList = productList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RestockViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_restock_suggestion, parent, false);
        return new RestockViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RestockViewHolder holder, int position) {
        Product product = productList.get(position);

        int currentStock = product.getStock();
        int suggestedQty = Math.max(0, DEFAULT_PAR_LEVEL - currentStock);
        double estCost = suggestedQty * product.getPrice();

        holder.tvName.setText(product.getName());
        holder.tvCategory.setText("Category: " + product.getCategoryName());
        holder.tvCurrentStock.setText(currentStock + " units");
        holder.tvTargetPar.setText(DEFAULT_PAR_LEVEL + " units");
        holder.tvSuggestedQty.setText("+" + suggestedQty + " units");
        holder.tvEstCost.setText(String.format(Locale.US, "₱%.2f", estCost));
        holder.tvUnitPrice.setText(String.format(Locale.US, "Unit Price: ₱%.2f", product.getPrice()));

        if (currentStock < 5) {
            holder.tvUrgencyBadge.setText("CRITICAL");
            holder.tvUrgencyBadge.setTextColor(Color.parseColor("#C62828"));
            holder.tvUrgencyBadge.setBackgroundColor(Color.parseColor("#FFEBEE"));
            holder.tvCurrentStock.setTextColor(Color.parseColor("#C62828"));
        } else {
            holder.tvUrgencyBadge.setText("LOW STOCK");
            holder.tvUrgencyBadge.setTextColor(Color.parseColor("#E65100"));
            holder.tvUrgencyBadge.setBackgroundColor(Color.parseColor("#FFF3E0"));
            holder.tvCurrentStock.setTextColor(Color.parseColor("#E65100"));
        }

        holder.btnRestock.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRestockClick(product, suggestedQty);
            }
        });
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public static class RestockViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvUrgencyBadge, tvCategory;
        TextView tvCurrentStock, tvTargetPar, tvSuggestedQty, tvEstCost, tvUnitPrice;
        Button btnRestock;

        public RestockViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvRestockProductName);
            tvUrgencyBadge = itemView.findViewById(R.id.tvRestockUrgencyBadge);
            tvCategory = itemView.findViewById(R.id.tvRestockCategory);
            tvCurrentStock = itemView.findViewById(R.id.tvRestockCurrentStock);
            tvTargetPar = itemView.findViewById(R.id.tvRestockTargetPar);
            tvSuggestedQty = itemView.findViewById(R.id.tvRestockSuggestedQty);
            tvEstCost = itemView.findViewById(R.id.tvRestockEstCost);
            tvUnitPrice = itemView.findViewById(R.id.tvRestockUnitPrice);
            btnRestock = itemView.findViewById(R.id.btnRestockAction);
        }
    }
}
