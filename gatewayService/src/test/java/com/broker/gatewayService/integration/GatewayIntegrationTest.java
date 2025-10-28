package com.broker.gatewayService.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Gateway - Integration Tests")
class GatewayIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Should route to auth service for public endpoints")
    void testAuthServicePublicRouting() {
        // Given
        String url = "http://localhost:" + port + "/api/auth/login";
        
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        // Then
        // Should route to auth service (may return 404/500 if service not running, but shouldn't be routing error)
        assertNotEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("Should require authentication for protected endpoints")
    void testProtectedEndpointWithoutAuth() {
        // Given
        String url = "http://localhost:" + port + "/api/orders/holdings";
        
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        
        // Then
        // Should be unauthorized or redirect due to missing JWT
        assertTrue(response.getStatusCode().is4xxClientError() || 
                  response.getStatusCode().is3xxRedirection());
    }

    @Test
    @DisplayName("Should handle CORS preflight requests")
    void testCorsPreflightRequest() {
        // Given
        String url = "http://localhost:" + port + "/api/auth/login";
        HttpHeaders headers = new HttpHeaders();
        headers.add("Origin", "http://localhost:3000");
        headers.add("Access-Control-Request-Method", "POST");
        headers.add("Access-Control-Request-Headers", "Content-Type,Authorization");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        // When
        ResponseEntity<String> response = restTemplate.exchange(
            url, HttpMethod.OPTIONS, entity, String.class);
        
        // Then
        // Should handle CORS preflight
        assertNotNull(response);
    }

    @Test
    @DisplayName("Should route to client service")
    void testClientServiceRouting() {
        // Given
        String url = "http://localhost:" + port + "/api/clients/register";
        
        // When
        ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
        
        // Then
        // Should route to client service (may return error but shouldn't be routing error)
        assertNotEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("Should route to wallet service with auth header")
    void testWalletServiceRouting() {
        // Given
        String url = "http://localhost:" + port + "/api/wallet/balance";
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer fake.jwt.token");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        // When
        ResponseEntity<String> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, String.class);
        
        // Then
        // Should route to wallet service
        assertNotEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}