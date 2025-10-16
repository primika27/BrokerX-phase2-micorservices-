package com.broker.matchingservice.controller;

import com.broker.matchingservice.dto.Order;
import com.broker.matchingservice.service.RabbitMQProducer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final RabbitMQProducer rabbitMQProducer;

    public OrderController(RabbitMQProducer rabbitMQProducer) {
        this.rabbitMQProducer = rabbitMQProducer;
    }

    @PostMapping("/send")
    public String sendOrder(@RequestBody Order order) {
        rabbitMQProducer.sendOrder(order);
        return "Order sent to RabbitMQ: " + order.getOrderId();
    }
}