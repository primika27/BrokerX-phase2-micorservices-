package com.broker.orderService.service;

import com.broker.orderService.config.RabbitMQConfig;
import com.broker.orderService.dto.Trade;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import com.broker.orderService.domain.Order;
import com.broker.orderService.domain.OrderStatus;
import com.broker.orderService.infrastructure.repo.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class MatchingConsumer {

    private final OrderRepository orderRepository;

    public MatchingConsumer(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.MATCHING_QUEUE)
    public void receiveTrade(Trade trade) {
        System.out.println("Received trade from RabbitMQ: " + trade);

        // Find the buy and sell orders
        Order buyOrder = orderRepository.findById(Integer.parseInt(trade.getBuyOrderId())).orElse(null);
        Order sellOrder = orderRepository.findById(Integer.parseInt(trade.getSellOrderId())).orElse(null);

        if (buyOrder != null) {
            buyOrder.setStatus(OrderStatus.FILLED);
            orderRepository.save(buyOrder);
            System.out.println("Updated buy order status to FILLED: " + buyOrder.getOrderId());
        }

        if (sellOrder != null) {
            sellOrder.setStatus(OrderStatus.FILLED);
            orderRepository.save(sellOrder);
            System.out.println("Updated sell order status to FILLED: " + sellOrder.getOrderId());
        }
    }
}
