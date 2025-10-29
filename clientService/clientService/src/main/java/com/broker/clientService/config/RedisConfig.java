package com.broker.clientService.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Configuration Redis pour le Client Service
 * Optimise les requêtes coûteuses identifiées dans les tests de load balancing:
 * - /api/clients/getByEmail (5338 requêtes - 23% du trafic)
 * - /api/clients/getEmailById (5429 requêtes - 24% du trafic)
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configuration des sérialiseurs
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<Object> jsonSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        
        // Configuration Jackson pour une sérialisation optimale
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(objectMapper.getPolymorphicTypeValidator(), 
                                         ObjectMapper.DefaultTyping.NON_FINAL);
        jsonSerializer.setObjectMapper(objectMapper);

        // Key serializer
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        
        // Value serializer
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Configuration spécifique pour les endpoints critiques
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) // TTL par défaut: 10 minutes
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(Object.class)))
                .disableCachingNullValues(); // Ne pas cacher les valeurs nulles

        // Configurations spécialisées par type de cache
        RedisCacheConfiguration emailLookupConfig = defaultConfig
                .entryTtl(Duration.ofMinutes(15)) // Plus long pour les lookups d'emails (moins de changements)
                .prefixCacheNameWith("client:email:");

        RedisCacheConfiguration clientDataConfig = defaultConfig
                .entryTtl(Duration.ofMinutes(5))  // Plus court pour les données client (peuvent changer)
                .prefixCacheNameWith("client:data:");

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("emailLookup", emailLookupConfig)
                .withCacheConfiguration("clientData", clientDataConfig)
                .transactionAware() // Support des transactions Spring
                .build();
    }
}