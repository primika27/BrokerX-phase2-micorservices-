package com.broker.orderService.infrastructure.repo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.broker.orderService.domain.Order;
import com.broker.orderService.domain.OrderStatus;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer>{

    List<Order> findByClientIdAndStatus(int clientId, OrderStatus status);
    
    @Query("SELECT o FROM Order o WHERE o.clientId = :clientId AND o.status = 'FILLED'")
    List<Order> findFilledOrdersByClientId(@Param("clientId") int clientId);
    
    List<Order> findByClientIdOrderByOrderIdDesc(int clientId);
    
    Order findByOrderId(int orderId);
}
