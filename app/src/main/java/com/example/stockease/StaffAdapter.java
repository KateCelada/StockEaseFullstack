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
        holder.tvStaffUsername.setText(staff.getUsername());
        holder.tvStaffEmail.setText(staff.getEmail().isEmpty() ? "N/A" : staff.getEmail());
        String statusText = staff.isActive() ? "Active" : "Inactive";
        holder.tvStaffStatus.setText(statusText);

        if (staff.isActive()) {
            holder.tvStaffStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
        } else {
            holder.tvStaffStatus.setTextColor(Color.parseColor("#F44336")); // Red
        }
    }

    @Override
    public int getItemCount() {
        return staffList.size();
    }

    public static class StaffViewHolder extends RecyclerView.ViewHolder {
        TextView tvStaffName, tvStaffUsername, tvStaffEmail, tvStaffStatus;

        public StaffViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStaffName = itemView.findViewById(R.id.tvStaffName);
            tvStaffUsername = itemView.findViewById(R.id.tvStaffUsername);
            tvStaffEmail = itemView.findViewById(R.id.tvStaffEmail);
            tvStaffStatus = itemView.findViewById(R.id.tvStaffStatus);
        }
    }
}
