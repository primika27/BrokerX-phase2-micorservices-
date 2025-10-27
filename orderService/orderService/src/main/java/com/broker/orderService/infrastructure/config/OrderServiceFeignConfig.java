package com.broker.orderService.infrastructure.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderServiceFeignConfig implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // Ajouter un header pour identifier les appels du OrderService
        template.header("X-Service-Call", "order-service");
        
        // Pour les appels de service à service, on doit passer l'email de l'utilisateur authentifié
        // Cet email sera utilisé par le service cible
        // L'email sera passé dynamiquement via les paramètres de la méthode Feign
    }
}