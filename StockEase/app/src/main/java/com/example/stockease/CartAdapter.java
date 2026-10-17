package com.example.stockease;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

    public interface OnCartChangeListener {
        void onCartUpdated();
    }

    private Context context;
    private List<CartItem> cartList;
    private OnCartChangeListener listener;

    public CartAdapter(Context context, List<CartItem> cartList, OnCartChangeListener listener) {
        this.context = context;
        this.cartList = cartList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart, parent, false);
        return new CartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        CartItem cartItem = cartList.get(position);
        holder.tvName.setText(cartItem.getProduct().getName());
        holder.tvQuantity.setText(String.valueOf(cartItem.getQuantity()));
        holder.tvPrice.setText(String.format(Locale.US, "₱%.2f", cartItem.getTotalPrice()));

        // Increase quantity up to max available stock
        holder.btnIncrease.setOnClickListener(v -> {
            if (cartItem.getQuantity() < cartItem.getProduct().getStock()) {
                cartItem.setQuantity(cartItem.getQuantity() + 1);
                notifyItemChanged(position);
                if (listener != null) listener.onCartUpdated();
            }
        });

        // Decrease quantity or remove item if quantity drops to 0
        holder.btnDecrease.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                if (cartItem.getQuantity() > 1) {
                    cartItem.setQuantity(cartItem.getQuantity() - 1);
                    notifyItemChanged(currentPos);
                } else {
                    cartList.remove(currentPos);
                    notifyItemRemoved(currentPos);
                    notifyItemRangeChanged(currentPos, cartList.size());
                }
                if (listener != null) listener.onCartUpdated();
            }
        });

        // Trash/Delete button to directly remove item from cart
        holder.btnRemove.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                cartList.remove(currentPos);
                notifyItemRemoved(currentPos);
                notifyItemRangeChanged(currentPos, cartList.size());
                if (listener != null) listener.onCartUpdated();
            }
        });
    }

    @Override
    public int getItemCount() {
        return cartList.size();
    }

    public static class CartViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvQuantity, tvPrice;
        ImageButton btnIncrease, btnDecrease, btnRemove;

        public CartViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCartProductName);
            tvQuantity = itemView.findViewById(R.id.tvCartQuantity);
            tvPrice = itemView.findViewById(R.id.tvCartPrice);
            btnIncrease = itemView.findViewById(R.id.btnIncreaseQty);
            btnDecrease = itemView.findViewById(R.id.btnDecreaseQty);
            btnRemove = itemView.findViewById(R.id.btnRemoveCartItem);
        }
    }
}
