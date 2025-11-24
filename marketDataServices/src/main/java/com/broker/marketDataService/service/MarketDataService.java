package com.broker.marketDataService.service;

import com.broker.marketDataService.dto.MarketQuote;
import com.broker.marketDataService.dto.OrderBook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.time.Duration;

@Service
public class MarketDataService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    // Clés Redis pour les abonnements
    private static final String REDIS_USER_SUBSCRIPTIONS_KEY = "brokerx:subscriptions:user:";
    private static final String REDIS_USER_SUB_TYPE_KEY = "brokerx:subscription-types:user:";
    private static final int SUBSCRIPTION_TTL_HOURS = 24; // TTL pour les abonnements

    // Gestion des abonnements par email utilisateur
    private final Map<String, Set<String>> userSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, String> userSubscriptionTypes = new ConcurrentHashMap<>();
    
    // Cache des derniers prix pour simulation
    private final Map<String, Double> lastPrices = new ConcurrentHashMap<>();
    
    // Symboles supportés (même liste que OrderController)
    private final Set<String> supportedSymbols = Set.of(
            "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA",
            "SPY", "QQQ", "VTI", "BND", "IWM", "EFA"
    );

    public MarketDataService() {
        // Initialiser les prix de base
        initializeBasePrices();
    }

    private void initializeBasePrices() {
        lastPrices.put("AAPL", 150.25);
        lastPrices.put("GOOGL", 2750.80);
        lastPrices.put("MSFT", 331.62);
        lastPrices.put("AMZN", 3380.50);
        lastPrices.put("TSLA", 1025.20);
        lastPrices.put("SPY", 445.30);
        lastPrices.put("QQQ", 378.90);
        lastPrices.put("VTI", 230.15);
        lastPrices.put("BND", 73.25);
        lastPrices.put("IWM", 220.30);
        lastPrices.put("EFA", 79.90);
    }


    public void addSubscription(String userEmail, String[] symbols, String subscriptionType) {
        Set<String> validSymbols = new HashSet<>();
        
        for (String symbol : symbols) {
            if (supportedSymbols.contains(symbol)) {
                validSymbols.add(symbol);
            }
        }
        
        userSubscriptions.put(userEmail, validSymbols);
        userSubscriptionTypes.put(userEmail, subscriptionType);
        
        System.out.println("Abonnement ajouté pour utilisateur " + userEmail + 
                          ": " + validSymbols + " (type: " + subscriptionType + ")");
    }

    public void removeSubscription(String userEmail, String[] symbols) {
        Set<String> currentSubscriptions = userSubscriptions.get(userEmail);
        if (currentSubscriptions != null) {
            for (String symbol : symbols) {
                currentSubscriptions.remove(symbol);
            }
            if (currentSubscriptions.isEmpty()) {
                userSubscriptions.remove(userEmail);
                userSubscriptionTypes.remove(userEmail);
            }
        }
    }

    public void removeAllSubscriptions(String userEmail) {
        userSubscriptions.remove(userEmail);
        userSubscriptionTypes.remove(userEmail);
        System.out.println("Tous les abonnements supprimés pour utilisateur " + userEmail);
    }


    public void sendSubscriptionConfirmation(String userEmail, String[] symbols) {
        Map<String, Object> confirmation = new HashMap<>();
        confirmation.put("type", "subscription_confirmed");
        confirmation.put("symbols", symbols);
        confirmation.put("timestamp", System.currentTimeMillis());
        
        messagingTemplate.convertAndSendToUser(userEmail, "/queue/confirmation", confirmation);
    }


    public void sendError(String userEmail, String errorMessage) {
        Map<String, Object> error = new HashMap<>();
        error.put("type", "error");
        error.put("message", errorMessage);
        error.put("timestamp", System.currentTimeMillis());
        
        messagingTemplate.convertAndSendToUser(userEmail, "/queue/error", error);
    }


    public void sendOrderBookSnapshot(String userEmail, String symbol) {
        if (!supportedSymbols.contains(symbol)) {
            sendError(userEmail, "Symbole non supporté: " + symbol);
            return;
        }
        
        OrderBook orderBook = generateOrderBookSnapshot(symbol);
        messagingTemplate.convertAndSendToUser(userEmail, "/queue/snapshot", orderBook);
    }

    @Scheduled(fixedRate = 1000) // Chaque seconde
    public void broadcastMarketData() {
        if (userSubscriptions.isEmpty()) {
            return;
        }

        // Pour chaque utilisateur actif
        for (Map.Entry<String, Set<String>> entry : userSubscriptions.entrySet()) {
            String userEmail = entry.getKey();
            Set<String> symbols = entry.getValue();
            String subscriptionType = userSubscriptionTypes.get(userEmail);

            for (String symbol : symbols) {
                if ("quotes".equals(subscriptionType) || subscriptionType == null) {
                    MarketQuote quote = generateMarketQuote(symbol);
                    messagingTemplate.convertAndSendToUser(userEmail, "/queue/quotes", quote);
                }
                
                if ("orderbook".equals(subscriptionType)) {
                    OrderBook orderBook = generateOrderBookUpdate(symbol);
                    messagingTemplate.convertAndSendToUser(userEmail, "/queue/orderbook", orderBook);
                }
            }
        }
    }
    private MarketQuote generateMarketQuote(String symbol) {
        double currentPrice = lastPrices.get(symbol);
        
        // Génération de volatilité (±2%)
        double volatility = 0.02;
        double change = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * volatility * currentPrice;
        double newPrice = Math.max(0.01, currentPrice + change);
        
        lastPrices.put(symbol, newPrice);
        
        // Spread bid/ask (0.1% du prix)
        double spread = newPrice * 0.001;
        double bid = newPrice - spread / 2;
        double ask = newPrice + spread / 2;
        
        int volume = ThreadLocalRandom.current().nextInt(100, 10000);
        double changePercent = (change / currentPrice) * 100;
        
        MarketQuote quote = new MarketQuote(symbol, bid, ask, newPrice, volume);
        quote.setChange(change);
        quote.setChangePercent(changePercent);
        
        return quote;
    }

    private OrderBook generateOrderBookSnapshot(String symbol) {
        double currentPrice = lastPrices.get(symbol);
        
        List<OrderBook.BookLevel> bids = new ArrayList<>();
        List<OrderBook.BookLevel> asks = new ArrayList<>();
        
        // Générer 5 niveaux de bid/ask
        for (int i = 0; i < 5; i++) {
            double bidPrice = currentPrice - (i + 1) * 0.01;
            double askPrice = currentPrice + (i + 1) * 0.01;
            
            int bidQty = ThreadLocalRandom.current().nextInt(100, 1000);
            int askQty = ThreadLocalRandom.current().nextInt(100, 1000);
            
            bids.add(new OrderBook.BookLevel(bidPrice, bidQty));
            asks.add(new OrderBook.BookLevel(askPrice, askQty));
        }
        
        return new OrderBook(symbol, bids, asks);
    }

    private OrderBook generateOrderBookUpdate(String symbol) {
        return generateOrderBookSnapshot(symbol); // Pour la simplicité, on renvoie un snapshot complet
    }


    public void handleDisconnection(String sessionId) {
        removeAllSubscriptions(sessionId);
        System.out.println("Session déconnectée et nettoyée: " + sessionId);
    }

    // Méthodes pour l'API REST avec cache Redis
    @Cacheable(value = "market-quotes", key = "#symbols.toString()")
    public List<MarketQuote> getLatestQuotes(Set<String> symbols) {
        System.out.println("Cache MISS - Récupération des quotes pour: " + symbols);
        List<MarketQuote> quotes = new ArrayList<>();
        for (String symbol : symbols) {
            if (supportedSymbols.contains(symbol)) {
                quotes.add(generateQuote(symbol));
            }
        }
        return quotes;
    }
    
    @Cacheable(value = "market-quote", key = "#symbol")
    public MarketQuote generateQuote(String symbol) {
        System.out.println("Cache MISS - Génération quote pour: " + symbol);
        double currentPrice = lastPrices.get(symbol);
        
        // Spread bid/ask (0.1% du prix)
        double spread = currentPrice * 0.001;
        double bid = currentPrice - spread / 2;
        double ask = currentPrice + spread / 2;
        
        int volume = ThreadLocalRandom.current().nextInt(100, 10000);
        
        return new MarketQuote(symbol, bid, ask, currentPrice, volume);
    }

    @Cacheable(value = "market-quotes", key = "'all-quotes'")
    public List<MarketQuote> getAllLatestQuotes() {
        System.out.println("Cache MISS - Récupération de tous les quotes");
        return getLatestQuotes(supportedSymbols);
    }

    // Invalide le cache lorsque les prix sont mis à jour
    @CacheEvict(value = {"market-quotes", "market-quote"}, allEntries = true)
    public void clearMarketDataCache() {
        System.out.println("Cache Redis invalidé pour market data");
    }
    
    // === NOUVELLES MÉTHODES DE GESTION DES ABONNEMENTS ===
    
    public boolean isSupportedSymbol(String symbol) {
        return supportedSymbols.contains(symbol);
    }
    
    public Set<String> getSupportedSymbols() {
        return new HashSet<>(supportedSymbols);
    }
    
    public void addUserSubscription(String userEmail, List<String> symbols, String subscriptionType) {
        try {
            // Valider les symboles
            Set<String> validSymbols = symbols.stream()
                .filter(supportedSymbols::contains)
                .collect(Collectors.toSet());
            
            if (validSymbols.isEmpty()) {
                System.out.println("Aucun symbole valide pour l'utilisateur: " + userEmail);
                return;
            }
            
            // Récupérer les abonnements existants depuis Redis
            String userKey = REDIS_USER_SUBSCRIPTIONS_KEY + userEmail;
            String typeKey = REDIS_USER_SUB_TYPE_KEY + userEmail;
            
            @SuppressWarnings("unchecked")
            Set<String> existingSymbols = (Set<String>) redisTemplate.opsForValue().get(userKey);
            if (existingSymbols == null) {
                existingSymbols = new HashSet<>();
            }
            
            // Ajouter les nouveaux symboles
            existingSymbols.addAll(validSymbols);
            
            // Sauvegarder dans Redis avec TTL
            redisTemplate.opsForValue().set(userKey, existingSymbols, Duration.ofHours(SUBSCRIPTION_TTL_HOURS));
            redisTemplate.opsForValue().set(typeKey, subscriptionType, Duration.ofHours(SUBSCRIPTION_TTL_HOURS));
            
            // Mettre à jour le cache mémoire pour compatibilité
            userSubscriptions.put(userEmail, existingSymbols);
            userSubscriptionTypes.put(userEmail, subscriptionType);
            
            System.out.println("Abonnement ajouté/mis à jour pour " + userEmail + ": " + existingSymbols);
            
            // Envoyer confirmation
            sendSubscriptionConfirmation(userEmail, validSymbols.toArray(new String[0]));
            
        } catch (Exception e) {
            System.err.println("Erreur lors de l'ajout de l'abonnement pour " + userEmail + ": " + e.getMessage());
        }
    }
    
    public void removeUserSubscription(String userEmail, List<String> symbols) {
        try {
            String userKey = REDIS_USER_SUBSCRIPTIONS_KEY + userEmail;
            String typeKey = REDIS_USER_SUB_TYPE_KEY + userEmail;
            
            @SuppressWarnings("unchecked")
            Set<String> existingSymbols = (Set<String>) redisTemplate.opsForValue().get(userKey);
            
            if (existingSymbols != null) {
                // Supprimer les symboles spécifiés
                symbols.forEach(existingSymbols::remove);
                
                if (existingSymbols.isEmpty()) {
                    // Supprimer complètement l'utilisateur
                    redisTemplate.delete(userKey);
                    redisTemplate.delete(typeKey);
                    userSubscriptions.remove(userEmail);
                    userSubscriptionTypes.remove(userEmail);
                } else {
                    // Mettre à jour avec les symboles restants
                    redisTemplate.opsForValue().set(userKey, existingSymbols, Duration.ofHours(SUBSCRIPTION_TTL_HOURS));
                    userSubscriptions.put(userEmail, existingSymbols);
                }
                
                System.out.println("Abonnement mis à jour pour " + userEmail + ": " + existingSymbols);
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la suppression de l'abonnement pour " + userEmail + ": " + e.getMessage());
        }
    }
    
    public void removeAllUserSubscriptions(String userEmail) {
        try {
            String userKey = REDIS_USER_SUBSCRIPTIONS_KEY + userEmail;
            String typeKey = REDIS_USER_SUB_TYPE_KEY + userEmail;
            
            // Supprimer de Redis
            redisTemplate.delete(userKey);
            redisTemplate.delete(typeKey);
            
            // Supprimer du cache mémoire
            userSubscriptions.remove(userEmail);
            userSubscriptionTypes.remove(userEmail);
            
            System.out.println("Tous les abonnements supprimés pour " + userEmail);
        } catch (Exception e) {
            System.err.println("Erreur lors de la suppression complète pour " + userEmail + ": " + e.getMessage());
        }
    }
    
    public Map<String, Object> getUserSubscriptions(String userEmail) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String userKey = REDIS_USER_SUBSCRIPTIONS_KEY + userEmail;
            String typeKey = REDIS_USER_SUB_TYPE_KEY + userEmail;
            
            @SuppressWarnings("unchecked")
            Set<String> symbols = (Set<String>) redisTemplate.opsForValue().get(userKey);
            String subscriptionType = (String) redisTemplate.opsForValue().get(typeKey);
            
            if (symbols != null) {
                result.put("symbols", symbols);
                result.put("subscription_type", subscriptionType != null ? subscriptionType : "quotes");
                result.put("total_symbols", symbols.size());
                result.put("has_subscriptions", true);
            } else {
                result.put("symbols", new HashSet<>());
                result.put("subscription_type", null);
                result.put("total_symbols", 0);
                result.put("has_subscriptions", false);
            }
            
        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération des abonnements pour " + userEmail + ": " + e.getMessage());
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    public Map<String, Object> getAllSubscriptions() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Rechercher toutes les clés d'abonnements
            Set<String> subscriptionKeys = redisTemplate.keys(REDIS_USER_SUBSCRIPTIONS_KEY + "*");
            
            if (subscriptionKeys != null) {
                Map<String, Object> allSubs = new HashMap<>();
                
                for (String key : subscriptionKeys) {
                    String userEmail = key.replace(REDIS_USER_SUBSCRIPTIONS_KEY, "");
                    Map<String, Object> userSub = getUserSubscriptions(userEmail);
                    allSubs.put(userEmail, userSub);
                }
                
                result.put("subscriptions", allSubs);
                result.put("total_users", allSubs.size());
                
                // Statistiques globales
                long totalSymbolSubscriptions = allSubs.values().stream()
                    .mapToLong(sub -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> subMap = (Map<String, Object>) sub;
                        return (Integer) subMap.getOrDefault("total_symbols", 0);
                    })
                    .sum();
                
                result.put("total_symbol_subscriptions", totalSymbolSubscriptions);
            } else {
                result.put("subscriptions", new HashMap<>());
                result.put("total_users", 0);
                result.put("total_symbol_subscriptions", 0);
            }
            
        } catch (Exception e) {
            System.err.println("Erreur lors de la récupération de tous les abonnements: " + e.getMessage());
            result.put("error", e.getMessage());
        }
        
        return result;
    }
}