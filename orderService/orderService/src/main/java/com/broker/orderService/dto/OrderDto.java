package com.broker.orderService.dto;

import java.io.Serializable;

public class OrderDto implements Serializable {
    private String orderId;
    private String stockSymbol;
    private int quantity;
    private double price;
    private String orderType; // e.g., "BUY" or "SELL"

    // Getters and Setters
    public String getOrderDtoId() {
        return orderId;
    }

    public void setOrderDtoId(String orderId) {
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

    @Override
    public String toString() {
        return "Order{" +
               "orderId='" + orderId + "'" +
               ", stockSymbol='" + stockSymbol + "'" +
               ", quantity=" + quantity +
               ", price=" + price +
               ", orderType='" + orderType + "'" +
               '}';
    }
}
