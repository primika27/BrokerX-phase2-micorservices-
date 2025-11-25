package com.broker.orderService.service;

import com.broker.orderService.domain.OutboxEvent;
import com.broker.orderService.domain.Order;
import com.broker.orderService.dto.OrderDto;
import com.broker.orderService.infrastructure.repo.OutboxEventRepository;
import com.broker.orderService.infrastructure.repo.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class OutboxService {
    
    private final OutboxEventRepository outboxEventRepository;
    private final OrderRepository orderRepository;
    private final OrderMessageProducer orderMessageProducer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OutboxService(OutboxEventRepository outboxEventRepository,
                        OrderRepository orderRepository,
                        OrderMessageProducer orderMessageProducer) {
        this.outboxEventRepository = outboxEventRepository;
        this.orderRepository = orderRepository;
        this.orderMessageProducer = orderMessageProducer;
    }

    /**
     * Publier un événement saga dans l'outbox (transactionnel)
     * Utilisé par le saga orchestrator pour persister les événements
     */
    @Transactional
    public void publishSagaEvent(String sagaId, String eventType, Map<String, Object> eventData) {
        try {
            String payloadJson = objectMapper.writeValueAsString(eventData);
            OutboxEvent event = new OutboxEvent(sagaId, eventType, payloadJson);
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish saga event", e);
        }
    }

    /**
     * Publier un événement d'annulation d'ordre via saga
     */
    @Transactional
    public void publishOrderCancelledBySaga(int orderId, String clientEmail, String sagaId) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", orderId);
        eventData.put("clientEmail", clientEmail);
        eventData.put("sagaId", sagaId);
        eventData.put("action", "SAGA_CANCEL");
        
        publishSagaEvent(sagaId, "SagaOrderCancelled", eventData);
    }

    /**
     * Publier un événement de modification d'ordre via saga
     */
    @Transactional
    public void publishOrderModifiedBySaga(int orderId, String clientEmail, String sagaId, 
                                          Double newPrice, Integer newQuantity) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("orderId", orderId);
        eventData.put("clientEmail", clientEmail);
        eventData.put("sagaId", sagaId);
        eventData.put("action", "SAGA_MODIFY");
        if (newPrice != null) eventData.put("newPrice", newPrice);
        if (newQuantity != null) eventData.put("newQuantity", newQuantity);
        
        publishSagaEvent(sagaId, "SagaOrderModified", eventData);
    }

    /**
     * Traitement automatique des événements outbox saga
     */
    @Scheduled(fixedDelay = 5000)
    public void processOutboxEvents() {
        List<OutboxEvent> events = outboxEventRepository.findUnprocessedEventsReadyForRetry(LocalDateTime.now());
        
        if (!events.isEmpty()) {
            System.out.println("Processing " + events.size() + " outbox events");
        }
        
        for (OutboxEvent event : events) {
            try {
                System.out.println("Processing event: " + event.getEventType() + " for aggregate: " + event.getAggregateId());
                processEvent(event);
                event.markAsProcessed();
                outboxEventRepository.save(event);
                System.out.println("Event processed successfully: " + event.getId());
            } catch (Exception e) {
                System.err.println("Failed to process event " + event.getId() + ": " + e.getMessage());
                e.printStackTrace();
                event.incrementRetryCount();
                outboxEventRepository.save(event);
            }
        }
    }

    // Debug methods
    public long getTotalEventsCount() {
        return outboxEventRepository.count();
    }

    public long getUnprocessedEventsCount() {
        return outboxEventRepository.findUnprocessedEventsReadyForRetry(LocalDateTime.now()).size();
    }

    public List<OutboxEvent> getRecentEvents(int limit) {
        return outboxEventRepository.findAll().stream()
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(limit)
            .toList();
    }

    @SuppressWarnings("unchecked")
    private void processEvent(OutboxEvent event) throws Exception {
        Map<String, Object> eventData = objectMapper.readValue(event.getPayload(), Map.class);
        
        if ("SagaOrderCancelled".equals(event.getEventType()) || "SagaOrderModified".equals(event.getEventType())) {
            // Fix type casting issue
            Object orderIdObj = eventData.get("orderId");
            Integer orderId;
            if (orderIdObj instanceof String) {
                orderId = Integer.parseInt((String) orderIdObj);
            } else {
                orderId = (Integer) orderIdObj;
            }
            
            Order order = orderRepository.findByOrderId(orderId);
            
            if (order != null) {
                // Créer OrderDto pour RabbitMQ
                OrderDto orderDto = new OrderDto();
                orderDto.setOrderId(String.valueOf(orderId));
                orderDto.setStockSymbol(order.getSymbol());
                orderDto.setQuantity(order.getQuantity());
                orderDto.setPrice(order.getPrice());
                orderDto.setOrderType(order.getOrderType());
                orderDto.setStatus(order.getStatus().toString());

                // Envoyer à MatchingService via RabbitMQ
                if ("SagaOrderCancelled".equals(event.getEventType())) {
                    orderMessageProducer.sendCancelledOrderToMatchingService(orderDto);
                } else {
                    orderMessageProducer.sendModifiedOrderToMatchingService(orderDto);
                }
                
                System.out.println("Successfully processed outbox event: " + event.getEventType() + " for order " + orderId);
            } else {
                System.err.println("Order not found for outbox event: " + orderId);
            }
        }
    }
}
