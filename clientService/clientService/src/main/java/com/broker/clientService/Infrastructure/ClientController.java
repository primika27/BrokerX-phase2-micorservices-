
package com.broker.clientService.Infrastructure;

import com.broker.clientService.Application.ClientService;
import com.broker.clientService.domain.Client;
import com.broker.clientService.Infrastructure.Repo.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

	private final ClientService clientService;
	
	@Autowired
	private ClientRepository clientRepository;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

	// Registration endpoint
	@PostMapping("/register")
	public ResponseEntity<String> register(
            @RequestParam String name,
            @RequestParam String email, 
            @RequestParam String motDePasse
    ) {
        System.out.println("Register endpoint called with: " + name + ", " + email);
                try {
                    Client client = clientService.register(name, email, motDePasse);
                    System.out.println("Registration succeeded for: " + email);
                    return ResponseEntity.ok("Registration successful. Please check your email to verify your account.");
                } catch (IllegalArgumentException e) {
                    System.out.println("Registration failed for: " + email + " - " + e.getMessage());
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
                } catch (Exception e) {
                    System.out.println("Registration failed for: " + email + " - " + e.getMessage());
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Registration failed.");
                }
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

