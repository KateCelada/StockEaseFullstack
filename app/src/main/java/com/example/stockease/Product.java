package com.example.stockease;

public class Product {
    private int id;
    private int storeId;
    private int categoryId;
    private String categoryName;
    private String name;
    private double price;
    private int stock;

    // Full Constructor (Used in ProductsActivity & InventoryActivity)
    public Product(int id, int storeId, int categoryId, String categoryName, String name, double price, int stock) {
        this.id = id;
        this.storeId = storeId;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    // Simplified Constructor (Used in POSActivity)
    public Product(int id, String name, String categoryName, double price, int stock) {
        this.id = id;
        this.name = name;
        this.categoryName = categoryName;
        this.price = price;
        this.stock = stock;
        this.storeId = 0;
        this.categoryId = 0;
    }

    // --- Getters ---
    public int getId() { return id; }
    public int getStoreId() { return storeId; }
    public int getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public String getCategory() { return categoryName; } // Alias for POSActivity
    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getStock() { return stock; }
}
