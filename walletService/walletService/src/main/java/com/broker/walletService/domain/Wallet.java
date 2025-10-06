package com.broker.walletService.domain;
import jakarta.persistence.*;


@Entity
public class Wallet {

 @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    int walletId;

    double balance=0.0;

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

}
