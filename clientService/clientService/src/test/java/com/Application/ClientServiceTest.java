package com.Application;


import com.broker.clientService.Application.ClientService;
import com.broker.clientService.Infrastructure.Repo.ClientRepository;
import com.broker.clientService.Infrastructure.client.AuthClient;
import com.broker.clientService.Infrastructure.client.UserCredentialRequest;
import com.broker.clientService.domain.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private AuthClient authClient;

    @InjectMocks
    private ClientService clientService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void register_ShouldSaveClient_WhenEmailNotUsed() {
        when(clientRepository.findByEmail("john@brokerx.com")).thenReturn(null);
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Client saved = clientService.register("John", "john@brokerx.com", "secret123");

        assertEquals("John", saved.getName());
        // assertEquals("PENDING", saved.getStatus());
        verify(authClient).createUserCredential(any(UserCredentialRequest.class));
        verify(mailSender).createMimeMessage();
    }

    @Test
    void register_ShouldThrow_WhenEmailAlreadyUsed() {
        when(clientRepository.findByEmail("used@brokerx.com")).thenReturn(new Client());

        assertThrows(IllegalArgumentException.class,
                () -> clientService.register("Alice", "used@brokerx.com", "secret"));
        verifyNoInteractions(mailSender);
    }

    @Test
    void verifyByToken_ShouldActivateClient_WhenTokenExists() {
        Client c1 = new Client(1, "John", "john@brokerx.com", 12345, "PENDING");
        when(clientRepository.findAll()).thenReturn(List.of(c1));
        when(clientRepository.save(any(Client.class))).thenReturn(c1);

        boolean result = clientService.verifyByToken(12345);

        assertTrue(result);
        assertEquals("ACTIVE", c1.getStatut());
    }

    @Test
    void verifyByToken_ShouldReturnFalse_WhenTokenNotFound() {
        when(clientRepository.findAll()).thenReturn(List.of());
        boolean result = clientService.verifyByToken(9999);
        assertFalse(result);
    }

    @Test
    void isDuplicate_ShouldReturnTrue_WhenEmailExists() {
        when(clientRepository.findByEmail("exists@brokerx.com")).thenReturn(new Client());
        assertTrue(clientService.isDuplicate("exists@brokerx.com"));
    }

    @Test
    void isDuplicate_ShouldReturnFalse_WhenEmailNotExists() {
        when(clientRepository.findByEmail("free@brokerx.com")).thenReturn(null);
        assertFalse(clientService.isDuplicate("free@brokerx.com"));
    }
}

