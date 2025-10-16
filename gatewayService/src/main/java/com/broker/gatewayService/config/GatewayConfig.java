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
            // Authentification service routes (with JWT filter that handles public endpoints)
            .route("auth_service", r -> r.path("/api/auth/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("http://localhost:8081"))

            // Client service routes
            .route("client_service", r -> r.path("/api/clients/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("http://localhost:8082"))

            // Wallet service routes
            .route("wallet_service", r -> r.path("/api/wallet/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("http://localhost:8083"))

            // Order service routes
            .route("order_service", r -> r.path("/api/orders/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("http://localhost:8084"))

            // Matching service routes
            .route("matching_service", r -> r.path("/api/matching/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("http://localhost:8085"))

            .build();
    }

    
}
