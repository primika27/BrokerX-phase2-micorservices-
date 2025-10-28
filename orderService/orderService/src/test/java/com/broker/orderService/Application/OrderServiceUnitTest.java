package com.broker.orderService.Application;

import com.broker.orderService.domain.Order;
import com.broker.orderService.domain.OrderStatus;
import com.broker.orderService.infrastructure.client.ClientServiceClient;
import com.broker.orderService.infrastructure.client.WalletServiceClient;
import com.broker.orderService.infrastructure.repo.OrderRepository;
import com.broker.orderService.infrastructure.repo.TransactionRepository;
import com.broker.orderService.service.OrderMessageProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService - Unit Tests")
class OrderServiceUnitTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletServiceClient walletServiceClient;

    @Mock
    private ClientServiceClient clientServiceClient;

    @Mock
    private OrderMessageProducer orderMessageProducer;

    @InjectMocks
    private OrderService orderService;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        testOrder = new Order();
        testOrder.setOrderId(1);
        testOrder.setClientId(100);
        testOrder.setSymbol("AAPL");
        testOrder.setPrice(150.0);
        testOrder.setQuantity(10);
        testOrder.setOrderType("BUY");
        testOrder.setStatus(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Should successfully place buy order with sufficient balance")
    void testAcheterActionSuccess() {
        // Given
        when(walletServiceClient.getBalance(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(2000.0));
        when(clientServiceClient.getByEmail(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(100));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // When
        boolean result = orderService.acheterAction("test@example.com", "AAPL", 150.0, 10);

        // Then
        assertTrue(result);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(orderMessageProducer, times(1)).sendNewOrderToMatchingService(any());
    }

    @Test
    @DisplayName("Should reject order with insufficient balance")
    void testAcheterActionInsufficientBalance() {
        // Given
        when(walletServiceClient.getBalance(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(500.0)); // Not enough for 10 shares at 150

        // When
        boolean result = orderService.acheterAction("test@example.com", "AAPL", 150.0, 10);

        // Then
        assertFalse(result);
        verify(orderRepository, never()).save(any(Order.class));
        verify(orderMessageProducer, never()).sendNewOrderToMatchingService(any());
    }

    @Test
    @DisplayName("Should reject order when client not found")
    void testAcheterActionClientNotFound() {
        // Given
        when(walletServiceClient.getBalance(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(2000.0));
        when(clientServiceClient.getByEmail(anyString(), anyString()))
                .thenReturn(ResponseEntity.notFound().build());

        // When
        boolean result = orderService.acheterAction("test@example.com", "AAPL", 150.0, 10);

        // Then
        assertFalse(result);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should get holdings by client email")
    void testGetHoldings() {
        // Given
        when(clientServiceClient.getByEmail(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(100));

        // When
        Map<String, Object> holdings = orderService.getHoldings("test@example.com");

        // Then
        assertNotNull(holdings);
        verify(clientServiceClient, times(1)).getByEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle sell action")
    void testVendreAction() {
        // Given
        when(walletServiceClient.getBalance(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(2000.0));
        when(clientServiceClient.getByEmail(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(100));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // When
        boolean result = orderService.vendreAction("test@example.com", "AAPL", 150.0, 10);

        // Then
        assertTrue(result);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(orderMessageProducer, times(1)).sendNewOrderToMatchingService(any());
    }
}
