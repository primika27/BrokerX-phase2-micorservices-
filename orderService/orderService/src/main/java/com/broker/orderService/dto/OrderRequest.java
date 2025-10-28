package com.broker.orderService.dto;

public class OrderRequest {
    private String symbol;
    private Double price;
    private Integer quantity;
    private String orderType;

    public OrderRequest() {}

    public OrderRequest(String symbol, Double price, Integer quantity, String orderType) {
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.orderType = orderType;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }
}