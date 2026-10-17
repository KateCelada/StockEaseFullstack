package com.example.stockease;

public class Transaction {
    private int id;
    private double totalAmount;
    private String paymentType;
    private String transactionDate;
    private String userName;
    private String cashierUsername;
    private String cashierRole;
    private String itemsSummary;
    private String status;

    public Transaction(int id, double totalAmount, String paymentType, String transactionDate, String userName, String itemsSummary) {
        this(id, totalAmount, paymentType, transactionDate, userName, "", "staff", itemsSummary, "completed");
    }

    public Transaction(int id, double totalAmount, String paymentType, String transactionDate, String userName, String itemsSummary, String status) {
        this(id, totalAmount, paymentType, transactionDate, userName, "", "staff", itemsSummary, status);
    }

    public Transaction(int id, double totalAmount, String paymentType, String transactionDate, String userName,
                       String cashierUsername, String cashierRole, String itemsSummary, String status) {
        this.id = id;
        this.totalAmount = totalAmount;
        this.paymentType = paymentType;
        this.transactionDate = transactionDate;
        this.userName = userName;
        this.cashierUsername = cashierUsername != null ? cashierUsername : "";
        this.cashierRole = cashierRole != null ? cashierRole : "staff";
        this.itemsSummary = itemsSummary;
        this.status = (status != null && !status.isEmpty()) ? status : "completed";
    }

    public int getId() { return id; }
    public double getTotalAmount() { return totalAmount; }
    public String getPaymentType() { return paymentType; }
    public String getTransactionDate() { return transactionDate; }
    public String getUserName() { return userName; }
    public String getCashierUsername() { return cashierUsername; }
    public String getCashierRole() { return cashierRole; }
    public String getDutyShiftInfo() {
        return userName + (!cashierUsername.isEmpty() ? " (@" + cashierUsername + ")" : "");
    }
    public String getItemsSummary() { return itemsSummary; }
    public String getStatus() { return status; }
    public boolean isVoided() { return "voided".equalsIgnoreCase(status); }
}
