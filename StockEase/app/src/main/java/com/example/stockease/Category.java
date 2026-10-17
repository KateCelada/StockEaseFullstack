package com.example.stockease;

public class Category {
    private int id;
    private String name;
    private int productCount;

    public Category(int id, String name, int productCount) {
        this.id = id;
        this.name = name;
        this.productCount = productCount;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getProductCount() { return productCount; }

    // Required for Spinner displaying clean text
    @Override
    public String toString() {
        return name;
    }
}