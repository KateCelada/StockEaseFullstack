package com.example.stockease;

public class CartItem {
    private Product product;
    private int quantity;

    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    // Explicit method called by POSActivity
    public double getTotalPrice() {
        return product != null ? product.getPrice() * quantity : 0.0;
    }

    // Alias in case CartAdapter uses getSubtotal()
    public double getSubtotal() {
        return getTotalPrice();
    }
}