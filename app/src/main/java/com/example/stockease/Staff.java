package com.example.stockease;

public class Staff {
    private int id;
    private String fullName;
    private String username;
    private String email;
    private boolean active;

    public Staff(int id, String fullName, String username, String email, boolean active) {
        this.id = id;
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.active = active;
    }

    public int getId() { return id; }
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
}
