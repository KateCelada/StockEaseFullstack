package com.example.stockease;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

public class ProfileActivity extends AppCompatActivity {

    private static final String PROFILE_URL = ApiConfig.getUrl("get_user_profile.php");
    private static final String UPDATE_PROFILE_URL = ApiConfig.getUrl("update_profile.php");

    private Button btnReturn;
    private TextView btnEditProfile, navDashboard, navPos, navInventory, navTransactions, navSettings, navLogout;
    private TextView tvName, tvRole, tvAddress, tvEmail, tvPhone, tvOccupation, tvSex, tvBranch, tvIdNumber, tvAvatar;

    // Profile model: intent extras act as instant placeholders while the
    // server response is loading, then get overwritten by get_user_profile.php.
    private int storeId = -1;
    private int userId = -1;
    private String profileFullName = "";
    private String profileEmail = "";
    private String profilePhone = "";
    private String profileAddress = "";
    private String profileOccupation = "";
    private String profileSex = "";
    private String profileRole = "";
    private String profileStore = "";
    private String profileUsername = "";
    private int profileId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize UI Elements
        initViews();

        // Setup Click Listeners
        setupListeners();

        // Collapsible sidebar sections (accordion): OVERVIEW opens by default
        SidebarSection.attach(this, R.id.navHeaderOverview, R.id.navArrowOverview, R.id.navSectionOverviewItems, true);
        SidebarSection.attach(this, R.id.navHeaderManagement, R.id.navArrowManagement, R.id.navSectionManagementItems, false);
        SidebarSection.attach(this, R.id.navHeaderAnalytics, R.id.navArrowAnalytics, R.id.navSectionAnalyticsItems, false);
        SidebarSection.attach(this, R.id.navHeaderSystem, R.id.navArrowSystem, R.id.navSectionSystemItems, false);

        // Tapping the profile card opens the same edit dialog as the Edit label
        View profileCard = findViewById(R.id.cardProfileDetails);
        if (profileCard != null) {
            profileCard.setOnClickListener(v -> showEditProfileDialog());
        }

        ViewFeedbackUtil.attachAll(findViewById(android.R.id.content));

        // Instant placeholders from the launching screen, then real data
        applyIntentPlaceholders();
        loadUserProfile();
    }

    private void initViews() {
        btnReturn = findViewById(R.id.btnReturn);
        btnEditProfile = findViewById(R.id.btnEditProfile);

        // Sidebar Navigation
        navDashboard = findViewById(R.id.navDashboard);
        navPos = findViewById(R.id.navPos);
        navInventory = findViewById(R.id.navInventory);
        navTransactions = findViewById(R.id.navTransactions);
        navSettings = findViewById(R.id.navSettings);
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
        tvAvatar = findViewById(R.id.tvAvatar);
    }

    private void setupListeners() {
        // Return button finishes current activity and goes back
        btnReturn.setOnClickListener(v -> finish());

        // Edit button opens the edit dialog
        btnEditProfile.setOnClickListener(v -> showEditProfileDialog());

        // Sidebar Actions
        navDashboard.setOnClickListener(v -> finish());

        navPos.setOnClickListener(v -> openWithStoreCheck(
                POSActivity.class, "POS/Sales is only available from your dashboard."));

        navInventory.setOnClickListener(v -> openWithStoreCheck(
                InventoryActivity.class, "Inventory is only available from your dashboard."));

        navTransactions.setOnClickListener(v -> openWithStoreCheck(
                TransactionHistoryActivity.class, "Transactions are only available from your dashboard."));

        navSettings.setOnClickListener(v -> {
            // Settings bounces non-admin sessions back to login, so only
            // launch it when this screen carries a valid admin identity.
            if (userId > 0 && isAdminRole(profileRole)) {
                Intent intent = new Intent(ProfileActivity.this, SettingsActivity.class);
                forwardExtras(intent);
                ScreenTransitions.open(ProfileActivity.this, intent);
            } else {
                Toast.makeText(this, "Settings is only available to administrator accounts.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        navLogout.setOnClickListener(v -> ApiSession.logout(this));
    }

    /** Launches a store-scoped screen when this session carried a valid identity. */
    private void openWithStoreCheck(Class<?> target, String fallbackMessage) {
        if (storeId > 0 && userId > 0) {
            Intent intent = new Intent(ProfileActivity.this, target);
            forwardExtras(intent);
            ScreenTransitions.open(ProfileActivity.this, intent);
        } else {
            Toast.makeText(this, fallbackMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean isAdminRole(String role) {
        return "super_admin".equals(role) || "store_admin".equals(role)
                || "manager".equals(role) || "store_owner".equals(role);
    }

    private void forwardExtras(Intent intent) {
        if (getIntent() != null && getIntent().getExtras() != null) {
            intent.putExtras(getIntent().getExtras());
        }
    }

    private void applyIntentPlaceholders() {
        Intent intent = getIntent();
        if (intent == null) {
            renderCard();
            return;
        }
        storeId = intent.getIntExtra("store_id", -1);
        userId = intent.getIntExtra("user_id", -1);
        profileFullName = safe(intent.getStringExtra("full_name"));
        profileEmail = safe(intent.getStringExtra("email"));
        profileRole = safe(intent.getStringExtra("role"));
        profileStore = safe(intent.getStringExtra("store_name"));
        profileUsername = safe(intent.getStringExtra("username"));
        renderCard();
    }

    private void loadUserProfile() {
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                PROFILE_URL,
                null,
                response -> {
                    try {
                        if (response.getBoolean("success")) {
                            applyUserObject(response.getJSONObject("user"));
                        }
                    } catch (JSONException ignored) {
                    }
                },
                error -> ApiSession.handleAuthError(this, error)
        );
        ApiSession.getRequestQueue(this).add(request);
    }

    /** Merges server values into the local model (new keys fall back to blanks). */
    private void applyUserObject(JSONObject user) {
        profileFullName = user.optString("full_name", profileFullName);
        profileEmail = user.optString("email", profileEmail);
        profilePhone = user.optString("phone", profilePhone);
        profileAddress = user.optString("address", profileAddress);
        profileOccupation = user.optString("occupation", profileOccupation);
        profileSex = user.optString("sex", profileSex);
        profileRole = user.optString("role", profileRole);
        profileStore = user.optString("store_name", profileStore);
        profileUsername = user.optString("username", profileUsername);
        if (user.has("id")) {
            profileId = user.optInt("id", profileId);
        }
        renderCard();
    }

    private void renderCard() {
        tvName.setText(fallback(profileFullName, "Not provided"));
        tvRole.setText(displayRole(profileRole));
        tvEmail.setText(fallback(profileEmail, "No email registered"));
        tvPhone.setText(fallback(profilePhone, "Not provided"));
        tvAddress.setText(fallback(profileAddress, "Not provided"));
        tvOccupation.setText(fallback(profileOccupation, "Not provided"));
        tvSex.setText("Sex: " + fallback(profileSex, "Not specified"));
        tvBranch.setText(fallback(profileStore, "No branch assigned"));
        tvIdNumber.setText("ID: " + (profileUsername.isEmpty()
                ? (profileId > 0 ? String.valueOf(profileId) : "—")
                : profileUsername));
        if (tvAvatar != null) {
            tvAvatar.setText(avatarInitial(profileFullName));
        }
    }

    private void showEditProfileDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);
        EditText etFullName = dialogView.findViewById(R.id.etEditFullName);
        EditText etEmail = dialogView.findViewById(R.id.etEditEmail);
        EditText etPhone = dialogView.findViewById(R.id.etEditPhone);
        EditText etAddress = dialogView.findViewById(R.id.etEditAddress);
        EditText etOccupation = dialogView.findViewById(R.id.etEditOccupation);
        EditText etSex = dialogView.findViewById(R.id.etEditSex);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelEditProfile);
        Button btnSave = dialogView.findViewById(R.id.btnSaveEditProfile);

        // Prefill from the profile card
        etFullName.setText(profileFullName);
        etEmail.setText(profileEmail);
        etPhone.setText(profilePhone);
        etAddress.setText(profileAddress);
        etOccupation.setText(profileOccupation);
        etSex.setText(profileSex);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String fullName = etFullName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String address = etAddress.getText().toString().trim();
            String occupation = etOccupation.getText().toString().trim();
            String sex = etSex.getText().toString().trim();

            if (fullName.isEmpty()) {
                Toast.makeText(this, "Full name cannot be empty.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isValidPhone(phone)) {
                Toast.makeText(this, "Please enter a valid phone number.", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSave.setEnabled(false);
            btnSave.setText("Saving...");

            JSONObject payload = new JSONObject();
            try {
                payload.put("full_name", fullName);
                payload.put("email", email);
                payload.put("phone", phone);
                payload.put("address", address);
                payload.put("occupation", occupation);
                payload.put("sex", sex);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    UPDATE_PROFILE_URL,
                    payload,
                    response -> {
                        boolean ok = response.optBoolean("success", false);
                        String message = response.optString("message", "Profile updated successfully.");
                        Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_LONG).show();
                        if (ok) {
                            // Keep the saved values even if the server omits the user object
                            profileFullName = fullName;
                            profileEmail = email;
                            profilePhone = phone;
                            profileAddress = address;
                            profileOccupation = occupation;
                            profileSex = sex;
                            JSONObject user = response.optJSONObject("user");
                            if (user != null) {
                                applyUserObject(user);
                            } else {
                                renderCard();
                            }
                            dialog.dismiss();
                        } else {
                            btnSave.setEnabled(true);
                            btnSave.setText("Save Changes");
                        }
                    },
                    error -> {
                        btnSave.setEnabled(true);
                        btnSave.setText("Save Changes");
                        if (ApiSession.handleAuthError(ProfileActivity.this, error)) {
                            dialog.dismiss();
                            return;
                        }
                        Toast.makeText(ProfileActivity.this, serverMessage(error, "Failed to save changes."),
                                Toast.LENGTH_LONG).show();
                    }
            );
            ApiSession.getRequestQueue(ProfileActivity.this).add(request);
        });

        dialog.show();

        // Present the editor as a full-width modal instead of a tiny popup
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private boolean isValidPhone(String phone) {
        if (phone.isEmpty()) {
            return true;
        }
        int digits = phone.replaceAll("\\D", "").length();
        return digits >= 7 && digits <= 15;
    }

    private String serverMessage(com.android.volley.VolleyError error, String fallback) {
        if (error.networkResponse != null && error.networkResponse.data != null) {
            try {
                JSONObject body = new JSONObject(new String(error.networkResponse.data));
                return body.optString("message", fallback);
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private String displayRole(String role) {
        if (role == null || role.isEmpty()) {
            return "—";
        }
        switch (role) {
            case "super_admin":
                return "Super Administrator";
            case "store_owner":
                return "Store Owner";
            case "store_admin":
                return "Store Admin";
            case "manager":
                return "Manager";
            case "inventory_checker":
                return "Inventory Staff";
            case "staff":
                return "Staff – Cashier";
            default:
                return role.replace('_', ' ');
        }
    }

    private static String avatarInitial(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "?";
        }
        return fullName.trim().substring(0, 1).toUpperCase();
    }

    private static String fallback(String value, String placeholder) {
        return (value == null || value.trim().isEmpty()) ? placeholder : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
