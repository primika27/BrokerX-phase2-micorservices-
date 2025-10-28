package com.broker.walletService.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("WalletController - Integration Tests")
class WalletControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should create wallet via API")
    void testCreateWalletEndpoint() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/wallet/create")
                .header("X-Authenticated-User", "newuser@test.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Wallet created successfully")));
    }

    @Test
    @DisplayName("Should deposit money successfully")
    void testDepositEndpoint() throws Exception {
        // Given - First create a wallet
        mockMvc.perform(post("/api/wallet/create")
                .header("X-Authenticated-User", "deposituser@test.com"))
                .andExpect(status().isOk());

        // When & Then - Deposit money
        mockMvc.perform(post("/api/wallet/deposit")
                .header("X-Authenticated-User", "deposituser@test.com")
                .param("amount", "500.0"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Deposit of 500.0$ successful")));
    }

    @Test
    @DisplayName("Should reject negative deposit amount")
    void testDepositNegativeAmount() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/wallet/deposit")
                .header("X-Authenticated-User", "testuser@test.com")
                .param("amount", "-100.0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("must be positive")));
    }

    @Test
    @DisplayName("Should get balance via API")
    void testGetBalanceEndpoint() throws Exception {
        // Given - Create wallet and deposit
        mockMvc.perform(post("/api/wallet/create")
                .header("X-Authenticated-User", "balanceuser@test.com"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/wallet/deposit")
                .header("X-Authenticated-User", "balanceuser@test.com")
                .param("amount", "1000.0"))
                .andExpect(status().isOk());

        // When & Then - Get balance
        mockMvc.perform(get("/api/wallet/balance")
                .header("X-Authenticated-User", "balanceuser@test.com"))
                .andExpect(status().isOk())
                .andExpect(content().string("1000.0"));
    }

    @Test
    @DisplayName("Should return 0 for non-existent wallet balance")
    void testGetBalanceNonExistentWallet() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/wallet/balance")
                .header("X-Authenticated-User", "nonexistent@test.com"))
                .andExpect(status().isOk())
                .andExpect(content().string("0.0"));
    }

    @Test
    @DisplayName("Should reject deposit without authentication header")
    void testDepositWithoutAuth() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/wallet/deposit")
                .param("amount", "100.0"))
                .andExpect(status().isBadRequest());
    }
}
