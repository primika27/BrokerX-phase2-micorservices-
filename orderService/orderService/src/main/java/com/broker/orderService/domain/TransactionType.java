package com.broker.orderService.domain;

public enum TransactionType {

    DEPOSIT("Dépôt"),
    WITHDRAWAL("Retrait"), 
    ORDER("Ordre de trading");
    
    private final String description;
    
    TransactionType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
}
