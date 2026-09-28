package com.example.stockease;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class StoreAdminAdapter extends RecyclerView.Adapter<StoreAdminAdapter.AdminViewHolder> {

    private final List<AdminItem> adminList;

    public static class AdminItem {
        int id;
        String fullName;
        String username;
        String storeName;

        public AdminItem(int id, String fullName, String username, String storeName) {
            this.id = id;
            this.fullName = fullName;
            this.username = username;
            this.storeName = storeName;
        }
    }

    public StoreAdminAdapter(List<AdminItem> adminList) {
        this.adminList = adminList;
    }

    @NonNull
    @Override
    public AdminViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_store_admin, parent, false);
        return new AdminViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AdminViewHolder holder, int position) {
        AdminItem item = adminList.get(position);
        holder.tvAdminFullName.setText(item.fullName);
        holder.tvAdminUsername.setText("@" + item.username);
        holder.tvAdminUsername.setTag(item.id);
        holder.tvAssignedStore.setText(item.storeName);
    }

    @Override
    public int getItemCount() {
        return adminList != null ? adminList.size() : 0;
    }

    static class AdminViewHolder extends RecyclerView.ViewHolder {
        TextView tvAdminFullName, tvAdminUsername, tvAssignedStore;

        public AdminViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAdminFullName = itemView.findViewById(R.id.tvAdminFullName);
            tvAdminUsername = itemView.findViewById(R.id.tvAdminUsername);
            tvAssignedStore = itemView.findViewById(R.id.tvAssignedStore);
        }
    }
}