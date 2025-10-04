

package com.brokerx.brokerx.Infrastructure;


import com.brokerx.brokerx.Application.ClientService;
import com.brokerx.brokerx.domain.Client;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

	private final ClientService clientService;

    private final com.brokerx.brokerx.Infrastructure.Repositories.ClientRepository clientRepository;

    public ClientController(com.brokerx.brokerx.Infrastructure.Repositories.ClientRepository clientRepository, ClientService clientService) {
        this.clientRepository = clientRepository;
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

	// // Verification endpoint (simulated)
	// @GetMapping("/verify")
    // public RedirectView verify(@RequestParam int token) {
    //     boolean result = clientService.verifyByToken(token);
    //     if (result) {
    //         return new RedirectView("/verification-success.html");
    //     } else {
    //         return new RedirectView("/verification-failed.html");
    //     }
    // }
    
}

