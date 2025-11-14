package com.broker.orderService.service;

import com.broker.orderService.config.RabbitMQConfig;
import com.broker.orderService.dto.OrderUpdateMessage;
import com.broker.orderService.dto.Trade;
import com.broker.orderService.infrastructure.client.ClientServiceClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import com.broker.orderService.domain.Order;
import com.broker.orderService.domain.OrderStatus;
import com.broker.orderService.infrastructure.repo.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class MatchingConsumer {

    private final OrderRepository orderRepository;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private ClientServiceClient clientServiceClient;

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
            
            // Broadcast WebSocket notification to client
            broadcastOrderUpdate(buyOrder, "PENDING", "Order matched and filled");
        }

        if (sellOrder != null) {
            sellOrder.setStatus(OrderStatus.FILLED);
            orderRepository.save(sellOrder);
            System.out.println("Updated sell order status to FILLED: " + sellOrder.getOrderId());
            
            // Broadcast WebSocket notification to client
            broadcastOrderUpdate(sellOrder, "PENDING", "Order matched and filled");
        }
    }
    
    /**
     * Broadcast order status change via WebSocket to the client
     */
    private void broadcastOrderUpdate(Order order, String previousStatus, String message) {
        try {
            // Get client email from clientId
            String clientEmail = getClientEmail(order.getClientId());
            if (clientEmail == null) {
                System.err.println("Could not find client email for clientId: " + order.getClientId());
                return;
            }
            
            OrderUpdateMessage update = new OrderUpdateMessage(
                order.getOrderId(),
                order.getSymbol(),
                order.getOrderType(),
                order.getQuantity(),
                order.getPrice(),
                order.getStatus().name(),
                previousStatus,
                message
            );
            
            messagingTemplate.convertAndSendToUser(
                clientEmail, 
                "/queue/order-updates", 
                update
            );
            
            System.out.println("WebSocket notification sent to " + clientEmail + 
                             " for order #" + order.getOrderId());
        } catch (Exception e) {
            System.err.println("Failed to broadcast order update via WebSocket: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get client email by clientId via ClientService
     */
    private String getClientEmail(int clientId) {
        try {
            var response = clientServiceClient.getEmailById("orderService", clientId);
            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            System.err.println("Error fetching client email: " + e.getMessage());
        }
        return null;
    }
}
