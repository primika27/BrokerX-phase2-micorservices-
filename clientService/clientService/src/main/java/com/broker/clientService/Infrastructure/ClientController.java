
package com.broker.clientService.Infrastructure;

import com.broker.clientService.Application.ClientService;
import com.broker.clientService.domain.Client;
import com.broker.clientService.Infrastructure.Repo.ClientRepository;
import com.broker.clientService.Infrastructure.client.UserCredentialRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.broker.clientService.Infrastructure.client.AuthClient;
@RestController
@RequestMapping("/api/clients")
public class ClientController {

	private final ClientService clientService;
	
	@Autowired
	private ClientRepository clientRepository;

	@Autowired
	private AuthClient authClient;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

	// Registration endpoint
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestParam String name, 
                                        @RequestParam String email, 
                                        @RequestParam String password) {
        authClient.createUserCredential(new UserCredentialRequest(email, password));
        clientService.register(name, email, password);
        return ResponseEntity.ok("Client registered successfully!");
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("ClientService is working!");
    }


    @GetMapping("/getByEmail")
    public ResponseEntity<Integer> getByEmail(@RequestParam String email) {
        Client client = clientRepository.findByEmail(email);
        if (client != null) {
            return ResponseEntity.ok(client.getClientId());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    
}

