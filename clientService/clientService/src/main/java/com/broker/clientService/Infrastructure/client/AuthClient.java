package com.broker.clientService.Infrastructure.client;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

public interface AuthClient {


    @PostMapping("/register-credentials")
    void registerCredentials(@RequestParam String email, @RequestParam String password);
    
}
