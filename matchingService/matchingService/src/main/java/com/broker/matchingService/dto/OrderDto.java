package com.broker.matchingService.dto;

import java.io.Serializable;

public class OrderDto implements Serializable {
    private String orderId;
    private String stockSymbol;
    private int quantity;
    private double price;
    private String orderType; // BUY or SELL

    public OrderDto() {
    }

    public OrderDto(String orderId, String stockSymbol, int quantity, double price, String orderType) {
        this.orderId = orderId;
        this.stockSymbol = stockSymbol;
        this.quantity = quantity;
        this.price = price;
        this.orderType = orderType;
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
        return "OrderDto{" +
               "orderId='" + orderId + "'" +
               ", stockSymbol='" + stockSymbol + "'" +
               ", quantity=" + quantity +
               ", price=" + price +
               ", orderType='" + orderType + "'" +
               '}';
    }
}
