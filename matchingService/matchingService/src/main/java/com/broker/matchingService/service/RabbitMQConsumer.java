package com.broker.matchingservice.service;

import com.broker.matchingservice.config.RabbitMQConfig;
import com.broker.matchingservice.dto.Order;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class RabbitMQConsumer {

    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
    public void receiveOrder(Order order) {
        System.out.println("Received order: " + order);
 
        if(order.getOrderType() == null || (!order.getOrderType().equals("BUY") && !order.getOrderType().equals("SELL"))) {
            System.err.println("Invalid order type: " + order.getOrderType());
            return;
        }
        else if(order.getQuantity() <= 0) {
            System.err.println("Invalid order quantity: " + order.getQuantity());
            return;
        }
        else if(order.getPrice() <= 0) {
            System.err.println("Invalid order price: " + order.getPrice());
            return;
        }
        sendMatchedOrder(order);
    }

    public void sendMatchedOrder(Order order) {
        // This would typically be handled by a separate producer or a service
        // that publishes matched orders. For simplicity, we're doing it here.
        // In a real system, you might have a dedicated exchange for matched orders.
        System.out.println("Simulating sending matched order: " + order + " to " + RabbitMQConfig.MATCHING_QUEUE);
        // rabbitTemplate.convertAndSend(RabbitMQConfig.MATCHING_QUEUE, order);
    }
}