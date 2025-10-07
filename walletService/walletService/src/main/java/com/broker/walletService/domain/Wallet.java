package com.broker.walletService.domain;
import jakarta.persistence.*;


@Entity
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long walletId;

    @Column(unique = true, nullable = false)
    private String ownerEmail; // Email de l'utilisateur (depuis JWT)

    @Column(nullable = false)
    private Double balance = 0.0;

    public Wallet() {}

    public Wallet(String ownerEmail) {
        this.ownerEmail = ownerEmail;
        this.balance = 0.0;
    }

    // Getters and setters
    public Long getWalletId() {
        return walletId;
    }

    public void setWalletId(Long walletId) {
        this.walletId = walletId;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public Double getBalance() {
        return balance;
    }

    public void setBalance(Double balance) {
        this.balance = balance;
    }
}
