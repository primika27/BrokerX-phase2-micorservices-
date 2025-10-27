package com.broker.orderService.Application;

import com.broker.orderService.infrastructure.client.ClientServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.broker.orderService.domain.Order;
import java.util.List;
import java.util.Map;
import com.broker.orderService.domain.OrderStatus;
import java.util.HashMap;
import com.broker.orderService.domain.Transaction;
import com.broker.orderService.domain.TransactionType;
import com.broker.orderService.infrastructure.repo.OrderRepository;
import com.broker.orderService.infrastructure.repo.TransactionRepository;
import com.broker.orderService.infrastructure.client.WalletServiceClient;
import org.springframework.http.ResponseEntity;
import com.broker.orderService.service.OrderMessageProducer; // Import the producer
import com.broker.orderService.dto.OrderDto; // Import the DTO


@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private WalletServiceClient walletServiceClient;

    @Autowired
    private ClientServiceClient clientServiceClient;

    @Autowired // Inject the producer
    private OrderMessageProducer orderMessageProducer;


    // Acheter des actions
    
    @Transactional
    public boolean acheterAction(String clientEmail, String symbol, double price, int quantity) {
        try {
            double total = price * quantity;

            // 1. Vérifier le solde du wallet avant de créer l'ordre
            ResponseEntity<Double> balanceResponse = walletServiceClient.getBalance(clientEmail);
            if (balanceResponse == null || !balanceResponse.getStatusCode().is2xxSuccessful() || balanceResponse.getBody() == null) {
                System.err.println("Impossible de récupérer le solde du wallet pour " + clientEmail);
                return false;
            }
            
            double currentBalance = balanceResponse.getBody();
            if (currentBalance < total) {
                System.err.println("Solde insuffisant pour " + clientEmail + ". Solde: " + currentBalance + ", Requis: " + total);
                return false;
            }
            
            // 2. Débiter le wallet
            ResponseEntity<String> debitResponse = walletServiceClient.walletTransaction(clientEmail, total, "DEBIT");
            if (debitResponse == null || !debitResponse.getStatusCode().is2xxSuccessful()) {
                System.err.println("Échec du débit du wallet pour " + clientEmail);
                return false;
            }

            // 3. Get clientId from clientService
            ResponseEntity<Integer> clientResponse = clientServiceClient.getByEmail(clientEmail);
            if (clientResponse == null || !clientResponse.getStatusCode().is2xxSuccessful() || clientResponse.getBody() == null) {
                System.err.println("Impossible de récupérer le clientID pour " + clientEmail);
                return false;
            }
            int clientId = clientResponse.getBody();
            
            // 4. Créer l'ordre après débit réussi
            Order order = new Order(); // This is your domain.Order
            order.setClientId(clientId);
            order.setSymbol(symbol);
            order.setPrice(price);
            order.setQuantity(quantity);
            order.setStatus(OrderStatus.FILLED); // exécuté - This status might need review for pending matching
            order.setOrderType("BUY");
            Order savedOrder = orderRepository.save(order);

            // 5. Créer la transaction pour l'audit trail
            Transaction transaction = new Transaction(
                savedOrder.getOrderId(),
                TransactionType.ORDER,
                total,
                String.format("Achat %d actions %s à %.2f$ par %s", quantity, symbol, price, clientEmail)
            );
            transactionRepository.save(transaction);
            
            // 6. Send order to matching service via RabbitMQ
            OrderDto orderDto = new OrderDto(); // This is the DTO for RabbitMQ
            orderDto.setOrderDtoId(String.valueOf(savedOrder.getOrderId())); // Assuming getOrderId returns int
            orderDto.setStockSymbol(savedOrder.getSymbol());
            orderDto.setQuantity(savedOrder.getQuantity());
            orderDto.setPrice(savedOrder.getPrice());
            orderDto.setOrderType(savedOrder.getOrderType());
            orderMessageProducer.sendNewOrderToMatchingService(orderDto);

            System.out.println("Achat réussi pour " + clientEmail + ": " + quantity + " " + symbol + " à " + price + "$ (Total: " + total + "$)");
            return true;
            
        } catch (Exception e) {
            System.err.println("Erreur acheterAction: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Get holdings for a client
    public Map<String, Object> getHoldings(String clientEmail) {
        try {
            // Get client ID from client service
            ResponseEntity<Integer> clientIdResponse = clientServiceClient.getByEmail(clientEmail);
            
            if (clientIdResponse.getStatusCode().is2xxSuccessful() && clientIdResponse.getBody() != null) {
                int clientId = clientIdResponse.getBody().intValue();
                
                // Get filled orders for this client
                List<Order> filledOrders = orderRepository.findFilledOrdersByClientId(clientId);
                
                // Group by symbol and calculate total holdings
                Map<String, Integer> holdings = new HashMap<>();
                for (Order order : filledOrders) {
                    holdings.merge(order.getSymbol(), order.getQuantity(), Integer::sum);
                }
                
                Map<String, Object> result = new HashMap<>();
                result.put("clientEmail", clientEmail);
                result.put("holdings", holdings);
                result.put("totalPositions", holdings.size());
                
                return result;
            } else {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", "Client not found");
                return errorResult;
            }
        } catch (Exception e) {
            System.out.println("Error getting holdings for " + clientEmail + ": " + e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Unable to retrieve holdings: " + e.getMessage());
            return errorResult;
        }
    }
}
