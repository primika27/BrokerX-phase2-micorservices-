package com.broker.orderService.saga;

import com.broker.orderService.domain.Order;
import com.broker.orderService.domain.OrderStatus;
import com.broker.orderService.infrastructure.client.ClientServiceClient;
import com.broker.orderService.infrastructure.client.WalletServiceClient;
import com.broker.orderService.infrastructure.repo.OrderRepository;
import com.broker.orderService.service.OrderMessageProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Order SAGA Orchestrator - Complete Test Suite")
class OrderSagaOrchestratorTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private WalletServiceClient walletServiceClient;

    @Mock
    private ClientServiceClient clientServiceClient;

    @Mock
    private OrderMessageProducer orderMessageProducer;

    @InjectMocks
    private OrderSagaOrchestrator sagaOrchestrator;

    private Order testOrder;
    private final String testClientEmail = "test@example.com";

    @BeforeEach
    void setUp() {
        testOrder = new Order();
        testOrder.setOrderId(1);
        testOrder.setClientId(123);
        testOrder.setSymbol("AAPL");
        testOrder.setPrice(150.0);
        testOrder.setQuantity(10);
        testOrder.setStatus(OrderStatus.PENDING);
        testOrder.setOrderType("BUY");
    }

    // ===== CANCEL ORDER SAGA TESTS =====

    @Test
    @DisplayName("Cancel Order Saga - Success")
    void cancelOrderSaga_Success() {
        // Arrange
        when(orderRepository.findByOrderId(1)).thenReturn(testOrder);
        doNothing().when(orderMessageProducer).sendCancelledOrderToMatchingService(any());
        when(clientServiceClient.getEmailById(testClientEmail, 123))
            .thenReturn(new ResponseEntity<>("test@example.com", HttpStatus.OK));

        // Act
        SagaResult result = sagaOrchestrator.cancelOrderSaga(1, testClientEmail);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(OrderStatus.CANCELLED, testOrder.getStatus());
        verify(orderRepository).save(testOrder);
        verify(orderMessageProducer).sendCancelledOrderToMatchingService(any());
    }

    @Test
    @DisplayName("Cancel Order Saga - Order Not Found")
    void cancelOrderSaga_OrderNotFound() {
        // Arrange
        when(orderRepository.findByOrderId(999)).thenReturn(null);

        // Act
        SagaResult result = sagaOrchestrator.cancelOrderSaga(999, testClientEmail);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Order not found", result.getErrorMessage());
        verify(orderRepository, never()).save(any());
        verify(orderMessageProducer, never()).sendCancelledOrderToMatchingService(any());
    }

    @Test
    @DisplayName("Cancel Order Saga - Already Filled Order")
    void cancelOrderSaga_AlreadyFilled() {
        // Arrange
        testOrder.setStatus(OrderStatus.FILLED);
        when(orderRepository.findByOrderId(1)).thenReturn(testOrder);

        // Act
        SagaResult result = sagaOrchestrator.cancelOrderSaga(1, testClientEmail);

        // Assert
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("cannot be cancelled"));
        verify(orderRepository, never()).save(any());
        verify(orderMessageProducer, never()).sendCancelledOrderToMatchingService(any());
    }

    // ===== MODIFY ORDER SAGA TESTS =====

    @Test
    @DisplayName("Modify Order Saga - Success with Quantity Change Only")
    void modifyOrderSaga_QuantityChangeOnly() {
        // Arrange
        Integer newQuantity = 15;
        double newTotal = testOrder.getPrice() * newQuantity; // 150 * 15 = 2250

        when(orderRepository.findById(1)).thenReturn(Optional.of(testOrder));
        when(walletServiceClient.getBalance(testClientEmail, testClientEmail))
            .thenReturn(new ResponseEntity<>(3000.0, HttpStatus.OK)); // Sufficient balance
        doNothing().when(orderMessageProducer).sendModifiedOrderToMatchingService(any());
        when(clientServiceClient.getEmailById(testClientEmail, 123))
            .thenReturn(new ResponseEntity<>("test@example.com", HttpStatus.OK));

        // Act
        SagaResult result = sagaOrchestrator.modifyOrderSaga(1, testClientEmail, null, newQuantity);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(150.0, testOrder.getPrice()); // Unchanged
        assertEquals(newQuantity, testOrder.getQuantity());
        verify(orderRepository).save(testOrder);
        verify(walletServiceClient).getBalance(testClientEmail, testClientEmail);
        verify(orderMessageProducer).sendModifiedOrderToMatchingService(any());
        assertTrue(result.getExecutedSteps().contains("Balance verified for new order total: " + newTotal));
        assertTrue(result.getExecutedSteps().contains("Order updated: price=150.0, quantity=15"));
    }

    @Test
    @DisplayName("Modify Order Saga - Success with Price Change Only")
    void modifyOrderSaga_PriceChangeOnly() {
        // Arrange
        Double newPrice = 160.0;
        double newTotal = newPrice * testOrder.getQuantity(); // 160 * 10 = 1600

        when(orderRepository.findById(1)).thenReturn(Optional.of(testOrder));
        when(walletServiceClient.getBalance(testClientEmail, testClientEmail))
            .thenReturn(new ResponseEntity<>(2000.0, HttpStatus.OK)); // Sufficient balance
        doNothing().when(orderMessageProducer).sendModifiedOrderToMatchingService(any());
        when(clientServiceClient.getEmailById(testClientEmail, 123))
            .thenReturn(new ResponseEntity<>("test@example.com", HttpStatus.OK));

        // Act
        SagaResult result = sagaOrchestrator.modifyOrderSaga(1, testClientEmail, newPrice, null);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(newPrice, testOrder.getPrice());
        assertEquals(10, testOrder.getQuantity()); // Unchanged
        verify(orderRepository).save(testOrder);
        verify(walletServiceClient).getBalance(testClientEmail, testClientEmail);
        verify(orderMessageProducer).sendModifiedOrderToMatchingService(any());
        assertTrue(result.getExecutedSteps().contains("Balance verified for new order total: " + newTotal));
        assertTrue(result.getExecutedSteps().contains("Order updated: price=" + newPrice + ", quantity=10"));
    }

    @Test
    @DisplayName("Modify Order Saga - Success with Both Price and Quantity Change")
    void modifyOrderSaga_BothChanges() {
        // Arrange
        Double newPrice = 160.0;
        Integer newQuantity = 15;
        double newTotal = newPrice * newQuantity; // 160 * 15 = 2400

        when(orderRepository.findById(1)).thenReturn(Optional.of(testOrder));
        when(walletServiceClient.getBalance(testClientEmail, testClientEmail))
            .thenReturn(new ResponseEntity<>(3000.0, HttpStatus.OK)); // Sufficient balance
        doNothing().when(orderMessageProducer).sendModifiedOrderToMatchingService(any());
        when(clientServiceClient.getEmailById(testClientEmail, 123))
            .thenReturn(new ResponseEntity<>("test@example.com", HttpStatus.OK));

        // Act
        SagaResult result = sagaOrchestrator.modifyOrderSaga(1, testClientEmail, newPrice, newQuantity);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(newPrice, testOrder.getPrice());
        assertEquals(newQuantity, testOrder.getQuantity());
        verify(orderRepository).save(testOrder);
        verify(walletServiceClient).getBalance(testClientEmail, testClientEmail);
        verify(orderMessageProducer).sendModifiedOrderToMatchingService(any());
        assertTrue(result.getExecutedSteps().contains("Balance verified for new order total: " + newTotal));
        assertTrue(result.getExecutedSteps().contains("Order updated: price=" + newPrice + ", quantity=" + newQuantity));
    }

    @Test
    @DisplayName("Modify Order Saga - Insufficient Balance")
    void modifyOrderSaga_InsufficientBalance() {
        // Arrange
        Double newPrice = 200.0;
        Integer newQuantity = 20;
        double newTotal = newPrice * newQuantity; // 200 * 20 = 4000

        when(orderRepository.findById(1)).thenReturn(Optional.of(testOrder));
        when(walletServiceClient.getBalance(testClientEmail, testClientEmail))
            .thenReturn(new ResponseEntity<>(1000.0, HttpStatus.OK)); // Insufficient balance
        when(clientServiceClient.getEmailById(testClientEmail, 123))
            .thenReturn(new ResponseEntity<>("test@example.com", HttpStatus.OK));

        // Act
        SagaResult result = sagaOrchestrator.modifyOrderSaga(1, testClientEmail, newPrice, newQuantity);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Insufficient balance. Required: " + newTotal + ", Available: 1000.0", result.getErrorMessage());
        
        // Order should not be modified
        assertEquals(150.0, testOrder.getPrice()); // Original price unchanged
        assertEquals(10, testOrder.getQuantity()); // Original quantity unchanged
        
        verify(orderRepository, never()).save(testOrder);
        verify(orderMessageProducer, never()).sendModifiedOrderToMatchingService(any());
    }

    @Test
    @DisplayName("Modify Order Saga - Order Not Found")
    void modifyOrderSaga_OrderNotFound() {
        // Arrange
        when(orderRepository.findById(999)).thenReturn(Optional.empty());

        // Act
        SagaResult result = sagaOrchestrator.modifyOrderSaga(999, testClientEmail, 160.0, 15);

        // Assert
        assertFalse(result.isSuccess());
        assertEquals("Order not found", result.getErrorMessage());
        verify(orderRepository, never()).save(any());
        verify(walletServiceClient, never()).getBalance(anyString(), anyString());
        verify(orderMessageProducer, never()).sendModifiedOrderToMatchingService(any());
    }

    @Test
    @DisplayName("Modify Order Saga - Invalid Values")
    void modifyOrderSaga_InvalidValues() {
        // Arrange
        when(orderRepository.findById(1)).thenReturn(Optional.of(testOrder));

        // Act & Assert - negative price
        SagaResult result1 = sagaOrchestrator.modifyOrderSaga(1, testClientEmail, -10.0, null);
        assertFalse(result1.isSuccess());
        assertTrue(result1.getErrorMessage().contains("Invalid"));

        // Act & Assert - zero quantity  
        SagaResult result2 = sagaOrchestrator.modifyOrderSaga(1, testClientEmail, null, 0);
        assertFalse(result2.isSuccess());
        assertTrue(result2.getErrorMessage().contains("Invalid"));

        // Act & Assert - both null
        SagaResult result3 = sagaOrchestrator.modifyOrderSaga(1, testClientEmail, null, null);
        assertFalse(result3.isSuccess());
        assertEquals("Either new price or new quantity must be provided", result3.getErrorMessage());
    }

    @Test
    @DisplayName("Modify Order Saga - Wallet Service Failure")
    void modifyOrderSaga_WalletServiceFailure() {
        // Arrange
        when(orderRepository.findById(1)).thenReturn(Optional.of(testOrder));
        when(walletServiceClient.getBalance(testClientEmail, testClientEmail))
            .thenThrow(new RuntimeException("Wallet service unavailable"));
        when(clientServiceClient.getEmailById(testClientEmail, 123))
            .thenReturn(new ResponseEntity<>("test@example.com", HttpStatus.OK));

        // Act
        SagaResult result = sagaOrchestrator.modifyOrderSaga(1, testClientEmail, 160.0, 15);

        // Assert
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Failed to verify balance"));
        
        // Order should not be modified
        assertEquals(150.0, testOrder.getPrice());
        assertEquals(10, testOrder.getQuantity());
        verify(orderRepository, never()).save(testOrder);
        verify(orderMessageProducer, never()).sendModifiedOrderToMatchingService(any());
    }

    @Test
    @DisplayName("Modify Order Saga - Messaging Service Failure")  
    void modifyOrderSaga_MessagingFailure() {
        // Arrange
        Double newPrice = 160.0;

        when(orderRepository.findById(1)).thenReturn(Optional.of(testOrder));
        when(walletServiceClient.getBalance(testClientEmail, testClientEmail))
            .thenReturn(new ResponseEntity<>(2000.0, HttpStatus.OK));
        doThrow(new RuntimeException("Matching service error")).when(orderMessageProducer)
            .sendModifiedOrderToMatchingService(any());
        when(clientServiceClient.getEmailById(testClientEmail, 123))
            .thenReturn(new ResponseEntity<>("test@example.com", HttpStatus.OK));

        // Act
        SagaResult result = sagaOrchestrator.modifyOrderSaga(1, testClientEmail, newPrice, null);

        // Assert - Should still succeed even if matching service fails
        assertTrue(result.isSuccess()); 
        assertEquals(newPrice, testOrder.getPrice());
        verify(orderRepository).save(testOrder);
        assertTrue(result.getExecutedSteps().contains("Order updated: price=" + newPrice + ", quantity=10"));
    }

    // ===== EDGE CASES AND INTEGRATION TESTS =====

    @Test
    @DisplayName("Modify Order Saga - Very Large Numbers")
    void modifyOrderSaga_LargeNumbers() {
        // Arrange
        Double newPrice = 99999.99;
        Integer newQuantity = Integer.MAX_VALUE / 100000; // Very large but safe quantity
        double newTotal = newPrice * newQuantity;

        when(orderRepository.findByOrderId(1)).thenReturn(testOrder);
        when(walletServiceClient.getBalance(testClientEmail, testClientEmail))
            .thenReturn(new ResponseEntity<>(Double.MAX_VALUE, HttpStatus.OK));
        doNothing().when(orderMessageProducer).sendModifiedOrderToMatchingService(any());
        when(clientServiceClient.getEmailById(testClientEmail, 123))
            .thenReturn(new ResponseEntity<>("test@example.com", HttpStatus.OK));

        // Act
        SagaResult result = sagaOrchestrator.modifyOrderSaga(1, testClientEmail, newPrice, newQuantity);

        // Assert
        assertTrue(result.isSuccess());
        assertEquals(newPrice, testOrder.getPrice());
        assertEquals(newQuantity, testOrder.getQuantity());
        assertTrue(result.getExecutedSteps().contains("Balance verified for new order total: " + newTotal));
    }

    @Test
    @DisplayName("SAGA Integration - Cancel Then Modify Should Fail")
    void sagaIntegration_CancelThenModify() {
        // Arrange - Cancel order first
        when(orderRepository.findByOrderId(1)).thenReturn(testOrder);
        when(clientServiceClient.getEmailById(testClientEmail, 123))
            .thenReturn(new ResponseEntity<>("test@example.com", HttpStatus.OK));
        doNothing().when(orderMessageProducer).sendCancelledOrderToMatchingService(any());

        // Act - Cancel the order
        SagaResult cancelResult = sagaOrchestrator.cancelOrderSaga(1, testClientEmail);
        
        // Verify cancel succeeded
        assertTrue(cancelResult.isSuccess());
        assertEquals(OrderStatus.CANCELLED, testOrder.getStatus());

        // Act - Try to modify cancelled order (should fail)
        when(orderRepository.findById(1)).thenReturn(Optional.of(testOrder)); 
        SagaResult modifyResult = sagaOrchestrator.modifyOrderSaga(1, testClientEmail, 160.0, 15);

        // Assert - Modification should fail
        assertFalse(modifyResult.isSuccess());
        assertTrue(modifyResult.getErrorMessage().contains("cannot be modified"));
        verify(walletServiceClient, never()).getBalance(anyString(), anyString());
        verify(orderMessageProducer, never()).sendModifiedOrderToMatchingService(any());
    }
}