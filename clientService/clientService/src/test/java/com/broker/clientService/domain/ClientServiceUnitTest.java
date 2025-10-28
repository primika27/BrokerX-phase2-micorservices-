package com.broker.clientService.domain;

import com.broker.clientService.Application.ClientService;
import com.broker.clientService.Infrastructure.Repo.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClientService - Unit Tests")
class ClientServiceUnitTest {

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private ClientService clientService;

    private Client testClient;

    @BeforeEach
    void setUp() {
        testClient = new Client();
        testClient.setClientId(1);
        testClient.setEmail("test@example.com");
        testClient.setName("Test User");
    }

    @Test
    @DisplayName("Should register new client successfully")
    void testRegisterClient() {
        // Given
        when(clientRepository.findByEmail(testClient.getEmail())).thenReturn(null);
        when(clientRepository.save(any(Client.class))).thenReturn(testClient);

        // When
        Client result = clientService.register("Test User", "test@example.com", "password123");

        // Then
        assertNotNull(result);
        assertEquals("Test User", result.getName());
        assertEquals("test@example.com", result.getEmail());
        verify(clientRepository, times(1)).save(any(Client.class));
    }

    @Test
    @DisplayName("Should throw exception when registering duplicate email")
    void testRegisterDuplicateClient() {
        // Given
        when(clientRepository.findByEmail(testClient.getEmail())).thenReturn(testClient);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            clientService.register("Test User", "test@example.com", "password123");
        });
        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    @DisplayName("Should find client by email")
    void testGetClientByEmail() {
        // Given
        when(clientRepository.findByEmail(testClient.getEmail())).thenReturn(testClient);

        // When
        Client result = clientService.getClientByEmail("test@example.com");

        // Then
        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        verify(clientRepository, times(1)).findByEmail("test@example.com");
    }

    @Test
    @DisplayName("Should return null when client not found by email")
    void testGetClientByEmailNotFound() {
        // Given
        when(clientRepository.findByEmail("nonexistent@example.com")).thenReturn(null);

        // When
        Client result = clientService.getClientByEmail("nonexistent@example.com");

        // Then
        assertNull(result);
        verify(clientRepository, times(1)).findByEmail("nonexistent@example.com");
    }

    @Test
    @DisplayName("Should check if email is duplicate")
    void testIsDuplicate() {
        // Given
        when(clientRepository.findByEmail("existing@example.com")).thenReturn(testClient);

        // When
        boolean result = clientService.isDuplicate("existing@example.com");

        // Then
        assertTrue(result);
        verify(clientRepository, times(1)).findByEmail("existing@example.com");
    }

    @Test
    @DisplayName("Should return false for non-duplicate email")
    void testIsNotDuplicate() {
        // Given
        when(clientRepository.findByEmail("new@example.com")).thenReturn(null);

        // When
        boolean result = clientService.isDuplicate("new@example.com");

        // Then
        assertFalse(result);
        verify(clientRepository, times(1)).findByEmail("new@example.com");
    }
}
