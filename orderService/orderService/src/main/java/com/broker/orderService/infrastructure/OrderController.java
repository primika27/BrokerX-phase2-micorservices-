package com.broker.orderService.infrastructure;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.ResponseEntity;
import com.broker.orderService.Application.OrderService;
import com.broker.orderService.saga.OrderSagaOrchestrator;
import com.broker.orderService.saga.SagaResult;
import com.broker.orderService.service.OutboxService;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Map<String, Double> ETF_PRICES = new HashMap<>();
    static{

        ETF_PRICES.put("SPY", 445.50);   // S&P 500 ETF
        ETF_PRICES.put("IVV", 445.20);   // iShares S&P 500 ETF
        ETF_PRICES.put("VOO", 445.80);   // Vanguard S&P 500 ETF
        ETF_PRICES.put("VTI", 265.40);   // Vanguard Total Stock Market ETF
        ETF_PRICES.put("QQQ", 380.75);   // Invesco QQQ Trust
        ETF_PRICES.put("VEA", 51.20);    // Vanguard FTSE Developed Markets ETF
        ETF_PRICES.put("VWO", 42.85);    // Vanguard FTSE Emerging Markets ETF
        ETF_PRICES.put("AGG", 101.50);   // iShares Core U.S. Aggregate Bond ETF
        ETF_PRICES.put("BND", 73.25);    // Vanguard Total Bond Market ETF
        ETF_PRICES.put("IWM", 220.30);   // iShares Russell 2000 ETF
        ETF_PRICES.put("EFA", 79.90);    // iShares MSCI EAFE ETF

    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderSagaOrchestrator sagaOrchestrator;

    @Autowired
    private OutboxService outboxService;

    @PostMapping("/placeOrder")
    public ResponseEntity<String> placeOrder(
            @RequestHeader(value = "X-Authenticated-User", required = true) String clientEmail,
            @RequestParam String symbol,
            @RequestParam int quantity,
            @RequestParam String orderType) {
        
        if (clientEmail == null || clientEmail.isEmpty()) {
            return ResponseEntity.badRequest().body("Request must go through Gateway - Missing authentication header");
        }
        
        if (!ETF_PRICES.containsKey(symbol)) {
            return ResponseEntity.badRequest().body("Symbol " + symbol + " not found");
        }
        
        double price = ETF_PRICES.get(symbol);
        boolean success;
        
        if ("BUY".equalsIgnoreCase(orderType)) {
            success = orderService.acheterAction(clientEmail, symbol, price, quantity);
        } else if ("SELL".equalsIgnoreCase(orderType)) {
            success = orderService.vendreAction(clientEmail, symbol, price, quantity);
        } else {
            return ResponseEntity.badRequest().body("Invalid order type. Use BUY or SELL.");
        }
        
        if (success) {
            return ResponseEntity.ok("Order submitted for " + orderType.toUpperCase());
        } else {
            return ResponseEntity.badRequest().body("Order failed. Please try again.");
        }
    }

    @GetMapping("/holdings")
    public ResponseEntity<Map<String, Object>> getHoldings(@RequestHeader(value = "X-Authenticated-User", required = true) String clientEmail) {
        if (clientEmail == null || clientEmail.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Request must go through Gateway - Missing authentication header");
            return ResponseEntity.badRequest().body(error);
        }
        
        Map<String, Object> holdings = orderService.getHoldings(clientEmail);
        
        if (holdings.containsKey("error")) {
            return ResponseEntity.badRequest().body(holdings);
        } else {
            return ResponseEntity.ok(holdings);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getOrderStatus(@RequestHeader(value = "X-Authenticated-User", required = true) String clientEmail) {
        if (clientEmail == null || clientEmail.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Request must go through Gateway - Missing authentication header");
            return ResponseEntity.badRequest().body(error);
        }
        
        Map<String, Object> orderStatus = orderService.getOrderStatus(clientEmail);
        
        if (orderStatus.containsKey("error")) {
            return ResponseEntity.badRequest().body(orderStatus);
        } else {
            return ResponseEntity.ok(orderStatus);
        }
    }

    /**
     * Cancel a PENDING order using Saga pattern
     * DELETE /api/orders/{orderId}
     */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> cancelOrder(
            @PathVariable int orderId,
            @RequestHeader(value = "X-Authenticated-User", required = true) String clientEmail) {
        
        Map<String, Object> response = new HashMap<>();
        
        if (clientEmail == null || clientEmail.isEmpty()) {
            response.put("error", "Request must go through Gateway - Missing authentication header");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            SagaResult result = sagaOrchestrator.cancelOrderSaga(orderId, clientEmail);
            
            if (result.isSuccess()) {
                response.put("success", true);
                response.put("message", result.getMessage());
                response.put("steps", result.getExecutedSteps());
                
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", result.getErrorMessage());
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Failed to cancel order: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Modify a PENDING order using Saga pattern
     * PUT /api/orders/{orderId}
     */
    @PutMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> modifyOrder(
            @PathVariable int orderId,
            @RequestHeader(value = "X-Authenticated-User", required = true) String clientEmail,
            @RequestParam(required = false) Double newPrice,
            @RequestParam(required = false) Integer newQuantity) {
        
        Map<String, Object> response = new HashMap<>();
        
        if (clientEmail == null || clientEmail.isEmpty()) {
            response.put("error", "Request must go through Gateway - Missing authentication header");
            return ResponseEntity.badRequest().body(response);
        }
        
        if (newPrice == null && newQuantity == null) {
            response.put("error", "At least one of newPrice or newQuantity must be provided");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            SagaResult result = sagaOrchestrator.modifyOrderSaga(orderId, clientEmail, newPrice, newQuantity);
            
            if (result.isSuccess()) {
                response.put("success", true);
                response.put("message", result.getMessage());
                response.put("steps", result.getExecutedSteps());
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", result.getErrorMessage());
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", "Failed to modify order: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Debug endpoint to check outbox events
     * GET /api/orders/debug/outbox
     */
    @GetMapping("/debug/outbox")
    public ResponseEntity<Map<String, Object>> debugOutbox() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get outbox stats
            long totalEvents = outboxService.getTotalEventsCount();
            long unprocessedEvents = outboxService.getUnprocessedEventsCount();
            
            response.put("totalEvents", totalEvents);
            response.put("unprocessedEvents", unprocessedEvents);
            response.put("message", "Outbox events are processed every 5 seconds automatically");
            
            // Get recent events details
            var recentEvents = outboxService.getRecentEvents(5);
            response.put("recentEvents", recentEvents);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Debug failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

}
