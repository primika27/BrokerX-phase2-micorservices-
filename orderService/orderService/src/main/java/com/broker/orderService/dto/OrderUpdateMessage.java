package com.broker.orderService.dto;

import java.time.LocalDateTime;

public class OrderUpdateMessage {
    private int orderId;
    private String symbol;
    private String orderType;
    private int quantity;
    private double price;
    private String status;
    private String previousStatus;
    private LocalDateTime timestamp;
    private String message;

    public OrderUpdateMessage() {
    }

    public OrderUpdateMessage(int orderId, String symbol, String orderType, int quantity, 
                            double price, String status, String previousStatus, String message) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.orderType = orderType;
        this.quantity = quantity;
        this.price = price;
        this.status = status;
        this.previousStatus = previousStatus;
        this.timestamp = LocalDateTime.now();
        this.message = message;
    }

    // Getters and Setters
    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
