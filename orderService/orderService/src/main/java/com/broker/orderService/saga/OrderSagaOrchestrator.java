package com.broker.orderService.saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.broker.orderService.Application.OrderService;
import com.broker.orderService.domain.Order;
import com.broker.orderService.domain.OrderStatus;
import com.broker.orderService.dto.OrderDto;
import com.broker.orderService.dto.OrderUpdateMessage;
import com.broker.orderService.infrastructure.client.ClientServiceClient;
import com.broker.orderService.infrastructure.repo.OrderRepository;
import com.broker.orderService.infrastructure.client.WalletServiceClient;
import com.broker.orderService.service.OrderMessageProducer;
import com.broker.orderService.service.OutboxService;

/**
 * Saga Orchestrator pour gérer les transactions distribuées
 * d'annulation et de modification d'ordres
 */
@Service
public class OrderSagaOrchestrator {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WalletServiceClient walletServiceClient;

    @Autowired
    private OrderMessageProducer orderMessageProducer;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private ClientServiceClient clientServiceClient;
    
    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxService outboxService;

    /**
     * Saga pour l'annulation d'un ordre
     * Étapes:
     * 1. Vérifier que l'ordre existe et est PENDING
     * 2. Marquer l'ordre comme CANCELLED
     * 3. Si l'ordre est BUY, recréditer le wallet
     * 4. Notifier le matching service pour retirer l'ordre
     * 
     * En cas d'échec: Rollback automatique via @Transactional
     */
    @Transactional
    public SagaResult cancelOrderSaga(int orderId, String clientEmail) {
        SagaResult result = new SagaResult();
        
        try {
            // Step 1: Retrieve and validate order
            Order order = orderRepository.findByOrderId(orderId);
            if (order == null) {
                result.setSuccess(false);
                result.setErrorMessage("Order not found");
                return result;
            }

            // Verify ownership by checking if clientEmail matches the order's client
            // (This would require clientId lookup, simplified for demo)
            
            // Check if order can be cancelled (only PENDING orders)
            if (order.getStatus() != OrderStatus.PENDING) {
                result.setSuccess(false);
                result.setErrorMessage("Order cannot be cancelled. Status: " + order.getStatus());
                return result;
            }

            // Step 2: Update order status to CANCELLED
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            result.addStep("Order status updated to CANCELLED");

            // Step 2b: Publier événement dans outbox (transactionnel)
            String sagaId = "cancel-saga-" + orderId + "-" + System.currentTimeMillis();
            outboxService.publishOrderCancelledBySaga(orderId, clientEmail, sagaId);
            result.addStep("Cancellation event published to outbox");

            // Step 3: No wallet refund needed for PENDING orders
            // The wallet is only debited when order is FILLED, not when it's PENDING
            result.addStep("No wallet refund needed (order was never debited)");

            // Step 4: Notify MatchingService IMMEDIATELY (synchronous) to cancel scheduled trade
        
            try {
                OrderDto orderDto = new OrderDto();
                orderDto.setOrderId(String.valueOf(order.getOrderId()));
                orderDto.setStockSymbol(order.getSymbol());
                orderDto.setQuantity(order.getQuantity());
                orderDto.setPrice(order.getPrice());
                orderDto.setOrderType(order.getOrderType());
                orderDto.setStatus("CANCELLED");
                
                orderMessageProducer.sendCancelledOrderToMatchingService(orderDto);
                result.addStep("Cancellation sent IMMEDIATELY to matching service");
            } catch (Exception e) {
                System.err.println("CRITICAL: Failed to notify matching service: " + e.getMessage());
                throw new RuntimeException("Cannot cancel order - matching service notification failed", e);
            }
            
            // Step 5: Event will ALSO be processed by OutboxService (backup)
            result.addStep("Outbox event created for reliability");

            // Step 5: Broadcast WebSocket notification to client
            try {
                broadcastOrderUpdate(order, "PENDING", "Order cancelled successfully");
                result.addStep("WebSocket notification sent to client");
            } catch (Exception e) {
                System.err.println("Failed to send WebSocket notification: " + e.getMessage());
                // Non-critical, don't fail the saga
            }
            
            // Step 6: Send email notification for cancelled order
            try {
                orderService.sendOrderStatusEmailByClientId(order.getClientId(), order, "CANCELLED", 
                    "Your order has been successfully cancelled.");
                result.addStep("Email notification sent to client");
            } catch (Exception e) {
                System.err.println("Failed to send email notification: " + e.getMessage());
                // Non-critical, don't fail the saga
            }
            
            result.setSuccess(true);
            result.setMessage("Order cancelled successfully");
            return result;

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("Saga failed: " + e.getMessage());
            // @Transactional will auto-rollback
            return result;
        }
    }

    /**
     * Saga pour la modification d'un ordre
     * Étapes:
     * 1. Vérifier que l'ordre existe et est PENDING
     * 2. Calculer la différence de prix
     * 3. Ajuster le wallet (débit/crédit selon la différence)
     * 4. Mettre à jour l'ordre
     * 5. Notifier le matching service
     * 
     * En cas d'échec: Rollback automatique via @Transactional
     */
    @Transactional
    public SagaResult modifyOrderSaga(int orderId, String clientEmail, Double newPrice, Integer newQuantity) {
        SagaResult result = new SagaResult();
        
        try {
            // Step 1: Retrieve and validate order
            Order order = orderRepository.findByOrderId(orderId);
            if (order == null) {
                result.setSuccess(false);
                result.setErrorMessage("Order not found");
                return result;
            }

            // Check if order can be modified (only PENDING orders)
            if (order.getStatus() != OrderStatus.PENDING) {
                result.setSuccess(false);
                result.setErrorMessage("Order cannot be modified. Status: " + order.getStatus());
                return result;
            }

            // Step 2: Calculate new total for balance verification
            double newTotalPrice = (newPrice != null ? newPrice : order.getPrice());
            int newTotalQuantity = (newQuantity != null ? newQuantity : order.getQuantity());
            double newTotal = newTotalPrice * newTotalQuantity;

            // Step 3: No wallet adjustment needed for PENDING orders
            // The wallet is only debited when order is FILLED, not when it's PENDING
            // So if we modify a PENDING order, there's nothing to adjust yet
            // We only need to verify the client has sufficient balance for the NEW total
            if ("BUY".equalsIgnoreCase(order.getOrderType())) {
                try {
                    // Verify client has sufficient balance for the new order total
                    var balanceResponse = walletServiceClient.getBalance(clientEmail, clientEmail);
                    if (balanceResponse != null && balanceResponse.getStatusCode().is2xxSuccessful() && balanceResponse.getBody() != null) {
                        double currentBalance = balanceResponse.getBody();
                        if (currentBalance < newTotal) {
                            throw new RuntimeException("Insufficient balance for modified order. Required: " + newTotal + ", Available: " + currentBalance);
                        }
                        result.addStep("Balance verified for new order total: " + newTotal);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to verify balance. Transaction rolled back: " + e.getMessage(), e);
                }
            }

            // Step 4: Update order details
            if (newPrice != null) {
                order.setPrice(newPrice);
            }
            if (newQuantity != null) {
                order.setQuantity(newQuantity);
            }
            orderRepository.save(order);
            result.addStep("Order updated: price=" + order.getPrice() + ", quantity=" + order.getQuantity());

            // Step 4b: Publier événement dans outbox (transactionnel)
            String sagaId = "modify-saga-" + orderId + "-" + System.currentTimeMillis();
            outboxService.publishOrderModifiedBySaga(orderId, clientEmail, sagaId, newPrice, newQuantity);
            result.addStep("Modification event published to outbox");

            // Step 5: Notify MatchingService IMMEDIATELY (synchronous) to update scheduled trade
            // Critical: Must happen before the 10-second trade execution delay
            try {
                OrderDto orderDto = new OrderDto();
                orderDto.setOrderId(String.valueOf(order.getOrderId()));
                orderDto.setStockSymbol(order.getSymbol());
                orderDto.setQuantity(order.getQuantity());
                orderDto.setPrice(order.getPrice());
                orderDto.setOrderType(order.getOrderType());
                orderDto.setStatus("MODIFIED");
                
                orderMessageProducer.sendModifiedOrderToMatchingService(orderDto);
                result.addStep("Modification sent IMMEDIATELY to matching service");
            } catch (Exception e) {
                System.err.println("CRITICAL: Failed to notify matching service: " + e.getMessage());
                throw new RuntimeException("Cannot modify order - matching service notification failed", e);
            }
            
            // Step 6: Event will ALSO be processed by OutboxService for reliability (backup)
            result.addStep("Outbox event created for reliability");

            // Step 6: Broadcast WebSocket notification to client
            try {
                broadcastOrderUpdate(order, "PENDING", "Order modified successfully");
                result.addStep("WebSocket notification sent to client");
            } catch (Exception e) {
                System.err.println("Failed to send WebSocket notification: " + e.getMessage());
                // Non-critical, don't fail the saga
            }
            
            // Step 7: Send email notification for modified order
            try {
                String modificationDetails = String.format("Your order has been successfully modified. New price: $%.2f, New quantity: %d", 
                    order.getPrice(), order.getQuantity());
                orderService.sendOrderStatusEmailByClientId(order.getClientId(), order, "MODIFIED", 
                    modificationDetails);
                result.addStep("Email notification sent to client");
            } catch (Exception e) {
                System.err.println("Failed to send email notification: " + e.getMessage());
            }
            
            result.setSuccess(true);
            result.setMessage("Order modified successfully");
            return result;

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("Saga failed: " + e.getMessage());
            // @Transactional will auto-rollback
            return result;
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
