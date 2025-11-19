package com.broker.orderService.saga;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class SagaTestConfig {
    
    @Bean
    @Primary
    public SimpMessagingTemplate mockSimpMessagingTemplate() {
        return mock(SimpMessagingTemplate.class);
    }
}