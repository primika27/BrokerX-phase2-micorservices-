
package com.broker.clientService.Infrastructure;

import com.broker.clientService.Application.ClientService;
import com.broker.clientService.domain.Client;
import com.broker.clientService.Infrastructure.Repo.ClientRepository;
import com.broker.clientService.Infrastructure.client.UserCredentialRequest;
import java.util.Map;
import com.broker.clientService.dto.RegisterRequest;
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
    // @PostMapping("/register")
    // public ResponseEntity<String> register(@RequestParam String name, 
    //                                     @RequestParam String email, 
    //                                     @RequestParam String password) {
    //     authClient.createUserCredential(new UserCredentialRequest(email, password));
    //     clientService.register(name, email, password);
    //     return ResponseEntity.ok("Client registered successfully!");
    // }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        try {
            // Use the original register method which handles both auth and client registration
            clientService.register(request.getName(), request.getEmail(), request.getPassword());
            return ResponseEntity.ok("Client registered successfully! Check your email to verify your account.");
        } catch (IllegalArgumentException e) {
            // Handle specific business logic errors (like email already exists)
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            // Handle auth service errors or other errors
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("Email already registered")) {
                return ResponseEntity.badRequest().body("This email address is already registered. Please use a different email or try logging in.");
            }
            return ResponseEntity.badRequest().body("Registration failed. Please try again later.");
        }
    }


@GetMapping("/me")
public ResponseEntity<?> getCurrentClient(
        @RequestHeader(value = "X-Authenticated-User", required = false) String email) {
    
    if (email == null || email.isEmpty()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing authentication header");
    }

    Client client = clientRepository.findByEmail(email);
    if (client == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Client not found");
    }

    // Build a lightweight DTO instead of returning the full entity
    Map<String, Object> response = Map.of(
        "id", client.getClientId(),
        "name", client.getName(),
        "email", client.getEmail(),
        "status", client.getStatut()
    );

    return ResponseEntity.ok(response);
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

