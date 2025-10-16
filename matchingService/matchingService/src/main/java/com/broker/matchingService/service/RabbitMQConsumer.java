package com.broker.matchingservice.service;

import com.broker.matchingservice.config.RabbitMQConfig;
import com.broker.matchingservice.dto.Order;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class RabbitMQConsumer {

    private final OrderMatchingService orderMatchingService;

    public RabbitMQConsumer(OrderMatchingService orderMatchingService) {
        this.orderMatchingService = orderMatchingService;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
    public void receiveOrder(Order order) {
        System.out.println("Received order from RabbitMQ: " + order);
        orderMatchingService.processNewOrder(order);
    }
}