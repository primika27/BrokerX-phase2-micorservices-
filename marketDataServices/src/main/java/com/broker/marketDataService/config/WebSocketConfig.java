package com.broker.marketDataService.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // setup routing + in memory broker to handle message subscriptions
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint pour la connexion WebSocket avec support SockJS
        registry.addEndpoint("/ws/market-data")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        
        // Endpoint sans SockJS pour les clients natifs WebSocket
        registry.addEndpoint("/ws/market-data")
                .setAllowedOriginPatterns("*");
    }

    
}