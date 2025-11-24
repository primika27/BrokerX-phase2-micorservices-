package com.broker.marketDataService.controller;

import com.broker.marketDataService.dto.MarketQuote;
import com.broker.marketDataService.service.MarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.Arrays;

@RestController
@RequestMapping("/api/market-data")
@CrossOrigin(origins = "*")
public class MarketDataRestController {

    @Autowired
    private MarketDataService marketDataService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Endpoint pour obtenir les dernières cotations
    @GetMapping("/quotes")
    public ResponseEntity<List<MarketQuote>> getQuotes(@RequestParam(required = false) String symbols) {
        try {
            List<MarketQuote> quotes;
            if (symbols != null && !symbols.isEmpty()) {
                Set<String> symbolSet = Set.of(symbols.split(","));
                quotes = marketDataService.getLatestQuotes(symbolSet);
            } else {
                quotes = marketDataService.getAllLatestQuotes();
            }
            return ResponseEntity.ok(quotes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // === ENDPOINTS DE TEST CACHE ===
    
    @GetMapping("/test/cache/status")
    public ResponseEntity<Map<String, Object>> testCacheStatus() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Test de connexion Redis
            String testKey = "test-connection-" + System.currentTimeMillis();
            String testValue = "Redis is working!";
            
            // Test écriture
            redisTemplate.opsForValue().set(testKey, testValue, Duration.ofSeconds(30));
            
            // Test lecture
            String retrievedValue = (String) redisTemplate.opsForValue().get(testKey);
            
            boolean isRedisWorking = testValue.equals(retrievedValue);
            
            // Nettoyage
            redisTemplate.delete(testKey);
            
            result.put("redis_connected", isRedisWorking);
            result.put("redis_host", redisTemplate.getConnectionFactory().getConnection().getNativeConnection().toString());
            result.put("test_key", testKey);
            result.put("test_value_written", testValue);
            result.put("test_value_read", retrievedValue);
            result.put("timestamp", new Date());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("redis_connected", false);
            result.put("error", e.getMessage());
            result.put("timestamp", new Date());
            return ResponseEntity.ok(result);
        }
    }
    
    @GetMapping("/test/cache/performance")
    public ResponseEntity<Map<String, Object>> testCachePerformance() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String symbol = "AAPL";
            
            // Test 1: Cache Miss (vider d'abord le cache)
            marketDataService.clearMarketDataCache();
            
            long startTime = System.currentTimeMillis();
            MarketQuote quote1 = marketDataService.generateQuote(symbol);
            long cacheMissTime = System.currentTimeMillis() - startTime;
            
            // Test 2: Cache Hit (même requête)
            startTime = System.currentTimeMillis();
            MarketQuote quote2 = marketDataService.generateQuote(symbol);
            long cacheHitTime = System.currentTimeMillis() - startTime;
            
            // Calculer l'amélioration
            double improvement = ((double) (cacheMissTime - cacheHitTime) / cacheMissTime) * 100;
            
            result.put("cache_miss_time_ms", cacheMissTime);
            result.put("cache_hit_time_ms", cacheHitTime);
            result.put("performance_improvement_percent", Math.round(improvement * 100.0) / 100.0);
            result.put("symbol_tested", symbol);
            result.put("quote1", quote1);
            result.put("quote2", quote2);
            result.put("quotes_identical", quote1.equals(quote2));
            result.put("timestamp", new Date());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("timestamp", new Date());
            return ResponseEntity.status(500).body(result);
        }
    }
    
    @PostMapping("/test/cache/clear")
    public ResponseEntity<Map<String, Object>> clearCache() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Compter les clés avant suppression
            Set<String> keysBefore = redisTemplate.keys("brokerx:*");
            int keysCountBefore = keysBefore != null ? keysBefore.size() : 0;
            
            // Vider le cache
            marketDataService.clearMarketDataCache();
            
            // Compter les clés après suppression
            Set<String> keysAfter = redisTemplate.keys("brokerx:*");
            int keysCountAfter = keysAfter != null ? keysAfter.size() : 0;
            
            result.put("cache_cleared", true);
            result.put("keys_before", keysCountBefore);
            result.put("keys_after", keysCountAfter);
            result.put("keys_removed", keysCountBefore - keysCountAfter);
            result.put("timestamp", new Date());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("cache_cleared", false);
            result.put("error", e.getMessage());
            result.put("timestamp", new Date());
            return ResponseEntity.status(500).body(result);
        }
    }
    
    @GetMapping("/test/cache/keys")
    public ResponseEntity<Map<String, Object>> listCacheKeys() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Lister toutes les clés du cache BrokerX
            Set<String> keys = redisTemplate.keys("brokerx:*");
            List<String> keysList = keys != null ? new ArrayList<>(keys) : new ArrayList<>();
            
            // Obtenir les valeurs pour quelques clés
            Map<String, Object> sampleValues = new HashMap<>();
            int maxSamples = Math.min(5, keysList.size());
            
            for (int i = 0; i < maxSamples; i++) {
                String key = keysList.get(i);
                try {
                    Object value = redisTemplate.opsForValue().get(key);
                    sampleValues.put(key, value);
                } catch (Exception e) {
                    sampleValues.put(key, "Error reading value: " + e.getMessage());
                }
            }
            
            result.put("total_keys", keysList.size());
            result.put("keys", keysList);
            result.put("sample_values", sampleValues);
            result.put("timestamp", new Date());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("timestamp", new Date());
            return ResponseEntity.status(500).body(result);
        }
    }

    // === GESTION DES ABONNEMENTS UTILISATEUR ===
    
    @PostMapping("/subscriptions")
    public ResponseEntity<Map<String, Object>> addSubscription(
            @RequestParam String userEmail,
            @RequestParam String symbols,
            @RequestParam(defaultValue = "quotes") String subscriptionType) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String[] symbolArray = symbols.split(",");
            
            // Valider et nettoyer les symboles
            List<String> validSymbols = new ArrayList<>();
            List<String> invalidSymbols = new ArrayList<>();
            
            for (String symbol : symbolArray) {
                String cleanSymbol = symbol.trim().toUpperCase();
                if (marketDataService.isSupportedSymbol(cleanSymbol)) {
                    validSymbols.add(cleanSymbol);
                } else {
                    invalidSymbols.add(cleanSymbol);
                }
            }
            
            if (validSymbols.isEmpty()) {
                result.put("success", false);
                result.put("message", "Aucun symbole valide fourni");
                result.put("invalid_symbols", invalidSymbols);
                return ResponseEntity.badRequest().body(result);
            }
            
            // Ajouter l'abonnement
            marketDataService.addUserSubscription(userEmail, validSymbols, subscriptionType);
            
            result.put("success", true);
            result.put("message", "Abonnement ajouté avec succès");
            result.put("user_email", userEmail);
            result.put("valid_symbols", validSymbols);
            result.put("subscription_type", subscriptionType);
            result.put("timestamp", new Date());
            
            if (!invalidSymbols.isEmpty()) {
                result.put("warning", "Certains symboles ont été ignorés");
                result.put("invalid_symbols", invalidSymbols);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", new Date());
            return ResponseEntity.status(500).body(result);
        }
    }
    
    @DeleteMapping("/subscriptions")
    public ResponseEntity<Map<String, Object>> removeSubscription(
            @RequestParam String userEmail,
            @RequestParam(required = false) String symbols) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (symbols == null || symbols.trim().isEmpty()) {
                // Supprimer tous les abonnements
                marketDataService.removeAllUserSubscriptions(userEmail);
                result.put("message", "Tous les abonnements supprimés");
            } else {
                // Supprimer des symboles spécifiques
                String[] symbolArray = symbols.split(",");
                List<String> symbolList = Arrays.stream(symbolArray)
                    .map(s -> s.trim().toUpperCase())
                    .collect(Collectors.toList());
                
                marketDataService.removeUserSubscription(userEmail, symbolList);
                result.put("message", "Abonnements supprimés pour les symboles spécifiés");
                result.put("removed_symbols", symbolList);
            }
            
            result.put("success", true);
            result.put("user_email", userEmail);
            result.put("timestamp", new Date());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", new Date());
            return ResponseEntity.status(500).body(result);
        }
    }
    
    @GetMapping("/subscriptions")
    public ResponseEntity<Map<String, Object>> getUserSubscriptions(
            @RequestParam String userEmail) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            Map<String, Object> subscriptions = marketDataService.getUserSubscriptions(userEmail);
            
            result.put("success", true);
            result.put("user_email", userEmail);
            result.put("subscriptions", subscriptions);
            result.put("timestamp", new Date());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", new Date());
            return ResponseEntity.status(500).body(result);
        }
    }
    
    @GetMapping("/subscriptions/all")
    public ResponseEntity<Map<String, Object>> getAllSubscriptions() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Map<String, Object> allSubscriptions = marketDataService.getAllSubscriptions();
            
            result.put("success", true);
            result.put("all_subscriptions", allSubscriptions);
            result.put("total_users", allSubscriptions.size());
            result.put("timestamp", new Date());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", new Date());
            return ResponseEntity.status(500).body(result);
        }
    }
    
    @GetMapping("/symbols/supported")
    public ResponseEntity<Map<String, Object>> getSupportedSymbols() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Set<String> supportedSymbols = marketDataService.getSupportedSymbols();
            
            result.put("success", true);
            result.put("supported_symbols", supportedSymbols);
            result.put("total_symbols", supportedSymbols.size());
            result.put("timestamp", new Date());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", new Date());
            return ResponseEntity.status(500).body(result);
        }
    }


}