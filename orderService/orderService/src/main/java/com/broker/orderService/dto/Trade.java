package com.broker.orderService.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Trade implements Serializable {
    private String tradeId;
    private String buyOrderId;
    private String sellOrderId;
    private String stockSymbol;
    private int quantity;
    private double price;
    private LocalDateTime timestamp;

    // Constructors
    public Trade() {
    }

    public Trade(String tradeId, String buyOrderId, String sellOrderId, String stockSymbol, int quantity, double price, LocalDateTime timestamp) {
        this.tradeId = tradeId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.stockSymbol = stockSymbol;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public String getBuyOrderId() {
        return buyOrderId;
    }

    public void setBuyOrderId(String buyOrderId) {
        this.buyOrderId = buyOrderId;
    }

    public String getSellOrderId() {
        return sellOrderId;
    }

    public void setSellOrderId(String sellOrderId) {
        this.sellOrderId = sellOrderId;
    }

    public String getStockSymbol() {
        return stockSymbol;
    }

    public void setStockSymbol(String stockSymbol) {
        this.stockSymbol = stockSymbol;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "Trade{"
               + "tradeId='" + tradeId + "'"
               + ", buyOrderId='" + buyOrderId + "'"
               + ", sellOrderId='" + sellOrderId + "'"
               + ", stockSymbol='" + stockSymbol + "'"
               + ", quantity=" + quantity
               + ", price=" + price
               + ", timestamp=" + timestamp
               + "}";
    }
}
