package com.broker.orderService.infrastructure.client;

import com.broker.orderService.infrastructure.config.OrderServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "client-service", url = "http://gateway:8080", configuration = OrderServiceFeignConfig.class)
public interface ClientServiceClient {

    @GetMapping("/api/clients/getByEmail")
    ResponseEntity<Integer> getByEmail(
        @RequestHeader("X-Authenticated-User") String authenticatedUser,
        @RequestParam("email") String email
    );

    @GetMapping("/api/clients/getEmailById")
    ResponseEntity<String> getEmailById(
        @RequestHeader("X-Service-Call") String serviceCall,
        @RequestParam("clientId") Integer clientId
    );
}