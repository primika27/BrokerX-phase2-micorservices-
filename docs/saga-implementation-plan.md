# Choreographed Saga with Outbox Pattern - Implementation Plan

## 1. Outbox Pattern Implementation

### A. Create Outbox Tables in Each Service

#### OrderService - Outbox Table
```sql
CREATE TABLE order_outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,      -- orderId
    event_type VARCHAR(255) NOT NULL,        -- OrderCreated, OrderCancelled, etc.
    payload TEXT NOT NULL,                   -- JSON event data
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    retry_count INT DEFAULT 0
);
```

#### WalletService - Outbox Table
```sql
CREATE TABLE wallet_outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,      -- walletId/clientEmail
    event_type VARCHAR(255) NOT NULL,        -- FundsReserved, FundsDebited, etc.
    payload TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    retry_count INT DEFAULT 0
);
```

#### MatchingService - Outbox Table
```sql
CREATE TABLE matching_outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_id VARCHAR(255) NOT NULL,      -- tradeId
    event_type VARCHAR(255) NOT NULL,        -- OrderMatched, TradeExecuted
    payload TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    retry_count INT DEFAULT 0
);
```

## 2. Saga Choreography Design

### Order Placement Saga Flow

#### Current Flow (Orchestrated):
```
Frontend → Gateway → OrderService → WalletService → MatchingService
```

#### New Choreographed Flow:
```
1. OrderService: OrderSubmitted → outbox
2. WalletService: listens to OrderSubmitted → FundsReserved → outbox  
3. MatchingService: listens to FundsReserved → OrderQueued → outbox
4. MatchingService: finds match → TradeExecuted → outbox
5. WalletService: listens to TradeExecuted → FundsTransferred → outbox
6. OrderService: listens to TradeExecuted → OrderFilled → outbox
```

#### Compensation Flow (Rollback):
```
If FundsReservationFailed:
  OrderService: OrderRejected → outbox
  
If MatchingFailed:
  WalletService: FundsReleased → outbox
  OrderService: OrderCancelled → outbox
```

## 3. Event Types Definition

### OrderService Events
- `OrderSubmitted` - Initial order creation
- `OrderRejected` - Order rejected due to validation
- `OrderCancelled` - Order cancelled
- `OrderFilled` - Order successfully executed
- `OrderPartiallyFilled` - Partial execution

### WalletService Events  
- `FundsReserved` - Funds locked for order
- `FundsReservationFailed` - Insufficient funds
- `FundsDebited` - Final debit after trade
- `FundsReleased` - Funds unlocked (compensation)
- `FundsCredited` - Credit from sell order

### MatchingService Events
- `OrderQueued` - Order added to matching engine
- `TradeExecuted` - Successful match found
- `OrderExpired` - Order expired without match
- `MatchingFailed` - Technical matching failure

## 4. Implementation Steps

### Phase 1: Infrastructure Setup
1. Add outbox tables to all services
2. Create OutboxEvent entity classes
3. Implement OutboxEventPublisher service
4. Setup scheduled outbox processor

### Phase 2: Event System
1. Define event schemas (JSON)
2. Create event handlers in each service
3. Implement RabbitMQ topic exchanges
4. Add event routing configuration

### Phase 3: Saga Logic
1. Replace direct service calls with events
2. Implement compensation handlers
3. Add saga state tracking
4. Implement timeout mechanisms

### Phase 4: Monitoring & Recovery
1. Add event processing metrics
2. Implement dead letter queues
3. Create saga monitoring dashboard
4. Add manual intervention tools

## 5. Key Benefits

### Reliability
- **At-least-once delivery** via outbox pattern
- **Automatic compensation** for failed transactions
- **Resilience** to service failures

### Scalability  
- **Loose coupling** between services
- **Asynchronous processing**
- **Better resource utilization**

### Maintainability
- **Clear event contracts**
- **Distributed business logic**
- **Easy to add new services**

## 6. Migration Strategy

### Approach: Strangler Fig Pattern
1. Keep existing orchestrated flow
2. Add outbox pattern to services
3. Implement choreographed flow in parallel
4. Gradually migrate traffic
5. Remove orchestrated logic

### Rollback Plan
- Feature flags for saga vs orchestrator
- Monitoring for both approaches
- Quick rollback capability

## 7. Testing Strategy

### Unit Tests
- Event publisher logic
- Event handler logic
- Compensation scenarios

### Integration Tests
- End-to-end saga flows
- Failure scenarios
- Performance under load

### Chaos Engineering
- Service failures during saga
- Network partitions
- Message loss scenarios