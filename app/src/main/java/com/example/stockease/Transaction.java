package com.example.stockease;

public class Transaction {
    private int id;
    private double totalAmount;
    private String paymentType;
    private String transactionDate;
    private String userName;
    private String itemsSummary;

    public Transaction(int id, double totalAmount, String paymentType, String transactionDate, String userName, String itemsSummary) {
        this.id = id;
        this.totalAmount = totalAmount;
        this.paymentType = paymentType;
        this.transactionDate = transactionDate;
        this.userName = userName;
        this.itemsSummary = itemsSummary;
    }

    public int getId() { return id; }
    public double getTotalAmount() { return totalAmount; }
    public String getPaymentType() { return paymentType; }
    public String getTransactionDate() { return transactionDate; }
    public String getUserName() { return userName; }
    public String getItemsSummary() { return itemsSummary; }
}
