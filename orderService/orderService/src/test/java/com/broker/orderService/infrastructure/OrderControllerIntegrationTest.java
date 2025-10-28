package com.broker.orderService.infrastructure;

import com.broker.orderService.dto.OrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.broker.orderService.infrastructure.client.ClientServiceClient;
import com.broker.orderService.infrastructure.client.WalletServiceClient;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("OrderController - Integration Tests")
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WalletServiceClient walletServiceClient;

    @MockBean
    private ClientServiceClient clientServiceClient;

    @Test
    @DisplayName("Should place order successfully with sufficient balance")
    void testPlaceOrderEndpoint() throws Exception {
        // Given - Mock external service calls
        when(clientServiceClient.getByEmail(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(100));
        when(walletServiceClient.getBalance(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(10000.0));

        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setSymbol("AAPL");
        orderRequest.setPrice(150.0);
        orderRequest.setQuantity(10);

        // When & Then
        mockMvc.perform(post("/api/orders/place")
                .header("X-Authenticated-User", "test@example.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Order placed successfully")));
    }

    @Test
    @DisplayName("Should reject order with insufficient balance")
    void testPlaceOrderInsufficientBalance() throws Exception {
        // Given - Mock with insufficient balance
        when(clientServiceClient.getByEmail(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(100));
        when(walletServiceClient.getBalance(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(500.0)); // Not enough

        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setSymbol("AAPL");
        orderRequest.setPrice(150.0);
        orderRequest.setQuantity(10);

        // When & Then
        mockMvc.perform(post("/api/orders/place")
                .header("X-Authenticated-User", "test@example.com")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should get holdings successfully")
    void testGetHoldingsEndpoint() throws Exception {
        // Given
        when(clientServiceClient.getByEmail(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(100));

        // When & Then
        mockMvc.perform(get("/api/orders/holdings")
                .header("X-Authenticated-User", "test@example.com"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should reject order without authentication header")
    void testPlaceOrderWithoutAuth() throws Exception {
        // Given
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setSymbol("AAPL");
        orderRequest.setPrice(150.0);
        orderRequest.setQuantity(10);

        // When & Then
        mockMvc.perform(post("/api/orders/place")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should get all orders for authenticated user")
    void testGetAllOrdersEndpoint() throws Exception {
        // Given
        when(clientServiceClient.getByEmail(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(100));

        // When & Then
        mockMvc.perform(get("/api/orders/all")
                .header("X-Authenticated-User", "test@example.com"))
                .andExpect(status().isOk());
    }
}
