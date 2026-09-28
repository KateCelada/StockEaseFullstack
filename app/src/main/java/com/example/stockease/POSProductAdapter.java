package com.example.stockease;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class POSProductAdapter extends RecyclerView.Adapter<POSProductAdapter.POSProductViewHolder> {

    public interface OnProductClickListener {
        void onProductAdd(Product product);
    }

    private Context context;
    private List<Product> productList;
    private OnProductClickListener listener;

    public POSProductAdapter(Context context, List<Product> productList, OnProductClickListener listener) {
        this.context = context;
        this.productList = productList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public POSProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_pos_product, parent, false);
        return new POSProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull POSProductViewHolder holder, int position) {
        Product product = productList.get(position);
        holder.tvPosProductName.setText(product.getName());
        holder.tvPosProductStock.setText("Stock: " + product.getStock());
        holder.tvPosProductPrice.setText(String.format("₱%.2f", product.getPrice()));

        holder.btnAddToCart.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProductAdd(product);
            }
        });
    }

    @Override
    public int getItemCount() {
        return productList != null ? productList.size() : 0;
    }

    public static class POSProductViewHolder extends RecyclerView.ViewHolder {
        TextView tvPosProductName, tvPosProductStock, tvPosProductPrice;
        Button btnAddToCart;

        public POSProductViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPosProductName = itemView.findViewById(R.id.tvPosProductName);
            tvPosProductStock = itemView.findViewById(R.id.tvPosProductStock);
            tvPosProductPrice = itemView.findViewById(R.id.tvPosProductPrice);
            btnAddToCart = itemView.findViewById(R.id.btnAddToCart);
        }
    }
}