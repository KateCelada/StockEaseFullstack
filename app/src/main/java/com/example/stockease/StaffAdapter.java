package com.example.stockease;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class StaffAdapter extends RecyclerView.Adapter<StaffAdapter.StaffViewHolder> {

    private Context context;
    private List<Staff> staffList;

    public StaffAdapter(Context context, List<Staff> staffList) {
        this.context = context;
        this.staffList = staffList;
    }

    @NonNull
    @Override
    public StaffViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_staff, parent, false);
        return new StaffViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StaffViewHolder holder, int position) {
        Staff staff = staffList.get(position);
        holder.tvStaffName.setText(staff.getFullName());
        holder.tvStaffUsername.setText("@" + staff.getUsername());
        holder.tvStaffEmail.setText(staff.getEmail().isEmpty() ? "No email" : staff.getEmail());
        
        if (holder.tvStaffRole != null) {
            holder.tvStaffRole.setText(staff.getRoleDisplay());
            if ("inventory_checker".equalsIgnoreCase(staff.getRole())) {
                holder.tvStaffRole.setTextColor(Color.parseColor("#7C3AED")); // Purple for inventory checker
            } else {
                holder.tvStaffRole.setTextColor(Color.parseColor("#2563EB")); // Blue for cashier
            }
        }

        String statusText = staff.isActive() ? "Active" : "Inactive";
        holder.tvStaffStatus.setText(statusText);

        if (staff.isActive()) {
            holder.tvStaffStatus.setTextColor(Color.parseColor("#10B981")); // Emerald
        } else {
            holder.tvStaffStatus.setTextColor(Color.parseColor("#EF4444")); // Red
        }
    }

    @Override
    public int getItemCount() {
        return staffList.size();
    }

    public static class StaffViewHolder extends RecyclerView.ViewHolder {
        TextView tvStaffName, tvStaffRole, tvStaffUsername, tvStaffEmail, tvStaffStatus;

        public StaffViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStaffName = itemView.findViewById(R.id.tvStaffName);
            tvStaffRole = itemView.findViewById(R.id.tvStaffRole);
            tvStaffUsername = itemView.findViewById(R.id.tvStaffUsername);
            tvStaffEmail = itemView.findViewById(R.id.tvStaffEmail);
            tvStaffStatus = itemView.findViewById(R.id.tvStaffStatus);
        }
    }
}
