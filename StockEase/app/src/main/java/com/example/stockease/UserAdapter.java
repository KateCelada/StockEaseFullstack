package com.example.stockease;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private List<User> userList;
    private OnUserClickListener listener;

    public interface OnUserClickListener {
        void onUserClick(User user);
    }

    public UserAdapter(List<User> userList, OnUserClickListener listener) {
        this.userList = userList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = userList.get(position);
        holder.tvFullName.setText(user.getFullName());
        holder.tvUsername.setText("@" + user.getUsername());
        holder.tvEmail.setText(user.getEmail().isEmpty() ? "Email: Not provided" : "Email: " + user.getEmail());
        holder.tvRole.setText("Role: " + user.getRole());
        holder.tvStore.setText("Store: " + user.getStoreName());
        if (user.isActive()) {
            holder.tvActive.setText("Active");
            holder.tvActive.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.success_strong));
            holder.tvActive.setBackgroundResource(R.drawable.bg_badge_emerald);
        } else {
            holder.tvActive.setText("Inactive");
            holder.tvActive.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
            holder.tvActive.setBackgroundResource(R.drawable.bg_badge_gray);
        }

        holder.itemView.setOnClickListener(listener == null ? null : v -> listener.onUserClick(user));
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvFullName, tvUsername, tvEmail, tvRole, tvStore, tvActive;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFullName = itemView.findViewById(R.id.tvFullName);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvRole = itemView.findViewById(R.id.tvRole);
            tvStore = itemView.findViewById(R.id.tvStore);
            tvActive = itemView.findViewById(R.id.tvActive);
        }
    }
}
