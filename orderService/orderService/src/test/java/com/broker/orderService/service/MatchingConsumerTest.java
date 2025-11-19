package com.broker.orderService.service;

import com.broker.orderService.domain.Order;
import com.broker.orderService.domain.OrderStatus;
import com.broker.orderService.dto.Trade;
import com.broker.orderService.infrastructure.client.ClientServiceClient;
import com.broker.orderService.infrastructure.client.WalletServiceClient;
import com.broker.orderService.infrastructure.repo.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchingConsumer - Wallet Calculation Tests")
class MatchingConsumerTestFixed {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private WalletServiceClient walletServiceClient;

    @Mock
    private ClientServiceClient clientServiceClient;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MatchingConsumer matchingConsumer;

    private Order buyOrder;
    private Order sellOrder;
    private Trade trade;

    @BeforeEach
    void setUp() {
        // Set up a buy order
        buyOrder = new Order();
        buyOrder.setOrderId(1);
        buyOrder.setClientId(123);
        buyOrder.setSymbol("AAPL");
        buyOrder.setPrice(150.0);
        buyOrder.setQuantity(10);
        buyOrder.setStatus(OrderStatus.PENDING);
        buyOrder.setOrderType("BUY");

        // Set up a sell order
        sellOrder = new Order();
        sellOrder.setOrderId(2);
        sellOrder.setClientId(456);
        sellOrder.setSymbol("AAPL");
        sellOrder.setPrice(149.0);
        sellOrder.setQuantity(5);
        sellOrder.setStatus(OrderStatus.PENDING);
        sellOrder.setOrderType("SELL");

        // Set up a trade
        trade = new Trade();
        trade.setTradeId("trade-123");
        trade.setBuyOrderId("1");
        trade.setSellOrderId("2");
        trade.setStockSymbol("AAPL");
        trade.setQuantity(5);
        trade.setPrice(149.5); // Market price different from order price
        trade.setTimestamp(LocalDateTime.now());
    }

    @Test
    @DisplayName("Buy Order Processing - Uses Order Price And Quantity")
    void receiveTrade_BuyOrder_UsesOrderPriceAndQuantity() {
        // Arrange
        when(orderRepository.findById(1)).thenReturn(Optional.of(buyOrder));
        when(orderRepository.findById(2)).thenReturn(Optional.of(sellOrder));
        when(clientServiceClient.getEmailById("service-call", 123))
            .thenReturn(new ResponseEntity<>("buyer@test.com", HttpStatus.OK));
        when(clientServiceClient.getEmailById("service-call", 456))
            .thenReturn(new ResponseEntity<>("seller@test.com", HttpStatus.OK));
        when(walletServiceClient.walletTransaction(anyString(), anyString(), anyDouble(), anyString()))
            .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        // Act
        matchingConsumer.receiveTrade(trade);

        // Assert - Verify wallet debit uses order price AND quantity
        double expectedDebit = buyOrder.getPrice() * buyOrder.getQuantity(); // 150.0 * 10 = 1500.0
        verify(walletServiceClient).walletTransaction(
            "buyer@test.com", 
            "buyer@test.com", 
            expectedDebit, 
            "DEBIT"
        );

        // Verify order status updated
        assertEquals(OrderStatus.FILLED, buyOrder.getStatus());
        verify(orderRepository).save(buyOrder);
    }

    @Test
    @DisplayName("Buy Order Processing - Modified Quantity Calculation")
    void receiveTrade_ModifiedQuantity_CorrectCalculation() {
        // Arrange - Simulate a modified order with updated quantity and price
        Order modifiedBuyOrder = new Order();
        modifiedBuyOrder.setOrderId(1);
        modifiedBuyOrder.setClientId(123);
        modifiedBuyOrder.setSymbol("AAPL");
        modifiedBuyOrder.setPrice(160.0); // Modified price
        modifiedBuyOrder.setQuantity(15);  // Modified quantity
        modifiedBuyOrder.setStatus(OrderStatus.PENDING);
        modifiedBuyOrder.setOrderType("BUY");

        when(orderRepository.findById(1)).thenReturn(Optional.of(modifiedBuyOrder));
        when(orderRepository.findById(2)).thenReturn(Optional.of(sellOrder));
        when(clientServiceClient.getEmailById("service-call", 123))
            .thenReturn(new ResponseEntity<>("buyer@test.com", HttpStatus.OK));
        when(clientServiceClient.getEmailById("service-call", 456))
            .thenReturn(new ResponseEntity<>("seller@test.com", HttpStatus.OK));
        when(walletServiceClient.walletTransaction(anyString(), anyString(), anyDouble(), anyString()))
            .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        // Act
        matchingConsumer.receiveTrade(trade);

        // Assert - Verify wallet debit uses MODIFIED order price and quantity
        double expectedDebit = modifiedBuyOrder.getPrice() * modifiedBuyOrder.getQuantity(); // 160.0 * 15 = 2400.0
        verify(walletServiceClient).walletTransaction(
            "buyer@test.com", 
            "buyer@test.com", 
            expectedDebit, 
            "DEBIT"
        );

        assertEquals(OrderStatus.FILLED, modifiedBuyOrder.getStatus());
        verify(orderRepository).save(modifiedBuyOrder);
    }

    @Test
    @DisplayName("Market Maker Trade - Handles Synthetic Orders")
    void receiveTrade_MarketMaker_HandlesCorrectly() {
        // Arrange - Market maker trade where sell order ID starts with MARKET_MAKER_
        trade.setSellOrderId("MARKET_MAKER_123456");
        
        when(orderRepository.findById(1)).thenReturn(Optional.of(buyOrder));
        when(clientServiceClient.getEmailById("service-call", 123))
            .thenReturn(new ResponseEntity<>("buyer@test.com", HttpStatus.OK));
        when(walletServiceClient.walletTransaction(anyString(), anyString(), anyDouble(), anyString()))
            .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        // Act
        matchingConsumer.receiveTrade(trade);

        // Assert - Only buy order should be processed
        verify(orderRepository).findById(1);
        verify(orderRepository, never()).findById(2); // Should not try to find market maker order
        
        double expectedDebit = buyOrder.getPrice() * buyOrder.getQuantity(); // 150.0 * 10 = 1500.0
        verify(walletServiceClient).walletTransaction(
            "buyer@test.com", 
            "buyer@test.com", 
            expectedDebit, 
            "DEBIT"
        );
        
        // No credit transaction should happen for market maker
        verify(walletServiceClient, never()).walletTransaction(anyString(), anyString(), anyDouble(), eq("CREDIT"));
    }

    @Test
    @DisplayName("Wallet Service Failure - Order Still Marked as Filled")
    void receiveTrade_WalletFailure_OrderStillFilled() {
        // Arrange
        when(orderRepository.findById(1)).thenReturn(Optional.of(buyOrder));
        when(clientServiceClient.getEmailById("service-call", 123))
            .thenReturn(new ResponseEntity<>("buyer@test.com", HttpStatus.OK));
        when(walletServiceClient.walletTransaction(anyString(), anyString(), anyDouble(), eq("DEBIT")))
            .thenThrow(new RuntimeException("Wallet service down"));

        // Act
        matchingConsumer.receiveTrade(trade);

        // Assert - Order should still be marked as filled despite wallet failure
        assertEquals(OrderStatus.FILLED, buyOrder.getStatus());
        verify(orderRepository).save(buyOrder);
    }

    @Test
    @DisplayName("Client Service Failure - Graceful Handling")
    void receiveTrade_ClientServiceFailure_GracefulHandling() {
        // Arrange
        when(orderRepository.findById(1)).thenReturn(Optional.of(buyOrder));
        when(clientServiceClient.getEmailById("service-call", 123))
            .thenThrow(new RuntimeException("Client service down"));

        // Act
        matchingConsumer.receiveTrade(trade);

        // Assert - Should handle gracefully, no wallet operations attempted
        verify(walletServiceClient, never()).walletTransaction(anyString(), anyString(), anyDouble(), anyString());
        
        // Order should still be updated to FILLED
        assertEquals(OrderStatus.FILLED, buyOrder.getStatus());
        verify(orderRepository).save(buyOrder);
    }

    @Test
    @DisplayName("Order Refresh - Ensures Latest Data from Database")
    void receiveTrade_OrderRefresh_UsesLatestData() {
        // Arrange - Simulate database refresh scenario
        Order originalOrder = new Order();
        originalOrder.setOrderId(1);
        originalOrder.setPrice(100.0); // Old price
        originalOrder.setQuantity(5);  // Old quantity
        
        Order updatedOrder = new Order();
        updatedOrder.setOrderId(1);
        updatedOrder.setClientId(123);
        updatedOrder.setSymbol("AAPL");
        updatedOrder.setPrice(200.0); // New price after modification
        updatedOrder.setQuantity(20); // New quantity after modification
        updatedOrder.setStatus(OrderStatus.PENDING);
        updatedOrder.setOrderType("BUY");

        // First call returns original, second call (refresh) returns updated
        when(orderRepository.findById(1))
            .thenReturn(Optional.of(originalOrder))
            .thenReturn(Optional.of(updatedOrder));
        
        when(clientServiceClient.getEmailById("service-call", 123))
            .thenReturn(new ResponseEntity<>("buyer@test.com", HttpStatus.OK));
        when(walletServiceClient.walletTransaction(anyString(), anyString(), anyDouble(), anyString()))
            .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        // Act
        matchingConsumer.receiveTrade(trade);

        // Assert - Should use updated values for wallet calculation
        double expectedDebit = updatedOrder.getPrice() * updatedOrder.getQuantity(); // 200.0 * 20 = 4000.0
        verify(walletServiceClient).walletTransaction(
            "buyer@test.com", 
            "buyer@test.com", 
            expectedDebit, 
            "DEBIT"
        );

        // Verify order was refreshed from database
        verify(orderRepository, times(2)).findById(1);
    }

    @Test
    @DisplayName("Trade Price vs Order Price - Correct Usage")
    void receiveTrade_PriceComparison_CorrectUsage() {
        // Arrange - Set different prices to verify which is used
        buyOrder.setPrice(100.0);  // Order price
        trade.setPrice(95.0);      // Trade price (lower)
        
        when(orderRepository.findById(1)).thenReturn(Optional.of(buyOrder));
        when(clientServiceClient.getEmailById("service-call", 123))
            .thenReturn(new ResponseEntity<>("buyer@test.com", HttpStatus.OK));
        when(walletServiceClient.walletTransaction(anyString(), anyString(), anyDouble(), anyString()))
            .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        // Act
        matchingConsumer.receiveTrade(trade);

        // Assert - Should use ORDER price (100.0), NOT trade price (95.0)
        double expectedDebit = buyOrder.getPrice() * buyOrder.getQuantity(); // 100.0 * 10 = 1000.0
        verify(walletServiceClient).walletTransaction(
            "buyer@test.com", 
            "buyer@test.com", 
            expectedDebit, // Should be 1000.0, not 950.0
            "DEBIT"
        );
    }

    @Test
    @DisplayName("Integration Test - Complete Trade Flow")
    void receiveTrade_CompleteFlow_Success() {
        // Arrange
        when(orderRepository.findById(1)).thenReturn(Optional.of(buyOrder));
        when(orderRepository.findById(2)).thenReturn(Optional.of(sellOrder));
        when(clientServiceClient.getEmailById("service-call", 123))
            .thenReturn(new ResponseEntity<>("buyer@test.com", HttpStatus.OK));
        when(clientServiceClient.getEmailById("service-call", 456))
            .thenReturn(new ResponseEntity<>("seller@test.com", HttpStatus.OK));
        when(walletServiceClient.walletTransaction(anyString(), anyString(), anyDouble(), anyString()))
            .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        // Act
        matchingConsumer.receiveTrade(trade);

        // Assert complete flow
        // 1. Both orders marked as filled
        assertEquals(OrderStatus.FILLED, buyOrder.getStatus());
        assertEquals(OrderStatus.FILLED, sellOrder.getStatus());
        
        // 2. Both orders saved
        verify(orderRepository).save(buyOrder);
        verify(orderRepository).save(sellOrder);
        
        // 3. Buyer debited correct amount (order price * order quantity)
        verify(walletServiceClient).walletTransaction(
            "buyer@test.com", 
            "buyer@test.com", 
            1500.0, // 150.0 * 10
            "DEBIT"
        );
        
        // 4. Seller credited correct amount (trade price * trade quantity)
        verify(walletServiceClient).walletTransaction(
            "seller@test.com", 
            "seller@test.com", 
            747.5, // 149.5 * 5 (trade quantity, not order quantity)
            "CREDIT"
        );
    }
}