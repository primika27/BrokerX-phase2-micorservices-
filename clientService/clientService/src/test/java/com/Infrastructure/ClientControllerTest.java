package com.Infrastructure;
import com.broker.clientService.Application.ClientService;
import com.broker.clientService.Infrastructure.Repo.ClientRepository;
import com.broker.clientService.Infrastructure.client.AuthClient;

import com.broker.clientService.Infrastructure.ClientController;
import com.broker.clientService.Infrastructure.client.UserCredentialRequest;
import com.broker.clientService.domain.Client;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;  

import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientController.class)
class ClientControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ClientService clientService;

    @MockBean
    private ClientRepository clientRepository;

    @MockBean
    private AuthClient authClient;

    @Test
    void testRegister_ShouldReturnSuccess() throws Exception {
        mvc.perform(post("/api/clients/register")
                .param("name", "Alice")
                .param("email", "alice@brokerx.com")
                .param("password", "secret"))
            .andExpect(status().isOk())
            .andExpect(content().string("Client registered successfully!"));

        verify(authClient, times(1)).createUserCredential(any(UserCredentialRequest.class));
        verify(clientService, times(1)).register(anyString(), anyString(), anyString());
    }

    @Test
    void testGetByEmail_ShouldReturnId_WhenClientExists() throws Exception {
        Client client = new Client(1, "John", "john@brokerx.com", 1234, "ACTIVE");
        when(clientRepository.findByEmail("john@brokerx.com")).thenReturn(client);

        mvc.perform(get("/api/clients/getByEmail").param("email", "john@brokerx.com"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    void testGetByEmail_ShouldReturn404_WhenNotFound() throws Exception {
        when(clientRepository.findByEmail(anyString())).thenReturn(null);

        mvc.perform(get("/api/clients/getByEmail").param("email", "missing@brokerx.com"))
                .andExpect(status().isNotFound());
    }
}

