package com.broker.clientService.Infrastructure.client;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "auth-service", url = "http://localhost:8082/api/auth")
public interface AuthClient {


    @PostMapping("/register")
    void createUserCredential(@RequestBody UserCredentialRequest request);
    
}
