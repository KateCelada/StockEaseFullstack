package com.example.stockease;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    private List<Product> productList;
    private OnProductClickListener listener;

    public ProductAdapter(List<Product> productList) {
        this(productList, null);
    }

    public ProductAdapter(List<Product> productList, OnProductClickListener listener) {
        this.productList = productList;
        this.listener = listener;
    }

    public void setOnProductClickListener(OnProductClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = productList.get(position);
        holder.tvName.setText(product.getName());
        holder.tvPrice.setText(String.format(Locale.getDefault(), "₱%.2f", product.getPrice()));
        holder.tvCategory.setText("Category: " + product.getCategoryName());
        holder.tvQty.setText("Stock: " + product.getStock());

        if (holder.tvCost != null) {
            holder.tvCost.setText(String.format(Locale.getDefault(), "Cost: ₱%.2f", product.getCostPrice()));
        }

        if (holder.tvProfit != null) {
            double profit = product.getGrossProfit();
            holder.tvProfit.setText(String.format(Locale.getDefault(), "Profit: %s₱%.2f", profit >= 0 ? "+" : "-", Math.abs(profit)));
            int colorRes = profit >= 0 ? R.color.profit : R.color.danger_strong;
            holder.tvProfit.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), colorRes));
        }

        if (holder.tvBarcode != null) {
            holder.tvBarcode.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProductClick(product);
            }
        });
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public static class ProductViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvPrice, tvCategory, tvQty, tvCost, tvProfit, tvBarcode;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvProductName);
            tvPrice = itemView.findViewById(R.id.tvProductPrice);
            tvCategory = itemView.findViewById(R.id.tvProductCategory);
            tvQty = itemView.findViewById(R.id.tvProductQty);
            tvCost = itemView.findViewById(R.id.tvProductCost);
            tvProfit = itemView.findViewById(R.id.tvProductProfit);
            tvBarcode = itemView.findViewById(R.id.tvProductBarcode);
        }
    }
}
