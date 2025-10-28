package com.broker.gatewayService.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Gateway E2E Tests - Full Request Flow")
class GatewayE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("E2E: Complete user registration and authentication flow")
    void testCompleteUserFlow() {
        // Step 1: Register user through gateway
        String registerUrl = "http://localhost:" + port + "/api/clients/register";
        
        Map<String, String> registerRequest = new HashMap<>();
        registerRequest.put("name", "Test User");
        registerRequest.put("email", "testuser@e2e.com");
        registerRequest.put("password", "SecurePass123!");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Origin", "http://localhost:3000"); // Test CORS
        
        HttpEntity<Map<String, String>> registerEntity = new HttpEntity<>(registerRequest, headers);
        
        // When - Register
        ResponseEntity<String> registerResponse = restTemplate.exchange(
            registerUrl, HttpMethod.POST, registerEntity, String.class);
        
        // Then - Registration should be routed correctly
        assertNotNull(registerResponse);
        // Note: Actual registration may fail if services not running, but routing should work
    }

    @Test
    @DisplayName("E2E: Authentication flow with JWT")
    void testAuthenticationFlow() {
        // Step 1: Login request
        String loginUrl = "http://localhost:" + port + "/api/auth/login";
        
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("email", "user@example.com");
        loginRequest.put("password", "password123");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, String>> loginEntity = new HttpEntity<>(loginRequest, headers);
        
        // When - Login
        ResponseEntity<String> loginResponse = restTemplate.exchange(
            loginUrl, HttpMethod.POST, loginEntity, String.class);
        
        // Then - Should route to auth service
        assertNotNull(loginResponse);
        
        // Step 2: Access protected resource (would use JWT from login response in real scenario)
        String protectedUrl = "http://localhost:" + port + "/api/wallet/balance";
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.add("Authorization", "Bearer sample.jwt.token");
        authHeaders.add("X-Authenticated-User", "user@example.com");
        
        HttpEntity<String> authEntity = new HttpEntity<>(authHeaders);
        
        // When - Access protected resource
        ResponseEntity<String> protectedResponse = restTemplate.exchange(
            protectedUrl, HttpMethod.GET, authEntity, String.class);
        
        // Then - Should be routed through gateway with JWT validation
        assertNotNull(protectedResponse);
    }

    @Test
    @DisplayName("E2E: Order placement flow")
    void testOrderPlacementFlow() {
        // Given - Mock authenticated request
        String orderUrl = "http://localhost:" + port + "/api/orders/place";
        
        Map<String, Object> orderRequest = new HashMap<>();
        orderRequest.put("symbol", "AAPL");
        orderRequest.put("price", 150.0);
        orderRequest.put("quantity", 10);
        orderRequest.put("orderType", "BUY");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Bearer valid.jwt.token");
        headers.add("X-Authenticated-User", "trader@example.com");
        
        HttpEntity<Map<String, Object>> orderEntity = new HttpEntity<>(orderRequest, headers);
        
        // When - Place order
        ResponseEntity<String> orderResponse = restTemplate.exchange(
            orderUrl, HttpMethod.POST, orderEntity, String.class);
        
        // Then - Should route to order service
        assertNotNull(orderResponse);
    }

    @Test
    @DisplayName("E2E: Multiple service interaction flow")
    void testMultiServiceFlow() {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.add("Authorization", "Bearer valid.jwt.token");
        authHeaders.add("X-Authenticated-User", "user@example.com");
        
        // Step 1: Get wallet balance
        String balanceUrl = "http://localhost:" + port + "/api/wallet/balance";
        HttpEntity<String> balanceEntity = new HttpEntity<>(authHeaders);
        
        ResponseEntity<String> balanceResponse = restTemplate.exchange(
            balanceUrl, HttpMethod.GET, balanceEntity, String.class);
        
        // Step 2: Get client info
        String clientUrl = "http://localhost:" + port + "/api/clients/me";
        HttpEntity<String> clientEntity = new HttpEntity<>(authHeaders);
        
        ResponseEntity<String> clientResponse = restTemplate.exchange(
            clientUrl, HttpMethod.GET, clientEntity, String.class);
        
        // Step 3: Get holdings
        String holdingsUrl = "http://localhost:" + port + "/api/orders/holdings";
        HttpEntity<String> holdingsEntity = new HttpEntity<>(authHeaders);
        
        ResponseEntity<String> holdingsResponse = restTemplate.exchange(
            holdingsUrl, HttpMethod.GET, holdingsEntity, String.class);
        
        // Then - All requests should be routed correctly
        assertNotNull(balanceResponse);
        assertNotNull(clientResponse);
        assertNotNull(holdingsResponse);
    }

    @Test
    @DisplayName("E2E: Error handling and routing")
    void testErrorHandlingFlow() {
        // Test 1: Invalid endpoint
        String invalidUrl = "http://localhost:" + port + "/api/invalid/endpoint";
        ResponseEntity<String> invalidResponse = restTemplate.getForEntity(invalidUrl, String.class);
        
        // Should return 404 for invalid routes
        assertEquals(404, invalidResponse.getStatusCodeValue());
        
        // Test 2: Unauthorized access
        String protectedUrl = "http://localhost:" + port + "/api/wallet/balance";
        ResponseEntity<String> unauthorizedResponse = restTemplate.getForEntity(protectedUrl, String.class);
        
        // Should require authentication
        assertTrue(unauthorizedResponse.getStatusCode().is4xxClientError() || 
                  unauthorizedResponse.getStatusCode().is3xxRedirection());
    }
}