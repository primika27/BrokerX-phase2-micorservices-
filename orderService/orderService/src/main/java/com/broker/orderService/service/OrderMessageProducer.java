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

    /**
     Send cancelled order to MatchingService so it can remove it from the OrderBook
     */
    public void sendCancelledOrderToMatchingService(OrderDto order) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_QUEUE, order);
        System.out.println("OrderService sent cancelled order to matchingService: " + order);
    }

    /**
      Send modified order to MatchingService so it can update the OrderBook
     */
    public void sendModifiedOrderToMatchingService(OrderDto order) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_QUEUE, order);
        System.out.println("OrderService sent modified order to matchingService: " + order);
    }
}