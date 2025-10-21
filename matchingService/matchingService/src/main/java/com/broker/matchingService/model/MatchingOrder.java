package com.broker.matchingService.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class MatchingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String orderId; // Original order ID from orderService
    private String stockSymbol;
    private int quantity;
    private int remainingQuantity; // Quantity yet to be matched
    private double price;
    private String orderType; // BUY or SELL
    private LocalDateTime timestamp;
    private String status; // e.g., PENDING, PARTIALLY_FILLED, FILLED, CANCELLED

    // Constructors
    public MatchingOrder() {
    }

    public MatchingOrder(String orderId, String stockSymbol, int quantity, double price, String orderType) {
        this.orderId = orderId;
        this.stockSymbol = stockSymbol;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.price = price;
        this.orderType = orderType;
        this.timestamp = LocalDateTime.now();
        this.status = "PENDING";
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
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

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(int remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "MatchingOrder{" +
               "id=" + id +
               ", orderId='" + orderId + "'" +
               ", stockSymbol='" + stockSymbol + "'" +
               ", quantity=" + quantity +
               ", remainingQuantity=" + remainingQuantity +
               ", price=" + price +
               ", orderType='" + orderType + "'" +
               ", timestamp=" + timestamp +
               ", status='" + status + "'" +
               '}';
    }
}
