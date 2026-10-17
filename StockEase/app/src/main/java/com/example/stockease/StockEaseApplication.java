package com.example.stockease;

import android.app.Application;

public class StockEaseApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ApiSession.initialize(this);
    }
}
