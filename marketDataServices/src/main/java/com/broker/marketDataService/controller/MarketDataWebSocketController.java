package com.broker.marketDataService.controller;

import com.broker.marketDataService.dto.SubscriptionRequest;
import com.broker.marketDataService.service.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class MarketDataWebSocketController {

    @Autowired
    private MarketDataService marketDataService;

  
    @MessageMapping("/subscribe")
    public void subscribe(@Payload SubscriptionRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String userEmail = extractUserEmailFromHeaders(headerAccessor);
        if (userEmail == null) {
            System.err.println("Authentification échouée - token JWT invalide ou manquant");
            return;
        }
        
        System.out.println("Nouvelle demande d'abonnement pour l'utilisateur: " + userEmail);
        System.out.println("Symboles: " + String.join(", ", request.getSymbols()));
        System.out.println("Type: " + request.getSubscriptionType());
        
        // Validation des symboles
        if (request.getSymbols() == null || request.getSymbols().length == 0) {
            marketDataService.sendError(userEmail, "Aucun symbole spécifié");
            return;
        }
        
        // Vérification des quotas et rate limiting (implémentation simple)
        if (request.getSymbols().length > 10) {
            marketDataService.sendError(userEmail, "Trop de symboles demandés. Maximum: 10");
            return;
        }
        
        // Enregistrer l'abonnement
        marketDataService.addSubscription(userEmail, request.getSymbols(), request.getSubscriptionType());
        
        // Confirmer l'abonnement
        marketDataService.sendSubscriptionConfirmation(userEmail, request.getSymbols());
    }

    @MessageMapping("/unsubscribe")
    public void unsubscribe(@Payload SubscriptionRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String userEmail = extractUserEmailFromHeaders(headerAccessor);
        if (userEmail == null) {
            System.err.println("Authentification échouée - token JWT invalide ou manquant");
            return;
        }
        
        System.out.println("Demande de désabonnement pour l'utilisateur: " + userEmail);
        
        if (request.getSymbols() == null || request.getSymbols().length == 0) {
            // Désabonner de tous les symboles
            marketDataService.removeAllSubscriptions(userEmail);
        } else {
            // Désabonner de symboles spécifiques
            marketDataService.removeSubscription(userEmail, request.getSymbols());
        }
    }


    @MessageMapping("/snapshot")
    public void getSnapshot(@Payload String symbol, SimpMessageHeaderAccessor headerAccessor) {
        String userEmail = extractUserEmailFromHeaders(headerAccessor);
        if (userEmail == null) {
            System.err.println("Authentification échouée - token JWT invalide ou manquant");
            return;
        }
        
        System.out.println("Demande de snapshot pour " + symbol + " par l'utilisateur: " + userEmail);
        
        // Envoyer le snapshot immédiatement
        marketDataService.sendOrderBookSnapshot(userEmail, symbol);
    }
    
    /**
     * Extrait l'email de l'utilisateur à partir du principal Spring Security
     * défini par le JwtChannelInterceptor
     */
    private String extractUserEmailFromHeaders(SimpMessageHeaderAccessor headerAccessor) {
        // First try to get user from Spring Security principal (set by JwtChannelInterceptor)
        if (headerAccessor != null && headerAccessor.getUser() != null) {
            return headerAccessor.getUser().getName();
        }
        
        // Fallback: check for X-Authenticated-User header from Gateway
        if (headerAccessor != null) {
            String authenticatedUser = headerAccessor.getFirstNativeHeader("X-Authenticated-User");
            if (authenticatedUser != null && !authenticatedUser.isEmpty()) {
                return authenticatedUser;
            }
        }
        
        System.err.println("No authenticated user found - neither principal nor header present");
        return null;
    }
}