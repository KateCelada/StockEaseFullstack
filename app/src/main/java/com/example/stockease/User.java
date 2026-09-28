package com.example.stockease;

public class User {
    private final int id;
    private final String fullName;
    private final String username;
    private final String email;
    private String role;
    private final int storeId;
    private final String storeName;
    private final boolean active;
    private final String createdAt;

    public User(int id, String fullName, String username, String email, String role,
                int storeId, String storeName, boolean active, String createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.role = role;
        this.storeId = storeId;
        this.storeName = storeName;
        this.active = active;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public int getStoreId() { return storeId; }
    public String getStoreName() { return storeName; }
    public boolean isActive() { return active; }
    public String getCreatedAt() { return createdAt; }
    public void setRole(String role) { this.role = role; }
}
