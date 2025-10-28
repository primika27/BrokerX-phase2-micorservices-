package com.broker.clientService.infrastructure;

import com.broker.clientService.dto.RegisterRequest;
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
@DisplayName("ClientController - Integration Tests")
class ClientControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should register new client via API")
    void testRegisterClientEndpoint() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setName("John Doe");
        request.setEmail("john.doe@test.com");
        request.setPassword("SecurePass123!");

        // When & Then
        mockMvc.perform(post("/api/clients/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john.doe@test.com"));
    }

    @Test
    @DisplayName("Should reject duplicate client registration")
    void testRegisterDuplicateClient() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setName("Jane Doe");
        request.setEmail("jane.doe@test.com");
        request.setPassword("SecurePass123!");

        // First registration
        mockMvc.perform(post("/api/clients/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // When & Then - Second registration should fail
        mockMvc.perform(post("/api/clients/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should get client info with X-Authenticated-User header")
    void testGetClientMe() throws Exception {
        // Given - First register a client
        RegisterRequest request = new RegisterRequest();
        request.setName("Test User");
        request.setEmail("testuser@test.com");
        request.setPassword("SecurePass123!");

        mockMvc.perform(post("/api/clients/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // When & Then - Get client info
        mockMvc.perform(get("/api/clients/me")
                .header("X-Authenticated-User", "testuser@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("testuser@test.com"))
                .andExpect(jsonPath("$.name").value("Test User"));
    }

    @Test
    @DisplayName("Should return 404 when client not found")
    void testGetClientMeNotFound() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/clients/me")
                .header("X-Authenticated-User", "nonexistent@test.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should get email by client ID")
    void testGetEmailById() throws Exception {
        // Given - First register a client
        RegisterRequest request = new RegisterRequest();
        request.setName("Email Test User");
        request.setEmail("emailtest@test.com");
        request.setPassword("SecurePass123!");

        String response = mockMvc.perform(post("/api/clients/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract ID from response (assuming JSON has "id" field)
        Long clientId = objectMapper.readTree(response).get("id").asLong();

        // When & Then - Get email by ID
        mockMvc.perform(get("/api/clients/" + clientId + "/email"))
                .andExpect(status().isOk())
                .andExpect(content().string("emailtest@test.com"));
    }
}
