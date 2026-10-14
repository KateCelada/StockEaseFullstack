package com.example.stockease;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class StoreAdapter extends RecyclerView.Adapter<StoreAdapter.StoreViewHolder> {

    public interface OnStoreClickListener {
        void onStoreClick(StoreItem store);
    }

    public static class StoreItem {
        private final int id;
        private final String storeName;
        private final String location;
        private final String status;
        private final int adminCount;

        public StoreItem(int id, String storeName, String location, String status, int adminCount) {
            this.id = id;
            this.storeName = storeName;
            this.location = location;
            this.status = status;
            this.adminCount = adminCount;
        }

        public int getId() { return id; }
        public String getStoreName() { return storeName; }
        public String getLocation() { return location; }
        public String getStatus() { return status; }
        public int getAdminCount() { return adminCount; }
    }

    private final List<StoreItem> storeList;
    private final OnStoreClickListener clickListener;

    public StoreAdapter(List<StoreItem> storeList) {
        this(storeList, null);
    }

    public StoreAdapter(List<StoreItem> storeList, OnStoreClickListener clickListener) {
        this.storeList = storeList;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public StoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_store, parent, false);
        return new StoreViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StoreViewHolder holder, int position) {
        StoreItem store = storeList.get(position);

        if (holder.tvFullName != null) {
            holder.tvFullName.setText(store.getStoreName());
        }
        if (holder.tvUsername != null) {
            holder.tvUsername.setText(store.getLocation());
        }
        if (holder.tvRole != null) {
            holder.tvRole.setText(store.getStatus() + " (" + store.getAdminCount() + " Admin)");
            if ("Active".equalsIgnoreCase(store.getStatus())) {
                holder.tvRole.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.success_strong));
                holder.tvRole.setBackgroundResource(R.drawable.bg_badge_emerald);
            } else {
                holder.tvRole.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
                holder.tvRole.setBackgroundResource(R.drawable.bg_badge_gray);
            }
        }

        holder.itemView.setOnClickListener(clickListener == null
                ? null
                : v -> clickListener.onStoreClick(store));
        holder.itemView.setClickable(clickListener != null);
    }

    @Override
    public int getItemCount() {
        return storeList != null ? storeList.size() : 0;
    }

    public static class StoreViewHolder extends RecyclerView.ViewHolder {
        TextView tvFullName, tvUsername, tvRole;

        public StoreViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFullName = itemView.findViewById(R.id.tvFullName);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvRole = itemView.findViewById(R.id.tvRole);
        }
    }
}
