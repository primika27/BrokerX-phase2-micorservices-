package com.broker.orderService.Application;
import com.broker.orderService.infrastructure.client.ClientServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.broker.orderService.domain.Order;
import java.util.List;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
import jakarta.mail.internet.MimeMessage;
import com.broker.orderService.dto.OrderDto; // Import the DTO


@Service
@Transactional
public class OrderService {

    @Autowired
    private JavaMailSender mailSender;

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
            ResponseEntity<Double> balanceResponse = walletServiceClient.getBalance(clientEmail, clientEmail);
            if (balanceResponse == null || !balanceResponse.getStatusCode().is2xxSuccessful() || balanceResponse.getBody() == null) {
                System.err.println("Impossible de récupérer le solde du wallet pour " + clientEmail);
                return false;
            }
            
            double currentBalance = balanceResponse.getBody();
            if (currentBalance < total) {
                System.err.println("Solde insuffisant pour " + clientEmail + ". Solde: " + currentBalance + ", Requis: " + total);
                return false;
            }
            
            // 2. Get clientId from clientService first
            ResponseEntity<Integer> clientResponse = clientServiceClient.getByEmail(clientEmail, clientEmail);
            if (clientResponse == null || !clientResponse.getStatusCode().is2xxSuccessful() || clientResponse.getBody() == null) {
                System.err.println("Impossible de récupérer le clientID pour " + clientEmail);
                return false;
            }
            int clientId = clientResponse.getBody();
            
            // 3. Create PENDING order (wallet will be debited only when trade executes)
            Order order = new Order(); // This is your domain.Order
            order.setClientId(clientId);
            order.setSymbol(symbol);
            order.setPrice(price);
            order.setQuantity(quantity);
            order.setStatus(OrderStatus.PENDING); // Start as PENDING for matching
            order.setOrderType("BUY");
            Order savedOrder = orderRepository.save(order);
            
            // 4. Send order to matching service via RabbitMQ for processing
            OrderDto orderDto = new OrderDto(); // This is the DTO for RabbitMQ
            // Use standard 'orderId' property name so matching service receives it correctly
            orderDto.setOrderId(String.valueOf(savedOrder.getOrderId())); // Assuming getOrderId returns int
            orderDto.setStockSymbol(savedOrder.getSymbol());
            orderDto.setQuantity(savedOrder.getQuantity());
            orderDto.setPrice(savedOrder.getPrice());
            orderDto.setOrderType(savedOrder.getOrderType());
            orderMessageProducer.sendNewOrderToMatchingService(orderDto);

            System.out.println("Order submitted for matching: " + clientEmail + ": " + quantity + " " + symbol + " à " + price + "$ - Order ID: " + savedOrder.getOrderId());
            return true;
            
        } catch (Exception e) {
            System.err.println("Erreur acheterAction: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Sell order - Market Order (immediate execution for demo)
    public boolean vendreAction(String clientEmail, String symbol, double price, int quantity) {
        try {
            System.out.println("Processing SELL order for " + clientEmail + ": " + quantity + " " + symbol + " at " + price);

            // 1. Get clientId from clientService
            ResponseEntity<Integer> clientResponse = clientServiceClient.getByEmail(clientEmail, clientEmail);
            if (clientResponse == null || !clientResponse.getStatusCode().is2xxSuccessful() || clientResponse.getBody() == null) {
                System.err.println("Impossible de récupérer le clientID pour " + clientEmail);
                return false;
            }
            int clientId = clientResponse.getBody();
            
            // 2. Check if user has enough shares to sell (basic validation)
            Map<String, Integer> currentHoldings = getCurrentHoldings(clientId);
            int currentQuantity = currentHoldings.getOrDefault(symbol, 0);
            if (currentQuantity < quantity) {
                System.err.println("Insufficient shares to sell. Has: " + currentQuantity + ", Trying to sell: " + quantity);
                return false;
            }
            
            // 3. For demo purposes: Execute market order immediately (bypass matching)
            // Credit wallet immediately
            double total = price * quantity;
            ResponseEntity<String> creditResponse = walletServiceClient.walletTransaction(
                clientEmail, clientEmail, total, "CREDIT");
            
            if (creditResponse == null || !creditResponse.getStatusCode().is2xxSuccessful()) {
                System.err.println("Failed to credit wallet for " + clientEmail);
                return false;
            }
            
            // 4. Create FILLED SELL order (immediate execution) - negative quantity for holdings calculation
            Order order = new Order();
            order.setClientId(clientId);
            order.setSymbol(symbol);
            order.setPrice(price);
            order.setQuantity(-quantity); // Negative for sell to reduce holdings
            order.setStatus(OrderStatus.FILLED); // Immediately filled
            order.setOrderType("SELL");
            Order savedOrder = orderRepository.save(order);
            
            // 5. Create transaction record
            Transaction transaction = new Transaction(
                savedOrder.getOrderId(),
                TransactionType.ORDER,
                total,
                String.format("Market Sell: %d shares of %s at $%.2f", quantity, symbol, price)
            );
            transactionRepository.save(transaction);

            System.out.println("Market SELL order executed immediately: " + clientEmail + ": " + quantity + " " + symbol + " à " + price + "$ - Order ID: " + savedOrder.getOrderId());
            return true;
            
        } catch (Exception e) {
            System.err.println("Erreur vendreAction: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Helper method to get current holdings as a map
    private Map<String, Integer> getCurrentHoldings(int clientId) {
        try {
            List<Order> filledOrders = orderRepository.findFilledOrdersByClientId(clientId);
            Map<String, Integer> holdings = new HashMap<>();
            for (Order order : filledOrders) {
                holdings.merge(order.getSymbol(), order.getQuantity(), Integer::sum);
            }
            return holdings;
        } catch (Exception e) {
            System.err.println("Error getting current holdings for clientId " + clientId + ": " + e.getMessage());
            return new HashMap<>();
        }
    }

    // Get holdings for a client
    public Map<String, Object> getHoldings(String clientEmail) {
        try {
            // Get client ID from client service
            ResponseEntity<Integer> clientIdResponse = clientServiceClient.getByEmail(clientEmail, clientEmail);
            
            if (clientIdResponse.getStatusCode().is2xxSuccessful() && clientIdResponse.getBody() != null) {
                int clientId = clientIdResponse.getBody().intValue();
                
                // Get filled orders for this client
                Map<String, Integer> holdings = getCurrentHoldings(clientId);
                
                // Filter out zero or negative holdings
                holdings.entrySet().removeIf(entry -> entry.getValue() <= 0);
                
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

    // Get order status for a client
    public Map<String, Object> getOrderStatus(String clientEmail) {
        try {
            // Get client ID from client service
            ResponseEntity<Integer> clientIdResponse = clientServiceClient.getByEmail(clientEmail, clientEmail);
            
            if (clientIdResponse.getStatusCode().is2xxSuccessful() && clientIdResponse.getBody() != null) {
                int clientId = clientIdResponse.getBody().intValue();
                
                // Get all orders for this client
                List<Order> allOrders = orderRepository.findByClientIdOrderByOrderIdDesc(clientId);
                
                Map<String, Object> result = new HashMap<>();
                result.put("clientEmail", clientEmail);
                result.put("orders", allOrders);
                result.put("totalOrders", allOrders.size());
                
                return result;
            } else {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", "Client not found");
                return errorResult;
            }
        } catch (Exception e) {
            System.out.println("Error getting order status for " + clientEmail + ": " + e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Unable to retrieve order status: " + e.getMessage());
            return errorResult;
        }
    }

    /**
     * Get client email by clientId via ClientService
     */
    private String getClientEmail(int clientId) {
        try {
            var response = clientServiceClient.getEmailById("orderService", clientId);
            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            System.err.println("Error fetching client email: " + e.getMessage());
        }
        return null;
    }

    /**
     * Send email notification for order status changes
     */
    public void sendOrderStatusEmail(String clientEmail, Order order, String status, String message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setTo(clientEmail);
            helper.setFrom("brokerx@trading.com");
            helper.setSubject("BrokerX Order Update - " + status);
            
            String emailBody = buildOrderStatusEmailBody(order, status, message);
            helper.setText(emailBody, true); // true for HTML content
            
            mailSender.send(mimeMessage);
            System.out.println("Order status email sent to: " + clientEmail + " for order #" + order.getOrderId() + " (" + status + ")");
            
        } catch (Exception e) {
            System.err.println("Failed to send order status email to " + clientEmail + ": " + e.getMessage());
            e.printStackTrace();
            // Don't throw exception - email failure shouldn't break order processing
        }
    }
    
    /**
     * Send order status email notification using clientId (convenience method)
     */
    public void sendOrderStatusEmailByClientId(int clientId, Order order, String status, String message) {
        String clientEmail = getClientEmail(clientId);
        if (clientEmail != null) {
            sendOrderStatusEmail(clientEmail, order, status, message);
        } else {
            System.err.println("Cannot send email notification - client email not found for clientId: " + clientId);
        }
    }
    
    /**
     * Build HTML email body for order status notifications
     */
    private String buildOrderStatusEmailBody(Order order, String status, String message) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body>");
        html.append("<h2>BrokerX Order Update</h2>");
        html.append("<p>Dear Trader,</p>");
        html.append("<p>").append(message).append("</p>");
        
        html.append("<table border='1' cellpadding='10' cellspacing='0' style='border-collapse: collapse;'>");
        html.append("<tr><td><strong>Order ID:</strong></td><td>#").append(order.getOrderId()).append("</td></tr>");
        html.append("<tr><td><strong>Symbol:</strong></td><td>").append(order.getSymbol()).append("</td></tr>");
        html.append("<tr><td><strong>Type:</strong></td><td>").append(order.getOrderType()).append("</td></tr>");
        html.append("<tr><td><strong>Quantity:</strong></td><td>").append(order.getQuantity()).append("</td></tr>");
        html.append("<tr><td><strong>Price:</strong></td><td>$").append(String.format("%.2f", order.getPrice())).append("</td></tr>");
        
        // Set color based on status
        String statusColor = "black";
        if (status.equals("FILLED")) {
            statusColor = "green";
        } else if (status.equals("CANCELLED")) {
            statusColor = "red";
        } else if (status.equals("MODIFIED")) {
            statusColor = "blue";
        }
        
        html.append("<tr><td><strong>Status:</strong></td><td><span style='color:")
               .append(statusColor).append(";'><strong>")
               .append(status).append("</strong></span></td></tr>");
        
        if (status.equals("FILLED")) {
            double totalValue = order.getQuantity() * order.getPrice();
            html.append("<tr><td><strong>Total Value:</strong></td><td>$").append(String.format("%.2f", totalValue)).append("</td></tr>");
        }
        
        html.append("</table>");
        
        if (status.equals("FILLED")) {
            html.append("<p style='color: green;'><strong>Congratulations! Your order has been executed.</strong></p>");
        } else if (status.equals("CANCELLED")) {
            html.append("<p style='color: red;'><strong>Your order has been cancelled as requested.</strong></p>");
        } else if (status.equals("MODIFIED")) {
            html.append("<p style='color: blue;'><strong>Your order has been updated with the new details above.</strong></p>");
        }
        
        html.append("<p>Thank you for using BrokerX!</p>");
        html.append("<p><em>This is an automated notification. Please do not reply to this email.</em></p>");
        html.append("</body></html>");
        
        return html.toString();
    }
}
