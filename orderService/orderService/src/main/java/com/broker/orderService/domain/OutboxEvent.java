package com.broker.orderService.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, name = "aggregate_id")
    private String aggregateId; // orderId or sagaId
    
    @Column(nullable = false, name = "event_type")
    private String eventType; // OrderSubmitted, FundsReserved, etc.
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // JSON payload
    
    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "retry_count")
    private Integer retryCount = 0;
    
    @Column(name = "max_retries")
    private Integer maxRetries = 5;
    
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    // Constructeur par défaut pour JPA
    public OutboxEvent() {
        this.createdAt = LocalDateTime.now();
    }

    // Constructeur principal
    public OutboxEvent(String aggregateId, String eventType, String payload) {
        this();
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
    }

    // Méthodes utilitaires
    public boolean isProcessed() {
        return processedAt != null;
    }
    
    public void markAsProcessed() {
        this.processedAt = LocalDateTime.now();
    }
    
    public void incrementRetryCount() {
        this.retryCount++;
        // Exponential backoff: 1min, 2min, 4min, 8min, 16min
        long delayMinutes = (long) Math.pow(2, retryCount);
        this.nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
    }
    
    public boolean canRetry() {
        return retryCount < maxRetries && 
               (nextRetryAt == null || LocalDateTime.now().isAfter(nextRetryAt));
    }

    // Getters et Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    @Override
    public String toString() {
        return "OutboxEvent{" +
                "id=" + id +
                ", aggregateId='" + aggregateId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", createdAt=" + createdAt +
                ", processedAt=" + processedAt +
                ", retryCount=" + retryCount +
                '}';
    }
}
