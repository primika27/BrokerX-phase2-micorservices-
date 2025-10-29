
package com.broker.walletService.infrastructure;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
// import org.springframework.cache.annotation.Cacheable;

import com.broker.walletService.Application.WalletService;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/deposit")
    public ResponseEntity<String> deposit(
            @RequestHeader(value = "X-Authenticated-User", required = true) String ownerEmail,
            @RequestParam Double amount) {
        
        if (ownerEmail == null || ownerEmail.isEmpty()) {
            return ResponseEntity.badRequest().body("Request must go through Gateway - Missing authentication header");
        }
        
        if (amount <= 0) {
            return ResponseEntity.badRequest().body("Deposit amount must be positive.");
        }
        
        try {
            boolean success = walletService.deposit(ownerEmail, amount);
            if (success) {
                return ResponseEntity.ok("Deposit of " + amount + "$ successful.");
            } else {
                return ResponseEntity.badRequest().body("Deposit failed. Please try again.");
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("An error occurred: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    public ResponseEntity<String> createWallet(@RequestHeader("X-Authenticated-User") String ownerEmail) {
        try {
            walletService.createWallet(ownerEmail);
            return ResponseEntity.ok("Wallet created successfully for " + ownerEmail);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to create wallet: " + e.getMessage());
        }
    }

    // Internal service endpoints (no authentication required)
    @PostMapping("/transaction")
    public ResponseEntity<String> transaction(
            @RequestParam("ownerEmail") String ownerEmail,
            @RequestParam("amount") double amount,
            @RequestParam("type") String type) { // "DEBIT" ou "CREDIT"
        System.out.println("transaction endpoint called with ownerEmail: " + ownerEmail + ", amount: " + amount + ", type: " + type);
        
        if (ownerEmail == null || ownerEmail.isEmpty()) {
            return ResponseEntity.badRequest().body("Owner email is required");
        }
        
        if (amount <= 0) {
            return ResponseEntity.badRequest().body("Amount must be positive.");
        }
        
        try {
            boolean success;
            String operation;
            
            if ("DEBIT".equalsIgnoreCase(type)) {
                success = walletService.debit(ownerEmail, amount);
                operation = "Debit";
            } else if ("CREDIT".equalsIgnoreCase(type)) {
                success = walletService.deposit(ownerEmail, amount);
                operation = "Credit";
            } else {
                return ResponseEntity.badRequest().body("Type must be 'DEBIT' or 'CREDIT'");
            }
            
            if (success) {
                return ResponseEntity.ok(operation + " of " + amount + "$ successful.");
            } else {
                return ResponseEntity.badRequest().body(operation + " failed. Check balance or try again.");
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("An error occurred: " + e.getMessage());
        }
    }

    // Unified balance endpoint that handles both user and service calls
    @GetMapping("/balance")
    // @Cacheable(value = "walletBalance", key = "#ownerEmail != null ? #ownerEmail : #authenticatedUser", 
    //            unless = "#result.statusCode.is4xxClientError() or #result.statusCode.is5xxServerError()")
    public ResponseEntity<Double> getBalance(
            @RequestHeader(value = "X-Authenticated-User", required = false) String authenticatedUser,
            @RequestParam(value = "ownerEmail", required = false) String ownerEmail) {
        
        // Si ownerEmail n'est pas fourni en paramètre, utiliser l'email de l'utilisateur authentifié
        if (ownerEmail == null || ownerEmail.isEmpty()) {
            ownerEmail = authenticatedUser;
        }
        
        // Vérifier si c'est un appel de service (via Gateway avec X-Service-Call)
        if (authenticatedUser != null && authenticatedUser.startsWith("service-")) {
            try {
                Double balance = walletService.getBalance(ownerEmail);
                return ResponseEntity.ok(balance);
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(0.0);
            }
        }
        
        // Pour les appels utilisateur normaux via Gateway
        if (authenticatedUser != null && authenticatedUser.equals(ownerEmail)) {
            try {
                Double balance = walletService.getBalance(ownerEmail);
                return ResponseEntity.ok(balance);
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(0.0);
            }
        }
        
        // Si aucune authentification valide
        return ResponseEntity.status(403).body(0.0);
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("WalletService is working!");
    }
}
