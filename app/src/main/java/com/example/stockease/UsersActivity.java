package com.example.stockease;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsersActivity extends AppCompatActivity {

    private static final String USERS_URL = ApiConfig.getUrl("get_users.php");
    private static final String UPDATE_ROLE_URL = ApiConfig.getUrl("update_role.php");
    private static final String STORES_URL = ApiConfig.getUrl("manage_stores.php?action=fetch");
    private static final String[] CANONICAL_ROLES = {"super_admin", "store_admin", "staff"};

    private final List<User> users = new ArrayList<>();
    private final Map<Integer, String> storeNames = new HashMap<>();
    private UserAdapter adapter;
    private int authenticatedUserId = -1;
    private String authenticatedRole = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_users);

        authenticatedUserId = getIntent().getIntExtra("user_id", -1);
        authenticatedRole = getIntent().getStringExtra("role");

        if (authenticatedUserId <= 0 || !"super_admin".equals(authenticatedRole)) {
            returnToLogin("Users management is available only to Super Admin accounts.");
            return;
        }

        RecyclerView rvUsers = findViewById(R.id.rvUsers);
        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserAdapter(users, this::showRoleDialog);
        rvUsers.setAdapter(adapter);

        loadStoresThenUsers();
    }

    private void loadStoresThenUsers() {
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                STORES_URL,
                null,
                response -> {
                    try {
                        if (response.optBoolean("success", false)) {
                            JSONArray stores = response.getJSONArray("stores");
                            storeNames.clear();
                            for (int i = 0; i < stores.length(); i++) {
                                JSONObject store = stores.getJSONObject(i);
                                int storeId = store.getInt("id");
                                if (storeId > 0) {
                                    storeNames.put(storeId, store.optString("store_name", "Store ID " + storeId));
                                }
                            }
                        }
                    } catch (JSONException e) {
                        Toast.makeText(this, "Unable to parse store assignments", Toast.LENGTH_SHORT).show();
                    }
                    loadUsers();
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    loadUsers();
                }
        );
        queue.add(request);
    }

    private void loadUsers() {
        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                USERS_URL,
                null,
                response -> {
                    try {
                        if (!response.optBoolean("success", false)) {
                            Toast.makeText(this, response.optString("message", "Unable to load users"), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        JSONArray userArray = response.getJSONArray("users");
                        users.clear();
                        for (int i = 0; i < userArray.length(); i++) {
                            JSONObject item = userArray.getJSONObject(i);
                            int storeId = item.isNull("store_id") ? -1 : item.optInt("store_id", -1);
                            String storeName = storeId > 0
                                    ? storeNames.getOrDefault(storeId, "Store ID " + storeId)
                                    : "No store assigned";

                            users.add(new User(
                                    item.getInt("id"),
                                    item.optString("full_name", "Unnamed user"),
                                    item.optString("username", ""),
                                    item.optString("email", ""),
                                    item.optString("role", ""),
                                    storeId,
                                    storeName,
                                    item.optBoolean("is_active", false),
                                    item.optString("created_at", "")
                            ));
                        }
                        adapter.notifyDataSetChanged();
                    } catch (JSONException e) {
                        Toast.makeText(this, "Unable to parse users", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to load users", Toast.LENGTH_SHORT).show();
                }
        );
        queue.add(request);
    }

    private void showRoleDialog(User targetUser) {
        if (targetUser == null || targetUser.getId() <= 0) {
            Toast.makeText(this, "Invalid user account", Toast.LENGTH_SHORT).show();
            return;
        }

        if (targetUser.getId() == authenticatedUserId) {
            Toast.makeText(this, "You cannot change the role of your active account", Toast.LENGTH_LONG).show();
            return;
        }

        int currentIndex = 0;
        for (int i = 0; i < CANONICAL_ROLES.length; i++) {
            if (CANONICAL_ROLES[i].equals(targetUser.getRole())) {
                currentIndex = i;
                break;
            }
        }
        final int[] selectedIndex = {currentIndex};

        new AlertDialog.Builder(this)
                .setTitle("Change role: " + targetUser.getFullName())
                .setSingleChoiceItems(CANONICAL_ROLES, currentIndex,
                        (dialog, which) -> selectedIndex[0] = which)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Update", (dialog, which) -> {
                    String selectedRole = CANONICAL_ROLES[selectedIndex[0]];
                    if (selectedRole.equals(targetUser.getRole())) return;

                    if (!"super_admin".equals(selectedRole) && targetUser.getStoreId() <= 0) {
                        Toast.makeText(this,
                                "This account has no store assignment. The current API cannot assign one during a role update.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    updateRole(targetUser, selectedRole);
                })
                .show();
    }

    private void updateRole(User targetUser, String newRole) {
        if (targetUser.getId() <= 0 || !isCanonicalRole(newRole)) {
            Toast.makeText(this, "Invalid user or role", Toast.LENGTH_SHORT).show();
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("user_id", targetUser.getId());
            body.put("role", newRole);
        } catch (JSONException e) {
            Toast.makeText(this, "Unable to prepare role update", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestQueue queue = ApiSession.getRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                UPDATE_ROLE_URL,
                body,
                response -> {
                    if (response.optBoolean("success", false)) {
                        targetUser.setRole(newRole);
                        adapter.notifyDataSetChanged();
                    }
                    Toast.makeText(this,
                            response.optString("message", "Role update completed"),
                            Toast.LENGTH_LONG).show();
                },
                error -> {
                    if (ApiSession.handleAuthError(this, error)) return;
                    Toast.makeText(this, "Failed to update user role", Toast.LENGTH_SHORT).show();
                }
        );
        queue.add(request);
    }

    private boolean isCanonicalRole(String role) {
        for (String allowedRole : CANONICAL_ROLES) {
            if (allowedRole.equals(role)) return true;
        }
        return false;
    }

    private void returnToLogin(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
