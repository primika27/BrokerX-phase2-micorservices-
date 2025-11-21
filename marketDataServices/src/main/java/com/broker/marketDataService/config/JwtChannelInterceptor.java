package com.broker.marketDataService.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Try to get token from WebSocket session attributes (set by handshake interceptor)
            String token = null;
            if (accessor.getSessionAttributes() != null) {
                token = (String) accessor.getSessionAttributes().get("jwt_token");
            }
            
            if (token != null && !token.isEmpty()) {
                // For now, extract email from token (simplified - in production, validate JWT properly)
                // This is a basic implementation - you should validate the JWT token here
                String userEmail = extractEmailFromToken(token);
                if (userEmail != null) {
                    accessor.setUser(new JwtPrincipal(userEmail));
                    System.out.println("MarketData WebSocket authenticated user: " + userEmail);
                } else {
                    throw new SecurityException("Invalid JWT token");
                }
            } else {
                // Fallback: check for X-Authenticated-User header from Gateway
                String authenticatedUser = accessor.getFirstNativeHeader("X-Authenticated-User");
                if (authenticatedUser != null && !authenticatedUser.isEmpty()) {
                    accessor.setUser(new JwtPrincipal(authenticatedUser));
                } else {
                    throw new SecurityException("Authentication required - JWT token missing");
                }
            }
        }
        
        return message;
    }
    
    /**
     * Extract email from JWT token (simplified implementation)
     * In production, use a proper JWT library to validate and parse the token
     */
    private String extractEmailFromToken(String token) {
        try {
            // This is a simplified extraction - in production, validate the JWT signature
            // For now, decode the payload and extract the subject (email)
            
            if (token != null && token.contains(".")) {
                // Split JWT token (header.payload.signature)
                String[] parts = token.split("\\.");
                if (parts.length == 3) {
                    // Decode payload (base64)
                    String payload = parts[1];
                    // Add padding if needed
                    while (payload.length() % 4 != 0) {
                        payload += "=";
                    }
                    
                    // Decode base64
                    byte[] decodedBytes = java.util.Base64.getDecoder().decode(payload);
                    String decodedPayload = new String(decodedBytes);
                    
                    // Extract subject (email) from JSON payload
                    // Look for "sub":"email@domain.com"
                    if (decodedPayload.contains("\"sub\":\"")) {
                        int startIndex = decodedPayload.indexOf("\"sub\":\"") + 7;
                        int endIndex = decodedPayload.indexOf("\"", startIndex);
                        if (endIndex > startIndex) {
                            String email = decodedPayload.substring(startIndex, endIndex);
                            System.out.println("Extracted email from JWT: " + email);
                            return email;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing JWT token: " + e.getMessage());
        }
        return null;
    }

    private static class JwtPrincipal implements Principal {
        private final String email;

        public JwtPrincipal(String email) {
            this.email = email;
        }

        @Override
        public String getName() {
            return email;
        }
    }
}