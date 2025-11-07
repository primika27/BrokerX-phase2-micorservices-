package com.broker.marketDataService.dto;

public class SubscriptionRequest {
    private String[] symbols;
    private String subscriptionType; // "quotes", "orderbook", "trades"

    public SubscriptionRequest() {}

    public SubscriptionRequest(String[] symbols, String subscriptionType) {
        this.symbols = symbols;
        this.subscriptionType = subscriptionType;
    }

    public String[] getSymbols() {
        return symbols;
    }

    public void setSymbols(String[] symbols) {
        this.symbols = symbols;
    }

    public String getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(String subscriptionType) {
        this.subscriptionType = subscriptionType;
    }
}