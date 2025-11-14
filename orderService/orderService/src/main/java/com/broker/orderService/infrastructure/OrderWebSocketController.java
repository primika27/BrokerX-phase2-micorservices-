package com.broker.orderService.infrastructure;

import com.broker.orderService.dto.OrderUpdateMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class OrderWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Client subscribes to order updates
     * Subscription destination: /user/queue/order-updates
     */
    @MessageMapping("/orders/subscribe")
    public void subscribeToOrderUpdates(SimpMessageHeaderAccessor headerAccessor) {
        String userEmail = extractUserEmailFromHeaders(headerAccessor);
        if (userEmail == null) {
            System.err.println("WebSocket authentication failed - missing user email");
            return;
        }
        
        System.out.println("User " + userEmail + " subscribed to order updates");
        
        // Send confirmation
        OrderUpdateMessage confirmation = new OrderUpdateMessage();
        confirmation.setMessage("Subscribed to order updates successfully");
        messagingTemplate.convertAndSendToUser(userEmail, "/queue/order-updates", confirmation);
    }

    /**
     * Broadcast order update to specific user
     */
    public void sendOrderUpdate(String userEmail, OrderUpdateMessage update) {
        System.out.println("Broadcasting order update to user: " + userEmail + 
                         " - Order #" + update.getOrderId() + " status: " + update.getStatus());
        messagingTemplate.convertAndSendToUser(userEmail, "/queue/order-updates", update);
    }

    /**
     * Extract user email from WebSocket headers (set by Gateway JWT filter)
     */
    private String extractUserEmailFromHeaders(SimpMessageHeaderAccessor headerAccessor) {
        // Try to get email from native headers (passed through by Gateway)
        String userEmail = headerAccessor.getFirstNativeHeader("X-Authenticated-User");
        
        if (userEmail == null || userEmail.isEmpty()) {
            System.err.println("No X-Authenticated-User header found in WebSocket connection");
            return null;
        }
        
        return userEmail;
    }
}
