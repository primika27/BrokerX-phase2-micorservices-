package com.broker.orderService.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    //sent to matching service for new orders
    public static final String ORDER_QUEUE = "orderQueue"; // Must match the queue name in matchingService
    //sent back from the matching service after matching 
    public static final String MATCHING_QUEUE = "matchingQueue"; // Must match the queue name in matchingService

    @Bean
    public Queue orderQueue() {
        return new Queue(ORDER_QUEUE, false);
    }

    @Bean
    public Queue matchingQueue() {
        return new Queue(MATCHING_QUEUE, false);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }
}