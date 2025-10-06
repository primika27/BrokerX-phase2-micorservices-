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

@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TransactionRepository transactionRepository;


    // Acheter des actions
    
@Transactional
    public boolean acheterAction(int clientId, String symbol, double price, int quantity) {

    try {
        // Créer l'ordre en positionnant explicitement clientId et status
        Order order = new Order();
        order.setSymbol(symbol);
        order.setPrice(price);
        order.setQuantity(quantity);
        order.setStatus(1); // exécuté
        order.setOrderType("BUY");
        Order savedOrder = orderRepository.save(order);
        
        // Créer une transaction pour l'audit trail
        String description = String.format("Achat %d actions %s à %.2f€", quantity, symbol, price);
            // Transaction transaction = new Transaction(
            //     savedOrder.getOrderId(),
            //     TransactionType.ORDER,
            //     total,
            //     portefeuille.getPortefeuilleId(),
            //     description
            // );
        //transactionRepository.save(transaction);
        
        return true;
        
    } catch (Exception e) {
        System.err.println("Erreur acheterAction: " + e.getMessage());
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

    public boolean passerOrdre(int clientId, String symbol, double price, int quantity) {
        return acheterAction(clientId, symbol, price, quantity);
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
