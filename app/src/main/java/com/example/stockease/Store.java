package com.example.stockease;

public class Store {
    private int id;
    private String name;
    private String location;
    private String storeAdmin;
    private String status;

    public Store(int id, String name, String location, String storeAdmin, String status) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.storeAdmin = storeAdmin;
        this.status = status;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public String getStoreAdmin() { return storeAdmin; }
    public String getStatus() { return status; }
}