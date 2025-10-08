package com.broker.orderService.Application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.broker.orderService.domain.Order;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import com.broker.orderService.domain.Transaction;
import com.broker.orderService.domain.TransactionType;
import com.broker.orderService.infrastructure.repo.OrderRepository;
import com.broker.orderService.infrastructure.repo.TransactionRepository;
import com.broker.orderService.infrastructure.client.WalletServiceClient;
import org.springframework.http.ResponseEntity;

@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private WalletServiceClient walletServiceClient;


    // Acheter des actions
    
    @Transactional
    public boolean acheterAction(String clientEmail, String symbol, double price, int quantity) {
        try {
            double total = price * quantity;
            
            // 1. Vérifier le solde du wallet avant de créer l'ordre
            ResponseEntity<Double> balanceResponse = walletServiceClient.getBalance(clientEmail);
            if (balanceResponse == null || balanceResponse.getBody() == null) {
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
            
            // 3. Créer l'ordre après débit réussi
            Order order = new Order();
            order.setSymbol(symbol);
            order.setPrice(price);
            order.setQuantity(quantity);
            order.setStatus(1); // exécuté
            order.setOrderType("BUY");
            Order savedOrder = orderRepository.save(order);

            // 4. Créer la transaction pour l'audit trail
            Transaction transaction = new Transaction(
                savedOrder.getOrderId(),
                TransactionType.ORDER,
                total, 
                0, // On peut mettre 0 ou un ID générique puisqu'on utilise l'email maintenant
                String.format("Achat %d actions %s à %.2f$ par %s", quantity, symbol, price, clientEmail)
            );
            transactionRepository.save(transaction);
            
            System.out.println("Achat réussi pour " + clientEmail + ": " + quantity + " " + symbol + " à " + price + "$ (Total: " + total + "$)");
            return true;
            
        } catch (Exception e) {
            System.err.println("Erreur acheterAction: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

//  @Transactional
//    public boolean vendreAction(int clientId, String symbol, double price, int quantity) {
//     try {
       
//         Map<String, Integer> holdings = getClientHoldings(clientId);
//         int owned = holdings.getOrDefault(symbol, 0);
//         if (owned < quantity) {
//             return false; 
//         }

//         double total = price * quantity;
//         //portefeuille.setSolde(portefeuille.getSolde() + total);


        
//         // Créer l'ordre SELL correctement avec clientId et status
//         Order order = new Order();
//         order.setSymbol(symbol);
//         order.setPrice(price);
//         order.setQuantity(quantity);
//         order.setStatus(1); // exécuté
//         order.setOrderType("SELL");
//         Order savedOrder = orderRepository.save(order);
        
//         // Créer une transaction pour l'audit trail
//         String description = String.format("Vente %d actions %s à %.2f€", quantity, symbol, price);
//         Transaction transaction = new Transaction(
//             savedOrder.getOrderId(),
//             TransactionType.ORDER,
//             total, clientId,
//             description
//         );
//         transactionRepository.save(transaction);

//         return true;
//     } catch (Exception e) {
//         System.err.println("Erreur vendreAction: " + e.getMessage());
//         return false;
//     }
// }

    public boolean passerOrdre(String clientEmail, String symbol, double price, int quantity) {
        return acheterAction(clientEmail, symbol, price, quantity);
    }

  
    public void annulerOrdre(int orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        orderOpt.ifPresent(orderRepository::delete);
    }

    public void modifierOrdre(int orderId, double newPrice, int newQuantity) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            order.setPrice(newPrice);
            order.setQuantity(newQuantity);
            orderRepository.save(order);

        }
    }

    
    // public Map<String, Integer> getClientHoldings(int clientId) {
    //     Map<String, Integer> holdings = new HashMap<>();
        
    //     try {
    //         List<Order> orders = orderRepository.findAll();
            
    //         for (Order order : orders) {
    //             if (order.getClientId() == clientId && order.getStatus() == 1) { // Ordres exécutés
    //                 String symbol = order.getSymbol();
    //                 int quantity = order.getQuantity();
                    
    //                 if ("BUY".equals(order.getOrderType())) {
    //                     holdings.put(symbol, holdings.getOrDefault(symbol, 0) + quantity);
    //                 } else if ("SELL".equals(order.getOrderType())) {
    //                     holdings.put(symbol, holdings.getOrDefault(symbol, 0) - quantity);
    //                 }
    //             }
    //         }
            
            
    //         holdings.entrySet().removeIf(entry -> entry.getValue() <= 0);
            
    //     } catch (Exception e) {
    //         System.err.println("Erreur getClientHoldings: " + e.getMessage());
    //     }
        
    //     return holdings;
    // }

}
