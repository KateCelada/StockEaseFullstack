package com.example.stockease;

public class Staff {
    private int id;
    private String fullName;
    private String username;
    private String email;
    private String role;
    private boolean active;

    public Staff(int id, String fullName, String username, String email, String role, boolean active) {
        this.id = id;
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.role = role;
        this.active = active;
    }

    public int getId() { return id; }
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getRoleDisplay() {
        if ("inventory_checker".equalsIgnoreCase(role)) return "Inventory Checker";
        if ("store_admin".equalsIgnoreCase(role)) return "Store Admin";
        if ("manager".equalsIgnoreCase(role)) return "Manager";
        return "Cashier";
    }
    public boolean isActive() { return active; }
}
