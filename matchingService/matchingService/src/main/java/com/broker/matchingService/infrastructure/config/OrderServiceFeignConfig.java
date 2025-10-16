package com.broker.orderService.infrastructure.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderServiceFeignConfig implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // Ajouter un header pour identifier les appels du OrderService
        template.header("X-Service-Call", "order-service");
    }
}