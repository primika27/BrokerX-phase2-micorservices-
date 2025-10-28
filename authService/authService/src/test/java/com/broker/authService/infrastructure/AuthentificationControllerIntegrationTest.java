package com.broker.authService.infrastructure;

import com.broker.authService.infrastructure.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AuthController - Integration Tests")
class AuthentificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should register new user via API")
    void testRegisterEndpoint() throws Exception {
        // Given
        UserCredentialRequest request = new UserCredentialRequest();
        request.setEmail("newuser@test.com");
        request.setPassword("Test123!");

        // When & Then
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    @DisplayName("Should login with valid credentials via API")
    void testLoginEndpoint() throws Exception {
        // Given - First register a user
        UserCredentialRequest registerRequest = new UserCredentialRequest();
        registerRequest.setEmail("loginuser@test.com");
        registerRequest.setPassword("Test123!");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        // When & Then - Try to login
        UserCredentialRequest loginRequest = new UserCredentialRequest();
        loginRequest.setEmail("loginuser@test.com");
        loginRequest.setPassword("Test123!");

        mockMvc.perform(post("/api/auth/simple-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    @DisplayName("Should reject login with wrong password")
    void testLoginWithWrongPassword() throws Exception {
        // Given - Register a user first
        UserCredentialRequest registerRequest = new UserCredentialRequest();
        registerRequest.setEmail("testuser@test.com");
        registerRequest.setPassword("CorrectPassword123!");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        // When & Then - Try wrong password
        UserCredentialRequest loginRequest = new UserCredentialRequest();
        loginRequest.setEmail("testuser@test.com");
        loginRequest.setPassword("WrongPassword!");

        mockMvc.perform(post("/api/auth/simple-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject duplicate email registration")
    void testDuplicateRegistration() throws Exception {
        // Given
        UserCredentialRequest request = new UserCredentialRequest();
        request.setEmail("duplicate@test.com");
        request.setPassword("Test123!");

        // First registration
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // When & Then - Second registration should fail
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
