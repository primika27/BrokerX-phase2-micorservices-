package com.broker.marketDataService.dto;

import java.time.LocalDateTime;
import java.util.List;

public class OrderBook {
    private String symbol;
    private List<BookLevel> bids;
    private List<BookLevel> asks;
    private LocalDateTime timestamp;

    public OrderBook() {
        this.timestamp = LocalDateTime.now();
    }

    public OrderBook(String symbol, List<BookLevel> bids, List<BookLevel> asks) {
        this.symbol = symbol;
        this.bids = bids;
        this.asks = asks;
        this.timestamp = LocalDateTime.now();
    }

    // Getters et Setters
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public List<BookLevel> getBids() {
        return bids;
    }

    public void setBids(List<BookLevel> bids) {
        this.bids = bids;
    }

    public List<BookLevel> getAsks() {
        return asks;
    }

    public void setAsks(List<BookLevel> asks) {
        this.asks = asks;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    // Classe interne pour représenter un niveau du carnet d'ordres
    public static class BookLevel {
        private double price;
        private int quantity;

        public BookLevel() {}

        public BookLevel(double price, int quantity) {
            this.price = price;
            this.quantity = quantity;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}