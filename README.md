# BrokerX Architecture

## Overview

BrokerX is a stock trading platform built using a **microservices architecture**. Each microservice is responsible for a specific business domain and can be developed, deployed, and scaled independently. Communication between the frontend and backend is handled through an API Gateway, which routes requests to the appropriate service.

## Architecture

```
                           +----------------------+
                           |    React Frontend    |
                           +----------+-----------+
                                      |
                                      v
                           +----------------------+
                           |     API Gateway      |
                           +----------+-----------+
                                      |
      -----------------------------------------------------------------
      |            |             |             |             |          |
      v            v             v             v             v          v
+------------+ +------------+ +------------+ +------------+ +------------+ +----------------+
|Auth Service| |Client      | |Wallet      | |Order       | |Matching    | |Notification    |
|            | |Service     | |Service     | |Service     | |Service     | |Service         |
+------+-----+ +------+-----+ +------+-----+ +------+-----+ +------+-----+ +--------+-------+
       |              |              |              |              |                 |
       v              v              v              v              v                 v
  Auth DB       Client DB      Wallet DB      Order DB     Matching DB     Notification DB
```

## Microservices

### Authentication Service

Responsible for user authentication and authorization. It manages login, registration, JWT authentication, email verification, and multi-factor authentication (MFA).

### Client Service

Manages client information and user profiles. It stores customer-related data and exposes APIs used throughout the platform.

### Wallet Service

Maintains users' wallets and balances. It handles deposits, withdrawals, and updates account balances after completed trades.

### Order Service

Receives buy and sell orders submitted by clients. It validates orders, records them, and forwards eligible orders to the Matching Service.

### Matching Service

Implements the order matching engine. It continuously compares buy and sell orders, executes trades when matching conditions are met, and notifies the appropriate services of completed transactions.

### Notification Service

Sends notifications to users, such as account verification emails, transaction confirmations, and other platform events.

## Technology Stack

### Frontend

* React
* TypeScript
* Nginx

### Backend

* Java
* Spring Boot
* Spring Data JPA
* Hibernate
* REST APIs

### Database

Each microservice owns its own relational database, following the database-per-service pattern. This ensures loose coupling and allows services to evolve independently.

## Design Principles

* **Microservices Architecture:** Each service has a single, well-defined responsibility.
* **Database per Service:** Every microservice manages its own data without direct access to another service's database.
* **Loose Coupling:** Services communicate through REST APIs, reducing dependencies between components.
* **Scalability:** Individual services can be scaled according to demand.
* **Maintainability:** Changes to one service have minimal impact on the rest of the system.
* **Independent Deployment:** Services can be updated and deployed independently.

## Request Flow

1. A user interacts with the React frontend.
2. Requests are sent to the API Gateway.
3. The gateway forwards the request to the appropriate microservice.
4. Each microservice performs its business logic and accesses only its own database.
5. When necessary, services communicate with one another through APIs.
6. Responses are returned through the API Gateway back to the frontend.
