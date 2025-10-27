package com.broker.gatewayService.config;

import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthFilter implements GatewayFilter {

    private static final List<String> PUBLIC_PREFIXES = List.of(
        "/api/auth/login",   
        "/api/auth/verify",
        "/api/auth/verify-otp",
        "/api/auth/register",
        "/api/auth/test",                 // test endpoint
        "/api/clients/register",
        "/api/clients/test",              // client test endpoint
        "/actuator/health"                // optional health endpoint
    );
    
  private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    private boolean isPublicPath(String path) {
        for (String p : PUBLIC_PREFIXES) {
            if (path.startsWith(p)) return true;
        }
        return false;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        var request = exchange.getRequest();
         
        if (request.getMethod() == HttpMethod.OPTIONS) {
            exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return exchange.getResponse().setComplete();
        }

        // Allow public endpoints
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Allow internal service calls
        String serviceCallHeader = exchange.getRequest().getHeaders().getFirst("X-Service-Call");
        System.out.println("X-Service-Call header: " + serviceCallHeader);
        if (serviceCallHeader != null && !serviceCallHeader.isEmpty()) {
            System.out.println("Appel interne détecté : " + serviceCallHeader);
            // Pour les appels internes, ajouter un header générique
            ServerWebExchange modifiedExchange = exchange.mutate()
                    .request(exchange.getRequest().mutate()
                            .header("X-Authenticated-User", "service-" + serviceCallHeader)
                            .build())
                    .build();
            return chain.filter(modifiedExchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        if (!jwtService.validateToken(token)) {
            System.out.println("JWT validation failed: " + token);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Add email info as a header for downstream services
        String email = jwtService.extractEmail(token);
        ServerWebExchange modifiedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header("X-Authenticated-User", email)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build())
                .build();

        return chain.filter(modifiedExchange);
    }


}
