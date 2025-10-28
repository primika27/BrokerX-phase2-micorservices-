package com.broker.gatewayService.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthFilter - Unit Tests")
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private GatewayFilterChain chain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @Test
    @DisplayName("Should allow access to public endpoints without JWT")
    void testPublicEndpoint() {
        // Given
        MockServerHttpRequest publicRequest = MockServerHttpRequest
            .get("/api/auth/login")
            .build();
        ServerWebExchange publicExchange = MockServerWebExchange.from(publicRequest);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // When
        jwtAuthFilter.filter(publicExchange, chain);

        // Then
        verify(jwtService, never()).validateToken(anyString());
        verify(chain, times(1)).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Should pass through request with valid JWT token")
    void testValidJwtToken() {
        // Given
        String validToken = "Bearer valid.jwt.token";
        String userEmail = "user@example.com";
        
        MockServerHttpRequest requestWithAuth = MockServerHttpRequest
            .get("/api/wallet/balance")
            .header(HttpHeaders.AUTHORIZATION, validToken)
            .build();
        
        ServerWebExchange exchangeWithAuth = MockServerWebExchange.from(requestWithAuth);
        
        when(jwtService.extractEmail(anyString())).thenReturn(userEmail);
        when(jwtService.validateToken(anyString())).thenReturn(true);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        // When
        jwtAuthFilter.filter(exchangeWithAuth, chain);

        // Then
        verify(jwtService, times(1)).validateToken(anyString());
        verify(jwtService, times(1)).extractEmail(anyString());
    }

    @Test
    @DisplayName("Should reject request without authorization header")
    void testMissingAuthorizationHeader() {
        // Given
        MockServerHttpRequest protectedRequest = MockServerHttpRequest
            .get("/api/wallet/balance")
            .build();
        ServerWebExchange protectedExchange = MockServerWebExchange.from(protectedRequest);

        // When
        jwtAuthFilter.filter(protectedExchange, chain);

        // Then
        // Should handle missing header appropriately - request should be rejected
        verify(jwtService, never()).validateToken(anyString());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("Should reject request with invalid JWT token")
    void testInvalidJwtToken() {
        // Given
        String invalidToken = "Bearer invalid.jwt.token";
        
        MockServerHttpRequest requestWithInvalidAuth = MockServerHttpRequest
            .get("/api/wallet/balance")
            .header(HttpHeaders.AUTHORIZATION, invalidToken)
            .build();
        
        ServerWebExchange exchangeWithInvalidAuth = MockServerWebExchange.from(requestWithInvalidAuth);
        
        when(jwtService.validateToken(anyString())).thenReturn(false);

        // When
        jwtAuthFilter.filter(exchangeWithInvalidAuth, chain);

        // Then
        verify(jwtService, times(1)).validateToken(anyString());
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }
}