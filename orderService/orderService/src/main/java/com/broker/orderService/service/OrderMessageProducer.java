package com.broker.orderService.service;

import com.broker.orderService.config.RabbitMQConfig;
import com.broker.orderService.dto.OrderDto;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderMessageProducer {

    private final RabbitTemplate rabbitTemplate;

    public OrderMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendNewOrderToMatchingService(OrderDto order) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_QUEUE, order);
        System.out.println("OrderService sent new order to matchingService: " + order);
    }
}