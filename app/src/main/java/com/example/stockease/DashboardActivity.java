package com.example.stockease;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Hide action bar for a clean UI matching the image
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
    }
}