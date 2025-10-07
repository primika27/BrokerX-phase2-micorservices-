
package com.broker.walletService.infrastructure;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

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

    @GetMapping("/balance")
    public ResponseEntity<Double> getBalance(@RequestHeader(value = "X-Authenticated-User", required = true) String ownerEmail) {
        try {
            Double balance = walletService.getBalance(ownerEmail);
            return ResponseEntity.ok(balance);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(0.0);
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
}
