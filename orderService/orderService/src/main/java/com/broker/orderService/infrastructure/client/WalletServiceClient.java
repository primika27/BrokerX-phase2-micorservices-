package com.broker.orderService.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.broker.orderService.infrastructure.config.OrderServiceFeignConfig;

@FeignClient(name = "wallet-service", url = "http://localhost:8080", configuration = OrderServiceFeignConfig.class)
public interface WalletServiceClient {
    
    // Effectuer une transaction sur le wallet (débit ou crédit)
    @PostMapping("/api/wallet/transaction")
    ResponseEntity<String> walletTransaction(
        @RequestParam("ownerEmail") String ownerEmail,
        @RequestParam("amount") double amount,
        @RequestParam("type") String type
    );
    
    // Vérifier le solde du wallet
    @GetMapping("/api/wallet/balance")
    ResponseEntity<Double> getBalance(@RequestParam("ownerEmail") String ownerEmail);
}