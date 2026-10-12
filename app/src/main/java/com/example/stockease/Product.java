package com.example.stockease;

public class Product {
    private int id;
    private int storeId;
    private int categoryId;
    private String categoryName;
    private String name;
    private double price;
    private double costPrice;
    private int stock;
    private int totalSoldUnits;
    private double progressiveGross;
    private double progressiveNet;

    // Full Constructor with Progressive Earnings (Used in ProductsActivity)
    public Product(int id, int storeId, int categoryId, String categoryName, String name, double price, double costPrice, int stock, int totalSoldUnits, double progressiveGross, double progressiveNet) {
        this.id = id;
        this.storeId = storeId;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.name = name;
        this.price = price;
        this.costPrice = costPrice;
        this.stock = stock;
        this.totalSoldUnits = totalSoldUnits;
        this.progressiveGross = progressiveGross;
        this.progressiveNet = progressiveNet;
    }

    // Constructor without Progressive Earnings
    public Product(int id, int storeId, int categoryId, String categoryName, String name, double price, double costPrice, int stock) {
        this(id, storeId, categoryId, categoryName, name, price, costPrice, stock, 0, 0.0, 0.0);
    }

    // Legacy Full Constructor
    public Product(int id, int storeId, int categoryId, String categoryName, String name, double price, int stock) {
        this(id, storeId, categoryId, categoryName, name, price, 0.0, stock, 0, 0.0, 0.0);
    }

    // Simplified Constructor (Used in POSActivity)
    public Product(int id, String name, String categoryName, double price, int stock) {
        this(id, 0, 0, categoryName, name, price, 0.0, stock, 0, 0.0, 0.0);
    }

    // --- Getters ---
    public int getId() { return id; }
    public int getStoreId() { return storeId; }
    public int getCategoryId() { return categoryId; }
    public String getCategoryName() { return categoryName; }
    public String getCategory() { return categoryName; } // Alias for POSActivity
    public String getName() { return name; }
    public double getPrice() { return price; }
    public double getCostPrice() { return costPrice; }
    public int getStock() { return stock; }
    public double getGrossProfit() { return price - costPrice; }
    public int getTotalSoldUnits() { return totalSoldUnits; }
    public double getProgressiveGross() { return progressiveGross; }
    public double getProgressiveNet() { return progressiveNet; }
}
