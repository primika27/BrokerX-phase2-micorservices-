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
            // Authentification service routes
            .route("auth_service", r -> r.path("/api/auth/**")
                .uri("http://localhost:8081")) // No JWT filter on login/register

            // Client service routes
            .route("client_service", r -> r.path("/api/clients/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("http://localhost:8082"))

            // Wallet service example
            .route("wallet_service", r -> r.path("/api/wallet/**")
                .filters(f -> f.filter(jwtAuthFilter))
                .uri("http://localhost:8083"))

            .build();
    }

    
}
