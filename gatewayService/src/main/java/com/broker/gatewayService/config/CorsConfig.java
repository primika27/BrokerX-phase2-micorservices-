package com.broker.gatewayService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        // Allow specific origin (frontend)
        corsConfig.addAllowedOriginPattern("http://localhost:5173");
        corsConfig.addAllowedOriginPattern("http://localhost:3000"); // For dev
        
        // Allow all methods
        corsConfig.addAllowedMethod("*");
        
        // Allow all headers
        corsConfig.addAllowedHeader("*");
        
        // Allow credentials
        corsConfig.setAllowCredentials(true);
        
        // Cache preflight response
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}