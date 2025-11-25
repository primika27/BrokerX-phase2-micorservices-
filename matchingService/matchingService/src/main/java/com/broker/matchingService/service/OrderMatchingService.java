package com.broker.matchingService.service;

import com.broker.matchingService.dto.Trade;
import com.broker.matchingService.model.MatchingOrder;
import com.broker.matchingService.repository.MatchingOrderRepository;
import com.broker.matchingService.config.RabbitMQConfig;
import com.broker.matchingService.dto.OrderDto;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class OrderMatchingService {

    private final MatchingOrderRepository matchingOrderRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    
    // Map to store scheduled trades by order ID so they can be cancelled
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledTrades = new ConcurrentHashMap<>();

    public OrderMatchingService(MatchingOrderRepository matchingOrderRepository, RabbitTemplate rabbitTemplate) {
        this.matchingOrderRepository = matchingOrderRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public void processNewOrder(OrderDto newOrderDto) {
        System.out.println("Processing new order for matching: " + newOrderDto);

        // Convert DTO to Entity and save to order book
        MatchingOrder newMatchingOrder = new MatchingOrder(
            newOrderDto.getOrderId(),
            newOrderDto.getStockSymbol(),
            newOrderDto.getQuantity(),
            newOrderDto.getPrice(),
            newOrderDto.getOrderType()
        );
        matchingOrderRepository.save(newMatchingOrder);

        // Attempt to match the new order
        matchOrder(newMatchingOrder);
    }

    private void matchOrder(MatchingOrder newOrder) {
        String counterOrderType = newOrder.getOrderType().equals("BUY") ? "SELL" : "BUY";

        List<MatchingOrder> potentialMatches;

        if (newOrder.getOrderType().equals("BUY")) {
            // For a BUY order, look for SELL orders at or below the BUY price
            // Ordered by price ascending (cheapest first), then time ascending (FIFO)
            potentialMatches = matchingOrderRepository.findByStockSymbolAndOrderTypeAndStatusOrderByPriceAscTimestampAsc(
                newOrder.getStockSymbol(),
                counterOrderType,
                "PENDING" // Only match with pending orders
            );
        } else { // SELL order
            // For a SELL order, look for BUY orders at or above the SELL price
            // Ordered by price descending (highest bid first), then time ascending (FIFO)
            potentialMatches = matchingOrderRepository.findByStockSymbolAndOrderTypeAndStatusOrderByPriceDescTimestampAsc(
                newOrder.getStockSymbol(),
                counterOrderType,
                "PENDING" // Only match with pending orders
            );
        }

        int totalMatchedQuantity = 0;
        MatchingOrder bestMatch = null;

        // Simple FOK: Find a single counter-order that can fully fill the new order
        for (MatchingOrder existingOrder : potentialMatches) {
            boolean priceMatches = false;
            if (newOrder.getOrderType().equals("BUY") && newOrder.getPrice() >= existingOrder.getPrice()) {
                priceMatches = true; // Buy order can match sell order at or below its price
            } else if (newOrder.getOrderType().equals("SELL") && newOrder.getPrice() <= existingOrder.getPrice()) {
                priceMatches = true; // Sell order can match buy order at or above its price
            }

            if (priceMatches && existingOrder.getRemainingQuantity() >= newOrder.getQuantity()) {
                bestMatch = existingOrder;
                break; // Found a single order that can fill the FOK order
            }
        }

        if (bestMatch != null) {
            // Execute FOK trade
            executeTrade(newOrder, bestMatch, newOrder.getQuantity());

        } else {
            // No existing counter-order found, create a synthetic market maker order for demo purposes
            System.out.println("No counter-order found for " + newOrder.getOrderId() + 
                             ", creating synthetic market maker order for immediate matching");
            
            MatchingOrder marketMakerOrder = createSyntheticCounterOrder(newOrder);
            if (marketMakerOrder != null) {
                matchingOrderRepository.save(marketMakerOrder);
                executeTrade(newOrder, marketMakerOrder, newOrder.getQuantity());
            } else {
                // Fallback: cancel the order
                newOrder.setStatus("CANCELLED");
                newOrder.setRemainingQuantity(0);
                matchingOrderRepository.save(newOrder);
                System.out.println("Order " + newOrder.getOrderId() + " cancelled (no counter-order available).");
            }
        }
    }

    // Create a synthetic counter-order for market making (demo purposes)
    private MatchingOrder createSyntheticCounterOrder(MatchingOrder originalOrder) {
        try {
            String counterOrderType = originalOrder.getOrderType().equals("BUY") ? "SELL" : "BUY";
            
            // Create synthetic market maker order at the same price
            MatchingOrder marketMakerOrder = new MatchingOrder(
                "MARKET_MAKER_" + System.currentTimeMillis(), // Synthetic order ID
                originalOrder.getStockSymbol(),
                originalOrder.getQuantity(), // Same quantity to ensure full fill
                originalOrder.getPrice(), // Match at the requested price
                counterOrderType
            );
            
            marketMakerOrder.setStatus("PENDING");
            marketMakerOrder.setRemainingQuantity(originalOrder.getQuantity());
            
            System.out.println("Created synthetic market maker order: " + marketMakerOrder.getOrderId() +
                             " to match against " + originalOrder.getOrderId());
            
            return marketMakerOrder;
        } catch (Exception e) {
            System.err.println("Error creating synthetic counter-order: " + e.getMessage());
            return null;
        }
    }

    // Execute trade between two orders with delay for demo
    private void executeTrade(MatchingOrder order1, MatchingOrder order2, int tradeQuantity) {
        try {
            System.out.println("Trade will be executed in 10 seconds: " + tradeQuantity + " shares of " + order1.getStockSymbol() +
                             " between order " + order1.getOrderId() + 
                             " and order " + order2.getOrderId());
            
            // Schedule the trade execution with ability to cancel
            ScheduledFuture<?> scheduledTrade = scheduler.schedule(() -> {
                try {
                    // Check if orders are still PENDING before executing
                    MatchingOrder currentOrder1 = matchingOrderRepository.findByOrderId(order1.getOrderId());
                    MatchingOrder currentOrder2 = matchingOrderRepository.findByOrderId(order2.getOrderId());
                    
                    if (currentOrder1 == null || !"PENDING".equals(currentOrder1.getStatus())) {
                        System.out.println("Order " + order1.getOrderId() + " is no longer PENDING, cancelling trade");
                        return;
                    }
                    
                    if (currentOrder2 == null || !"PENDING".equals(currentOrder2.getStatus())) {
                        System.out.println("Order " + order2.getOrderId() + " is no longer PENDING, cancelling trade");
                        return;
                    }
                    
                    // Update quantities
                    currentOrder1.setRemainingQuantity(0);
                    currentOrder2.setRemainingQuantity(currentOrder2.getRemainingQuantity() - tradeQuantity);

                    // Update statuses
                    currentOrder1.setStatus("FILLED");
                    if (currentOrder2.getRemainingQuantity() <= 0) {
                        currentOrder2.setStatus("FILLED");
                    } else {
                        currentOrder2.setStatus("PARTIALLY_FILLED");
                    }

                    // Save updated orders
                    matchingOrderRepository.save(currentOrder1);
                    matchingOrderRepository.save(currentOrder2);

                    System.out.println("Trade executed after delay: " + tradeQuantity + " shares of " + currentOrder1.getStockSymbol() +
                                     " between order " + currentOrder1.getOrderId() + 
                                     " and order " + currentOrder2.getOrderId());

                    // Determine buy/sell order IDs for the trade
                    String buyOrderId = currentOrder1.getOrderType().equals("BUY") ? currentOrder1.getOrderId() : currentOrder2.getOrderId();
                    String sellOrderId = currentOrder1.getOrderType().equals("SELL") ? currentOrder1.getOrderId() : currentOrder2.getOrderId();
                    
                    // Use the price from the existing order (order2 in most cases, order1 if it's synthetic)
                    double tradePrice = currentOrder2.getOrderId().startsWith("MARKET_MAKER_") ? currentOrder1.getPrice() : currentOrder2.getPrice();

                    // Publish a Trade event
                    String tradeId = java.util.UUID.randomUUID().toString();
                    Trade trade = new Trade(
                        tradeId,
                        buyOrderId,
                        sellOrderId,
                        currentOrder1.getStockSymbol(),
                        tradeQuantity,
                        tradePrice,
                        java.time.LocalDateTime.now()
                    );
                    
                    rabbitTemplate.convertAndSend(RabbitMQConfig.MATCHING_QUEUE, trade);
                    System.out.println("Published Trade event after delay: " + trade);
                    
                    // Remove from scheduled trades map
                    scheduledTrades.remove(order1.getOrderId());
                    scheduledTrades.remove(order2.getOrderId());
                    
                } catch (Exception e) {
                    System.err.println("Error executing delayed trade: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 10, TimeUnit.SECONDS);
            
            // Store the scheduled future so it can be cancelled if needed
            scheduledTrades.put(order1.getOrderId(), scheduledTrade);
            scheduledTrades.put(order2.getOrderId(), scheduledTrade);
            
        } catch (Exception e) {
            System.err.println("Error setting up delayed trade execution: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Cancel an order in the matching engine
     */
    @Transactional
    public void cancelOrder(String orderId) {
        try {
            MatchingOrder order = matchingOrderRepository.findByOrderId(orderId);
            if (order != null && "PENDING".equals(order.getStatus())) {
                order.setStatus("CANCELLED");
                matchingOrderRepository.save(order);
                System.out.println("Order " + orderId + " cancelled in matching engine");
                
                // Cancel any scheduled trades for this order
                ScheduledFuture<?> scheduledTrade = scheduledTrades.remove(orderId);
                if (scheduledTrade != null && !scheduledTrade.isDone()) {
                    boolean cancelled = scheduledTrade.cancel(false);
                    System.out.println("Scheduled trade for order " + orderId + " cancelled: " + cancelled);
                }
                
            } else if (order == null) {
                System.out.println("Order " + orderId + " not found in matching engine (may have already been matched)");
            } else {
                System.out.println("Order " + orderId + " cannot be cancelled (status: " + order.getStatus() + ")");
            }
        } catch (Exception e) {
            System.err.println("Error cancelling order " + orderId + ": " + e.getMessage());
        }
    }
    
    /**
     * Modify an existing order in the matching engine
     */
    @Transactional
    public void modifyOrder(OrderDto modifiedOrderDto) {
        try {
            MatchingOrder existingOrder = matchingOrderRepository.findByOrderId(modifiedOrderDto.getOrderId());
            if (existingOrder != null && "PENDING".equals(existingOrder.getStatus())) {
                // IMPORTANT: First cancel any existing scheduled trade for this order
                ScheduledFuture<?> scheduledTrade = scheduledTrades.remove(existingOrder.getOrderId());
                if (scheduledTrade != null && !scheduledTrade.isDone()) {
                    boolean cancelled = scheduledTrade.cancel(false);
                    System.out.println("Cancelled existing scheduled trade for order " + existingOrder.getOrderId() + ": " + cancelled);
                }
                
                // Update order details
                existingOrder.setQuantity(modifiedOrderDto.getQuantity());
                existingOrder.setPrice(modifiedOrderDto.getPrice());
                existingOrder.setRemainingQuantity(modifiedOrderDto.getQuantity());
                matchingOrderRepository.save(existingOrder);
                System.out.println("Order " + modifiedOrderDto.getOrderId() + " modified in matching engine to price=" + 
                                 modifiedOrderDto.getPrice() + ", quantity=" + modifiedOrderDto.getQuantity());
                
                // Try to match the modified order (this will create a new scheduled trade if needed)
                matchOrder(existingOrder);
            } else if (existingOrder == null) {
                System.out.println("Order " + modifiedOrderDto.getOrderId() + " not found in matching engine");
            } else {
                System.out.println("Order " + modifiedOrderDto.getOrderId() + " cannot be modified (status: " + existingOrder.getStatus() + ")");
            }
        } catch (Exception e) {
            System.err.println("Error modifying order " + modifiedOrderDto.getOrderId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}