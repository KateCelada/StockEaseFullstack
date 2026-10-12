package com.example.stockease;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.nio.charset.StandardCharsets;

public final class ApiSession {
    private static final String LOGOUT_URL = ApiConfig.getUrl("logout.php");

    private static CookieManager cookieManager;
    private static RequestQueue requestQueue;

    private ApiSession() {
    }

    public static synchronized void initialize(Context context) {
        if (cookieManager == null) {
            cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            CookieHandler.setDefault(cookieManager);
        }
        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(context.getApplicationContext());
        }
    }

    public static RequestQueue getRequestQueue(Context context) {
        initialize(context);
        return requestQueue;
    }

    public static boolean handleAuthError(Activity activity, VolleyError error) {
        if (error.networkResponse == null || error.networkResponse.statusCode != 401) {
            return false;
        }

        clearAndReturnToLogin(activity, "Your session expired. Please sign in again.");
        return true;
    }

    /**
     * Checks if the server returned HTTP 403 PREMIUM_REQUIRED
     */
    public static boolean isPremiumRequired(VolleyError error) {
        if (error == null || error.networkResponse == null) {
            return false;
        }
        if (error.networkResponse.statusCode == 403 && error.networkResponse.data != null) {
            String body = new String(error.networkResponse.data, StandardCharsets.UTF_8);
            return body.contains("PREMIUM_REQUIRED");
        }
        return false;
    }

    /**
     * Displays a clean enterprise upgrade modal when a user discovers a Premium feature
     */
    public static void showPremiumUpgradeDialog(Activity activity, String featureName, String explanation) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_premium_upgrade, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tvPremiumFeatureTitle);
        TextView tvDesc = dialogView.findViewById(R.id.tvPremiumFeatureExplanation);
        Button btnViewPremium = dialogView.findViewById(R.id.btnViewPremium);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelUpgrade);

        if (tvTitle != null) {
            tvTitle.setText(featureName + " (Premium)");
        }
        if (tvDesc != null && explanation != null && !explanation.isEmpty()) {
            tvDesc.setText(explanation);
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                dialog.dismiss();
                if (activity != null && !activity.isFinishing()) {
                    activity.finish();
                }
            });
        }
        dialog.setOnCancelListener(d -> {
            if (activity != null && !activity.isFinishing()) {
                activity.finish();
            }
        });

        if (btnViewPremium != null) {
            btnViewPremium.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(activity, SubscriptionActivity.class);
                if (activity.getIntent() != null && activity.getIntent().getExtras() != null) {
                    intent.putExtras(activity.getIntent().getExtras());
                }
                activity.startActivity(intent);
            });
        }

        dialog.show();
    }

    public static void logout(Activity activity) {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                LOGOUT_URL,
                null,
                response -> clearAndReturnToLogin(activity, "Logged out"),
                error -> clearAndReturnToLogin(activity, "Logged out")
        );
        getRequestQueue(activity).add(request);
    }

    public static void clearAndReturnToLogin(Activity activity, String message) {
        initialize(activity);
        cookieManager.getCookieStore().removeAll();

        if (message != null && !message.isEmpty()) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }

        Intent intent = new Intent(activity, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
