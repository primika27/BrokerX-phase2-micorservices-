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
            int tradeQuantity = newOrder.getQuantity(); // FOK means full quantity

            // Update quantities
            newOrder.setRemainingQuantity(0);
            bestMatch.setRemainingQuantity(bestMatch.getRemainingQuantity() - tradeQuantity);

            // Update statuses
            newOrder.setStatus("FILLED");
            if (bestMatch.getRemainingQuantity() <= 0) {
                bestMatch.setStatus("FILLED");
            } else {
                bestMatch.setStatus("PARTIALLY_FILLED"); // Counter order might be partially filled
            }

            // Save updated orders
            matchingOrderRepository.save(newOrder);
            matchingOrderRepository.save(bestMatch);

            System.out.println("FOK Matched " + tradeQuantity + " shares of " + newOrder.getStockSymbol() +
                               " between new order " + newOrder.getOrderId() +
                               " and existing order " + bestMatch.getOrderId());

            // Publish a Trade event
            String tradeId = java.util.UUID.randomUUID().toString();
            Trade trade = new Trade(
                tradeId,
                newOrder.getOrderType().equals("BUY") ? newOrder.getOrderId() : bestMatch.getOrderId(),
                newOrder.getOrderType().equals("SELL") ? newOrder.getOrderId() : bestMatch.getOrderId(),
                newOrder.getStockSymbol(),
                tradeQuantity,
                bestMatch.getPrice(), // Trade price is the price of the existing order
                java.time.LocalDateTime.now()
            );
            rabbitTemplate.convertAndSend(RabbitMQConfig.MATCHING_QUEUE, trade); // Send to matchingQueue for now
            System.out.println("Published Trade event: " + trade);

        } else {
            // FOK order cannot be fully filled immediately, so it's cancelled
            newOrder.setStatus("CANCELLED");
            newOrder.setRemainingQuantity(0);
            matchingOrderRepository.save(newOrder);
            System.out.println("FOK order " + newOrder.getOrderId() + " for " + newOrder.getQuantity() +
                               " shares of " + newOrder.getStockSymbol() + " cancelled (not fully fillable).");
        }
    }
}