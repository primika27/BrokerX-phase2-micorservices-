package com.broker.orderService.service;

import com.broker.orderService.infrastructure.repo.OrderRepository;
import com.broker.orderService.infrastructure.repo.TransactionRepository;
import com.broker.orderService.domain.Order;
import com.broker.orderService.domain.Transaction;
import com.broker.orderService.domain.TransactionType;
import com.broker.orderService.domain.OrderStatus;
import com.broker.orderService.infrastructure.client.WalletServiceClient;
import com.broker.orderService.infrastructure.client.ClientServiceClient;
import com.broker.orderService.dto.TradeDto;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;

@Service
public class TradeExecutionService {

    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final WalletServiceClient walletServiceClient;
    private final ClientServiceClient clientServiceClient;

    public TradeExecutionService(OrderRepository orderRepository, 
                               TransactionRepository transactionRepository,
                               WalletServiceClient walletServiceClient,
                               ClientServiceClient clientServiceClient) {
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.walletServiceClient = walletServiceClient;
        this.clientServiceClient = clientServiceClient;
    }

    @RabbitListener(queues = "matchingQueue")
    @Transactional
    public void executeTrade(TradeDto trade) {
        try {
            System.out.println("Executing trade: " + trade);

            // Update buy order status
            Order buyOrder = orderRepository.findByOrderId(Integer.parseInt(trade.getBuyOrderId()));
            if (buyOrder != null) {
                buyOrder.setStatus(OrderStatus.FILLED);
                orderRepository.save(buyOrder);
                
                // Debit buyer's wallet
                double totalCost = trade.getPrice() * trade.getQuantity();
                // Note: We'll need client email - for now using a placeholder approach
                // In a real system, we'd need to get client email from order or have it in the trade
                String buyerEmail = getBuyerEmailFromOrder(buyOrder);
                if (buyerEmail != null) {
                    ResponseEntity<String> debitResponse = walletServiceClient.walletTransaction(
                        buyerEmail, buyerEmail, totalCost, "DEBIT");
                    
                    if (debitResponse != null && debitResponse.getStatusCode().is2xxSuccessful()) {
                        // Create transaction record
                        Transaction buyTransaction = new Transaction(
                            buyOrder.getOrderId(),
                            TransactionType.ORDER,
                            totalCost,
                            String.format("Trade execution: Bought %d shares of %s at $%.2f", 
                                trade.getQuantity(), trade.getSymbol(), trade.getPrice())
                        );
                        transactionRepository.save(buyTransaction);
                        System.out.println("Successfully executed BUY side of trade for order: " + trade.getBuyOrderId());
                    }
                }
            }

            // Update sell order status  
            Order sellOrder = orderRepository.findByOrderId(Integer.parseInt(trade.getSellOrderId()));
            if (sellOrder != null) {
                sellOrder.setStatus(OrderStatus.FILLED);
                orderRepository.save(sellOrder);
                
                // Credit seller's wallet
                double totalCredit = trade.getPrice() * trade.getQuantity();
                String sellerEmail = getSellerEmailFromOrder(sellOrder);
                if (sellerEmail != null) {
                    ResponseEntity<String> creditResponse = walletServiceClient.walletTransaction(
                        sellerEmail, sellerEmail, totalCredit, "CREDIT");
                    
                    if (creditResponse != null && creditResponse.getStatusCode().is2xxSuccessful()) {
                        // Create transaction record
                        Transaction sellTransaction = new Transaction(
                            sellOrder.getOrderId(),
                            TransactionType.ORDER,
                            totalCredit,
                            String.format("Trade execution: Sold %d shares of %s at $%.2f", 
                                trade.getQuantity(), trade.getSymbol(), trade.getPrice())
                        );
                        transactionRepository.save(sellTransaction);
                        System.out.println("Successfully executed SELL side of trade for order: " + trade.getSellOrderId());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error executing trade: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper methods to get client emails from orders
    private String getBuyerEmailFromOrder(Order order) {
        try {
            ResponseEntity<String> response = clientServiceClient.getEmailById("order-service", order.getClientId());
            if (response != null && response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            System.err.println("Error getting buyer email for clientId " + order.getClientId() + ": " + e.getMessage());
        }
        return null;
    }

    private String getSellerEmailFromOrder(Order order) {
        try {
            ResponseEntity<String> response = clientServiceClient.getEmailById("order-service", order.getClientId());
            if (response != null && response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
        } catch (Exception e) {
            System.err.println("Error getting seller email for clientId " + order.getClientId() + ": " + e.getMessage());
        }
        return null;
    }
}