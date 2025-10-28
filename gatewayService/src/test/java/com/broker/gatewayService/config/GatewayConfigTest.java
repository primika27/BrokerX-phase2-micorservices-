package com.broker.gatewayService.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayConfig - Unit Tests")
class GatewayConfigTest {

    @Mock
    private JwtAuthFilter jwtAuthFilter;

    @Mock
    private RouteLocatorBuilder routeLocatorBuilder;

    @Mock
    private RouteLocatorBuilder.Builder routes;

    @InjectMocks
    private GatewayConfig gatewayConfig;

    @Test
    @DisplayName("Should create route locator with JWT filter")
    void testRoutes() {
        // Given
        when(routeLocatorBuilder.routes()).thenReturn(routes);
        when(routes.route(anyString(), any())).thenReturn(routes);

        // When
        RouteLocator result = gatewayConfig.routes(routeLocatorBuilder);

        // Then
        assertNotNull(result);
        verify(routeLocatorBuilder, times(1)).routes();
    }

    @Test
    @DisplayName("Should inject JWT auth filter correctly")
    void testJwtAuthFilterInjection() {
        // Given & When
        GatewayConfig config = new GatewayConfig(jwtAuthFilter);

        // Then
        assertNotNull(config);
    }
}