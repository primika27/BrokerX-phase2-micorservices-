package com.broker.orderService.infrastructure.client;

import com.broker.orderService.infrastructure.config.OrderServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "client-service", url = "http://localhost:8080", configuration = OrderServiceFeignConfig.class)
public interface ClientServiceClient {

    @GetMapping("/api/clients/getByEmail")
    ResponseEntity<Integer> getByEmail(@RequestParam("email") String email);
}