package com.example.stockease;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {

    private Button btnReturn;
    private TextView btnEditProfile, navDashboard, navPos, navInventory, navLogout;
    private TextView tvName, tvRole, tvAddress, tvEmail, tvPhone, tvOccupation, tvSex, tvBranch, tvIdNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize UI Elements
        initViews();

        // Setup Click Listeners
        setupListeners();
    }

    private void initViews() {
        btnReturn = findViewById(R.id.btnReturn);
        btnEditProfile = findViewById(R.id.btnEditProfile);

        // Sidebar Navigation
        navDashboard = findViewById(R.id.navDashboard);
        navPos = findViewById(R.id.navPos);
        navInventory = findViewById(R.id.navInventory);
        navLogout = findViewById(R.id.navLogout);

        // Details
        tvName = findViewById(R.id.tvName);
        tvRole = findViewById(R.id.tvRole);
        tvAddress = findViewById(R.id.tvAddress);
        tvEmail = findViewById(R.id.tvEmail);
        tvPhone = findViewById(R.id.tvPhone);
        tvOccupation = findViewById(R.id.tvOccupation);
        tvSex = findViewById(R.id.tvSex);
        tvBranch = findViewById(R.id.tvBranch);
        tvIdNumber = findViewById(R.id.tvIdNumber);
    }

    private void setupListeners() {
        // Return button finishes current activity and goes back
        btnReturn.setOnClickListener(v -> finish());

        // Edit button action
        btnEditProfile.setOnClickListener(v ->
                Toast.makeText(ProfileActivity.this, "Edit profile clicked", Toast.LENGTH_SHORT).show()
        );

        // Sidebar Actions
        navDashboard.setOnClickListener(v ->
                Toast.makeText(ProfileActivity.this, "Navigating to Dashboard", Toast.LENGTH_SHORT).show()
        );

        navPos.setOnClickListener(v ->
                Toast.makeText(ProfileActivity.this, "Navigating to POS/Sales", Toast.LENGTH_SHORT).show()
        );

        navInventory.setOnClickListener(v ->
                Toast.makeText(ProfileActivity.this, "Navigating to Inventory", Toast.LENGTH_SHORT).show()
        );

        navLogout.setOnClickListener(v ->
                Toast.makeText(ProfileActivity.this, "Logging out...", Toast.LENGTH_SHORT).show()
        );
    }
}