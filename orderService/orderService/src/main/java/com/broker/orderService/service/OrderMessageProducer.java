package com.broker.orderservice.service;

import com.broker.orderservice.config.RabbitMQConfig;
import com.broker.orderservice.dto.Order;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    public OrderMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendNewOrderToMatchingService(Order order) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_QUEUE, order);
        System.out.println("OrderService sent new order to matchingService: " + order);
    }
}