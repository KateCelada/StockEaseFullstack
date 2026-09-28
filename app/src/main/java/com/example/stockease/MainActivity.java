package com.example.stockease;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Find navigation elements
        TextView btnInventory = findViewById(R.id.btnNavInventory);
        TextView btnProfile = findViewById(R.id.btnNavProfile);

        // Open InventoryActivity when Inventory is clicked
        if (btnInventory != null) {
            btnInventory.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, InventoryActivity.class);
                startActivity(intent);
            });
        }

        // Open ProfileActivity when Hilbert/Staff header is clicked
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                startActivity(intent);
            });
        }
    }
}