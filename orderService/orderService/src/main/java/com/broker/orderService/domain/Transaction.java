package com.broker.orderService.domain;
import jakarta.persistence.*;


@Entity
public class Transaction {
    

  @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int transactionId;
    
    @Column(nullable = true)  // Optionnel pour les dépôts/retraits
    private Integer orderId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;
    
    @Column(nullable = false)
    private double amount;
    
    @Column(nullable = false)
    private int portefeuilleId;
    
    @Column(nullable = false)
    private java.time.LocalDateTime dateTransaction = java.time.LocalDateTime.now();
    
    @Column(length = 500)
    private String description;

    // Constructeur par défaut pour JPA
    public Transaction() {}

    // Constructeur pour les ordres
    public Transaction(int orderId, TransactionType type, double amount, int portefeuilleId, String description) {
        this.orderId = orderId;
        this.type = type;
        this.amount = amount;
        this.portefeuilleId = portefeuilleId;
        this.description = description;
    }
    
    // Constructeur pour dépôts/retraits (sans orderId)
    public Transaction(TransactionType type, double amount, int portefeuilleId, String description) {
        this.orderId = null;  // Pas d'ordre pour dépôts/retraits
        this.type = type;
        this.amount = amount;
        this.portefeuilleId = portefeuilleId;
        this.description = description;
    }

    // Getters et Setters
    public int getTransactionId() {
        return transactionId;
    }

    public Integer getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public java.time.LocalDateTime getDateTransaction() {
        return dateTransaction;
    }

    public void setDateTransaction(java.time.LocalDateTime dateTransaction) {
        this.dateTransaction = dateTransaction;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


}
