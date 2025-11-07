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
            // Vérifier uniquement X-Authenticated-User (venant du gateway)
            // Le Gateway a déjà validé le JWT, pas besoin de re-validation
            String authenticatedUser = accessor.getFirstNativeHeader("X-Authenticated-User");
            if (authenticatedUser != null && !authenticatedUser.isEmpty()) {
                accessor.setUser(new JwtPrincipal(authenticatedUser));
            } else {
                throw new SecurityException("Authentification requise - doit passer par le Gateway");
            }
        }
        
        return message;
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