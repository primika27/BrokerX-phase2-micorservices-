package com.broker.orderService.domain;
import jakarta.persistence.*;


@Entity
@Table(name = "\"order\"")
public class Order {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    int orderId;
    int clientId;
    String symbol;
    double price;
    int quantity;
    int status; 
    private String orderType;

    public Order() {}

    public Order(int orderId, String symbol, double price, int quantity, int status) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.status = status;
    }

    public String getOrderType() {
        return orderType;
    }
    
    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    // Getters and setters
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

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }


    
}
