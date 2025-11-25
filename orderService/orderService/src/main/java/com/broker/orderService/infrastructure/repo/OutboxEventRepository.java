package com.broker.orderService.infrastructure.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.broker.orderService.domain.OutboxEvent;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // Trouver les événements non traités qui peuvent être tentés à nouveau
    @Query("SELECT e FROM OutboxEvent e WHERE e.processedAt IS NULL AND e.retryCount < e.maxRetries AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now)")
    List<OutboxEvent> findUnprocessedEventsReadyForRetry(@Param("now") LocalDateTime now);
    
    // Trouver les événements par aggregate ID (pour le suivi)
    List<OutboxEvent> findByAggregateIdOrderByCreatedAtAsc(String aggregateId);
    
    // Trouver les événements par type
    List<OutboxEvent> findByEventTypeOrderByCreatedAtDesc(String eventType);
    
    // Nettoyer les anciens événements traités (plus de 7 jours)
    @Query("SELECT e FROM OutboxEvent e WHERE e.processedAt IS NOT NULL AND e.processedAt < :cutoffDate")
    List<OutboxEvent> findOldProcessedEvents(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // Compter les événements en échec (max retries atteint)
    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.processedAt IS NULL AND e.retryCount >= e.maxRetries")
    long countFailedEvents();
}