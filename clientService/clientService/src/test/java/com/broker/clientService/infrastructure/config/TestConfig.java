package com.broker.clientService.infrastructure.config;

import com.broker.clientService.Infrastructure.client.AuthClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.mockito.Mockito;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public AuthClient mockAuthClient() {
        return Mockito.mock(AuthClient.class);
    }
}