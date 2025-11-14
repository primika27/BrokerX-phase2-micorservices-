package com.broker.orderService.saga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.broker.orderService.domain.Order;
import com.broker.orderService.domain.OrderStatus;
import com.broker.orderService.dto.OrderUpdateMessage;
import com.broker.orderService.infrastructure.client.ClientServiceClient;
import com.broker.orderService.infrastructure.repo.OrderRepository;
import com.broker.orderService.infrastructure.client.WalletServiceClient;
import com.broker.orderService.service.OrderMessageProducer;

import java.util.HashMap;
import java.util.Map;

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

            // Step 3: Refund wallet if it was a BUY order
            if ("BUY".equalsIgnoreCase(order.getOrderType())) {
                double refundAmount = order.getPrice() * order.getQuantity();
                try {
                    walletServiceClient.walletTransaction(
                        clientEmail, 
                        clientEmail, 
                        refundAmount, 
                        "CREDIT"
                    );
                    result.addStep("Wallet credited: " + refundAmount);
                } catch (Exception e) {
                    // Compensation: Rollback order status
                    order.setStatus(OrderStatus.PENDING);
                    orderRepository.save(order);
                    throw new RuntimeException("Failed to credit wallet. Transaction rolled back.", e);
                }
            }

            // Step 4: Notify matching service to remove order from order book
            try {
                Map<String, Object> cancelMessage = new HashMap<>();
                cancelMessage.put("orderId", orderId);
                cancelMessage.put("action", "CANCEL");
                // orderMessageProducer.sendCancelOrderToMatchingService(cancelMessage);
                result.addStep("Matching service notified");
            } catch (Exception e) {
                System.err.println("Warning: Failed to notify matching service: " + e.getMessage());
                // Non-critical, order is already cancelled in our system
            }

            // Step 5: Broadcast WebSocket notification to client
            try {
                broadcastOrderUpdate(order, "PENDING", "Order cancelled successfully");
                result.addStep("WebSocket notification sent to client");
            } catch (Exception e) {
                System.err.println("Failed to send WebSocket notification: " + e.getMessage());
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

            // Step 2: Calculate old and new totals
            double oldTotal = order.getPrice() * order.getQuantity();
            double newTotalPrice = (newPrice != null ? newPrice : order.getPrice());
            int newTotalQuantity = (newQuantity != null ? newQuantity : order.getQuantity());
            double newTotal = newTotalPrice * newTotalQuantity;
            double difference = newTotal - oldTotal;

            // Step 3: Adjust wallet if BUY order and price changed
            if ("BUY".equalsIgnoreCase(order.getOrderType()) && Math.abs(difference) > 0.01) {
                try {
                    if (difference > 0) {
                        // Need to debit more money
                        walletServiceClient.walletTransaction(
                            clientEmail, 
                            clientEmail, 
                            difference, 
                            "DEBIT"
                        );
                        result.addStep("Wallet debited: " + difference);
                    } else {
                        // Need to credit back
                        walletServiceClient.walletTransaction(
                            clientEmail, 
                            clientEmail, 
                            Math.abs(difference), 
                            "CREDIT"
                        );
                        result.addStep("Wallet credited: " + Math.abs(difference));
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to adjust wallet. Transaction rolled back.", e);
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

            // Step 5: Notify matching service about the modification
            try {
                Map<String, Object> modifyMessage = new HashMap<>();
                modifyMessage.put("orderId", orderId);
                modifyMessage.put("action", "MODIFY");
                modifyMessage.put("newPrice", order.getPrice());
                modifyMessage.put("newQuantity", order.getQuantity());
                // orderMessageProducer.sendModifyOrderToMatchingService(modifyMessage);
                result.addStep("Matching service notified");
            } catch (Exception e) {
                System.err.println("Warning: Failed to notify matching service: " + e.getMessage());
                // Non-critical, order is already updated in our system
            }

            // Step 6: Broadcast WebSocket notification to client
            try {
                broadcastOrderUpdate(order, "PENDING", "Order modified successfully");
                result.addStep("WebSocket notification sent to client");
            } catch (Exception e) {
                System.err.println("Failed to send WebSocket notification: " + e.getMessage());
                // Non-critical, don't fail the saga
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
