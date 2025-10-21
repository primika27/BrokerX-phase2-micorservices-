package com.broker.matchingService.repository;

import com.broker.matchingService.model.MatchingOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchingOrderRepository extends JpaRepository<MatchingOrder, Long> {
    List<MatchingOrder> findByStockSymbolAndOrderTypeAndStatusOrderByPriceAscTimestampAsc(String stockSymbol, String orderType, String status);
    List<MatchingOrder> findByStockSymbolAndOrderTypeAndStatusOrderByPriceDescTimestampAsc(String stockSymbol, String orderType, String status);
}