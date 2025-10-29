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

@Service
public class OrderMatchingService {

    private final MatchingOrderRepository matchingOrderRepository;
    private final RabbitTemplate rabbitTemplate;

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
            
            // Create a separate thread to handle the delayed execution
            new Thread(() -> {
                try {
                    // Wait for 10 seconds before executing the trade
                    Thread.sleep(10000);
                    
                    // Update quantities
                    order1.setRemainingQuantity(0);
                    order2.setRemainingQuantity(order2.getRemainingQuantity() - tradeQuantity);

                    // Update statuses
                    order1.setStatus("FILLED");
                    if (order2.getRemainingQuantity() <= 0) {
                        order2.setStatus("FILLED");
                    } else {
                        order2.setStatus("PARTIALLY_FILLED");
                    }

                    // Save updated orders
                    matchingOrderRepository.save(order1);
                    matchingOrderRepository.save(order2);

                    System.out.println("Trade executed after delay: " + tradeQuantity + " shares of " + order1.getStockSymbol() +
                                     " between order " + order1.getOrderId() + 
                                     " and order " + order2.getOrderId());

                    // Determine buy/sell order IDs for the trade
                    String buyOrderId = order1.getOrderType().equals("BUY") ? order1.getOrderId() : order2.getOrderId();
                    String sellOrderId = order1.getOrderType().equals("SELL") ? order1.getOrderId() : order2.getOrderId();
                    
                    // Use the price from the existing order (order2 in most cases, order1 if it's synthetic)
                    double tradePrice = order2.getOrderId().startsWith("MARKET_MAKER_") ? order1.getPrice() : order2.getPrice();

                    // Publish a Trade event
                    String tradeId = java.util.UUID.randomUUID().toString();
                    Trade trade = new Trade(
                        tradeId,
                        buyOrderId,
                        sellOrderId,
                        order1.getStockSymbol(),
                        tradeQuantity,
                        tradePrice,
                        java.time.LocalDateTime.now()
                    );
                    
                    rabbitTemplate.convertAndSend(RabbitMQConfig.MATCHING_QUEUE, trade);
                    System.out.println("Published Trade event after delay: " + trade);
                    
                } catch (InterruptedException e) {
                    System.err.println("Trade execution was interrupted: " + e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("Error executing delayed trade: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
            
        } catch (Exception e) {
            System.err.println("Error setting up delayed trade execution: " + e.getMessage());
            e.printStackTrace();
        }
    }
}