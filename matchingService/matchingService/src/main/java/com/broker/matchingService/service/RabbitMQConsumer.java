package com.broker.matchingService.service;

import com.broker.matchingService.config.RabbitMQConfig;
import com.broker.matchingService.dto.Trade;
import com.broker.matchingService.dto.OrderDto;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class RabbitMQConsumer {

    private final OrderMatchingService orderMatchingService;

    public RabbitMQConsumer(OrderMatchingService orderMatchingService) {
        this.orderMatchingService = orderMatchingService;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
    public void receiveOrder(OrderDto order) {
        System.out.println("Received order from RabbitMQ: " + order);
        orderMatchingService.processNewOrder(order);
    }
}