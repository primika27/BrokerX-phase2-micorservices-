package com.broker.orderService.service;

import com.broker.orderService.Application.OrderService;
import com.broker.orderService.config.RabbitMQConfig;
import com.broker.orderService.dto.OrderUpdateMessage;
import com.broker.orderService.dto.Trade;
import com.broker.orderService.infrastructure.client.ClientServiceClient;
import com.broker.orderService.infrastructure.client.WalletServiceClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import com.broker.orderService.domain.Order;
import com.broker.orderService.domain.OrderStatus;
import com.broker.orderService.infrastructure.repo.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchingConsumer {

    private final OrderRepository orderRepository;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private ClientServiceClient clientServiceClient;
    
    @Autowired
    private WalletServiceClient walletServiceClient;
    
    @Autowired
    private OrderService orderService;

    public MatchingConsumer(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.MATCHING_QUEUE)
    @Transactional
    public void receiveTrade(Trade trade) {
        System.out.println("Received trade from RabbitMQ: " + trade);

        // Find the buy and sell orders (skip if MARKET_MAKER synthetic order)
        Order buyOrder = null;
        Order sellOrder = null;
        
        // Only try to parse as Integer if it's not a MARKET_MAKER synthetic order
        if (!trade.getBuyOrderId().startsWith("MARKET_MAKER_")) {
            buyOrder = orderRepository.findById(Integer.parseInt(trade.getBuyOrderId())).orElse(null);
        }
        if (!trade.getSellOrderId().startsWith("MARKET_MAKER_")) {
            sellOrder = orderRepository.findById(Integer.parseInt(trade.getSellOrderId())).orElse(null);
        }

        // Process BUY order
        if (buyOrder != null) {
            // IMPORTANT: Refresh the order from DB to get the latest price (in case it was modified)
            Order updatedBuyOrder = orderRepository.findById(buyOrder.getOrderId()).orElse(buyOrder);
            String buyerEmail = getClientEmail(updatedBuyOrder.getClientId());
            if (buyerEmail != null) {
                // 1. Update order status to FILLED
                updatedBuyOrder.setStatus(OrderStatus.FILLED);
                orderRepository.save(updatedBuyOrder);
                System.out.println("Updated buy order status to FILLED: " + updatedBuyOrder.getOrderId());
                
                // 2. Debit buyer's wallet using the UPDATED ORDER price AND quantity
                // This ensures the user pays exactly what they agreed to pay after modification
                double totalCost = updatedBuyOrder.getPrice() * updatedBuyOrder.getQuantity();
                try {
                    walletServiceClient.walletTransaction(
                        buyerEmail, 
                        buyerEmail, 
                        totalCost, 
                        "DEBIT"
                    );
                    System.out.println("Debited buyer wallet: " + totalCost + " (updated order: " + updatedBuyOrder.getQuantity() + 
                                     " × " + updatedBuyOrder.getPrice() + "$, trade: " + trade.getQuantity() + " × " + trade.getPrice() + "$) for order #" + updatedBuyOrder.getOrderId());
                } catch (Exception e) {
                    System.err.println("Failed to debit buyer wallet: " + e.getMessage());
                    // Continue anyway - order is still filled
                }
                
                // 3. Send email notification for FILLED order
                orderService.sendOrderStatusEmail(buyerEmail, updatedBuyOrder, "FILLED", 
                    "Your buy order has been successfully matched and filled!");
                
                // 4. Broadcast WebSocket notification to client
                broadcastOrderUpdate(updatedBuyOrder, "PENDING", "Order matched and filled");
            }
        }

        // Process SELL order
        if (sellOrder != null) {
            String sellerEmail = getClientEmail(sellOrder.getClientId());
            if (sellerEmail != null) {
                // 1. Update order status to FILLED
                sellOrder.setStatus(OrderStatus.FILLED);
                orderRepository.save(sellOrder);
                System.out.println("Updated sell order status to FILLED: " + sellOrder.getOrderId());
                
                // 2. Credit seller's wallet
                double totalCredit = trade.getPrice() * trade.getQuantity();
                try {
                    walletServiceClient.walletTransaction(
                        sellerEmail, 
                        sellerEmail, 
                        totalCredit, 
                        "CREDIT"
                    );
                    System.out.println("Credited seller wallet: " + totalCredit + " for order #" + sellOrder.getOrderId());
                } catch (Exception e) {
                    System.err.println("Failed to credit seller wallet: " + e.getMessage());
                    // Continue anyway - order is still filled
                }
                
                // 3. Send email notification for FILLED order
                orderService.sendOrderStatusEmail(sellerEmail, sellOrder, "FILLED", 
                    "Your sell order has been successfully matched and filled!");
                
                // 4. Broadcast WebSocket notification to client
                broadcastOrderUpdate(sellOrder, "PENDING", "Order matched and filled");
            }
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
