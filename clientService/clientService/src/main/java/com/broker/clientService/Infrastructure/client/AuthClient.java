package com.broker.clientService.Infrastructure.client;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.broker.clientService.Infrastructure.config.FeignConfig;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "auth-service", url = "http://auth-service:8081/api/auth",
configuration = FeignConfig.class)
public interface AuthClient {

    @PostMapping("/register")
    void createUserCredential(@RequestBody UserCredentialRequest request);
    
}
