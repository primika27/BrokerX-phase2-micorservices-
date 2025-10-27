package com.broker.gatewayService.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {


    private final JwtAuthFilter jwtAuthFilter;

    public GatewayConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
            // Auth service routes (public endpoints without JWT filter)
            .route("auth_service_public", r -> r.path("/api/auth/login", "/api/auth/register", "/api/auth/verify**", "/api/auth/test")
                .uri("http://auth-service:8081"))
            
            // Auth service routes (protected endpoints with JWT filter)  
            .route("auth_service_protected", r -> r.path("/api/auth/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("http://auth-service:8081"))

            // Client service routes (public registration)
            .route("client_service_public", r -> r.path("/api/clients/register", "/api/clients/test")
                .uri("http://client-service:8082"))
                
            // Client service routes (protected)
            .route("client_service_protected", r -> r.path("/api/clients/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("http://client-service:8082"))

            // Wallet service routes
            .route("wallet_service", r -> r.path("/api/wallet/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("http://wallet-service:8083"))

            // Order service routes
            .route("order_service", r -> r.path("/api/orders/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("http://order-service:8084"))

            // Matching service routes
            .route("matching_service", r -> r.path("/api/matching/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("http://matching-service:8085"))

            .build();
    }

    
}
