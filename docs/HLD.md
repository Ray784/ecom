1. Introduction

This document outlines the architectural design for a new e-commerce application built using a microservices pattern, implemented with Spring Boot and Java. The architecture emphasizes decoupled services, resilience, and asynchronous communication, primarily utilizing Apache Kafka for critical workflows like order processing and payment.

# 1.1 Goal

The primary goal is to establish a scalable, maintainable, and highly available e-commerce platform by defining clear boundaries and responsibilities for each microservice, along with a strategy for common components and asynchronous communication.

## 1.2 Technology Stack

| Component | Technology/Tool | Purpose |
| --- | --- | --- |
| Backend Framework | Spring Boot 3+ (Java) | Rapid microservice development and dependency management |
| Inter-Service Communication (Sync) | RESTful API (Spring WebFlux/Web) | Standard synchronous communication |
| Inter-Service Communication (Async) | Apache Kafka | Asynchronous messaging for order and payment workflows |
| Service Discovery | Spring Cloud Eureka/Consul | Service registration and lookup |
| Configuration Management | Spring Cloud Config/Vault | Centralized management of application configurations |
| Database (Relational) | MySQL | Transactional data storage (e.g., User, Product, Order details) |
| Database (NoSQL) | Redis/MongoDB/S3 | Non-transactional data storage (e.g., Carts, Session, Cache) |
| API Gateway | Spring Cloud Gateway/Zuul | Single entry point, routing, security, and load balancing |

2. Service Definitions and Responsibilities

The application will be composed of several dedicated microservices. An API Gateway will sit in front of these services to manage external requests.

## 2.1 Core Services (User Defined)

| Service Name | Description | Key Responsibilities | Database |
| --- | --- | --- | --- |
| **API Gateway** | Entry point for all client requests. | Request routing, Load balancing, Authentication enforcement (by communicating with IAM Service), Rate limiting, Circuit breakers. | N/A |
| **Search Service** | Provides fast, relevant product search functionality. | Indexing product data, Handling search queries, Filtering and sorting results. | Elasticsearch (Search Index) |
| **IAM Service** | Identity and Access Management. | Authentication (OAuth2/JWT), Token generation and validation, Role management, Permission definition. | Relational (User credentials, Roles, Permissions) |
| **User Service** | User registration and profile management. | Register new users, Manage user profiles (address, details), Password reset/update. | Relational (User details) |
| **Product Service** | Catalog management and inventory tracking. | Add/Update/Remove products, Product search and retrieval, Inventory management (decrement/increment stock). | Relational (Product catalog, Inventory) |
| **Review Service** | Manages customer product reviews and ratings. | Submit/View product reviews, Calculate aggregate ratings, Moderation. | Relational (Reviews, Ratings) |
| **Order Service** | Handles shopping carts and processing orders. | Manage user carts, Create and track orders, Handle order state transitions (via Kafka). | Relational (Orders, Order items, Cart) |
| **Payment Service** | Integrates with external payment gateways. | Process payments (via Kafka), Handle payment confirmation/failure, Integrate with [smart-chip] Payment Gateway. | Relational (Payment transactions) |
| **Invoice Service** | Generates billing documents. | Calculate billing on demand, Generate and store invoices (PDF/Digital). | Relational (Billing records, Invoice metadata) |
| **Notification Service** | Handles all user notifications. | Send email/SMS notifications, Integrate with external notification providers, Handle asynchronous events from Kafka (e.g., Order Placed, Payment Failed). | NoSQL (Notification logs/templates) |

3. Component Design

## 3.1 Common Library (`commons-jar`)

A dedicated `commons-jar` will be created as a shared library and included as a dependency in all microservices.

### Contents of `commons-jar`:

1. **Request & Response DTOs (Data Transfer Objects):** Standardized response formats for API calls, including success, error, and pagination objects.
1. **Service Clients:** Abstracted clients (e.g., Feign clients) to facilitate synchronous REST communication between services. These clients manage request/response mapping and service discovery integration.
1. **Common Exceptions:** Standardized application exception classes (e.g., `ResourceNotFoundException`, `UnauthorizedException`, `InvalidInputException`) to ensure consistent error handling across all services.
1. **Utility Classes:** General utilities for logging, date formatting, and input validation.
1. **Kafka Message Schemas:** Definition of the data structures (e.g., Avro/JSON schemas) for messages exchanged over Kafka.

## 3.2 Asynchronous Communication using Kafka

Apache Kafka is crucial for decoupling the Order and Payment processes, ensuring high throughput and resilience.

### 3.2.1 Order Placement Workflow

This flow ensures that Order creation is fast and subsequent steps (inventory update, payment initiation) occur asynchronously.


| Step | Initiator | Message Topic | Listener | Action |
| --- | --- | --- | --- | --- |
| 1. Order Creation | Order Service | `order-placed-topic` | Payment Service, Product Service, Notification Service | Payment Service initiates payment; Product Service reserves inventory; Notification Service sends order confirmation. |
| 2. Inventory Update | Product Service | `inventory-reserved-topic` or `inventory-failed-topic` | Order Service | Order Service updates order status based on inventory status. |

### 3.2.2 Payment Processing Workflow

This flow handles the outcome of the payment attempt, which is typically handled by the Payment Service interacting with an external gateway.


| Step | Initiator | Message Topic | Listener | Action |
| --- | --- | --- | --- | --- |
| 1. Payment Attempt Result | Payment Service | `payment-successful-topic` or `payment-failed-topic` | Order Service, Invoice Service, Notification Service | Order Service finalizes order; Invoice Service generates invoice; Notification Service informs user of success/failure. |


\[A diagram illustrating the asynchronous flow: User Service -> Order Service (sync) -> Kafka Topic (Async) -> Payment/Product/Invoice/Notification Services (Async processing)\]

## 3.3 Data Storage Strategy

Each service is responsible for its own database, adhering to the microservices principle of "Database per Service."


| Service | Database Type | Data Description |
| --- | --- | --- |
| IAM Service | Relational | User credentials, roles, JWT token blacklist |
| User Service | Relational | User profile information, addresses |
| Product Service | Relational | Product details, category structure, inventory count |
| Order Service | Relational | Order history, line items, status |
| Payment Service | Relational | Payment transaction logs, gateway references |
| Invoice Service | Relational | Invoice records, billing calculations |
| Review Service | Relational | Review content, rating scores |
| Search Service | NoSQL (Search Index) | Product index for fast lookups |

4. Deployment and Operations

## 4.1 Deployment Strategy

Services will be packaged as Docker containers. Kubernetes will be used for orchestration, scaling, and self-healing capabilities.

## 4.2 Logging and Monitoring

1. **Centralized Logging:** Use the ELK stack (Elasticsearch, Logstash, Kibana) to centralize logs and Prometheus/Grafana for metrics from all services.
1. **Distributed Tracing:** Implement Spring Cloud Sleuth and Zipkin(jaeger) to trace requests across multiple services, which is essential for debugging asynchronous flows.

5. Security

## 5.1 External Security

The API Gateway is the primary point of external security enforcement:


- All requests must pass through the API Gateway.
- The API Gateway communicates with the IAM Service to validate JWT tokens (`/validate` endpoint).
- If the token is valid, the Gateway attaches user identity and permissions to the request header before routing to the downstream service.

## 5.2 Internal Security (Service-to-Service)

Internal service communication must also be secured:


- Use internal, non-routable network segments for service communication.
- Employ mTLS (mutual TLS) for all internal REST calls for encryption and service identity verification.

6. Future Enhancements

| Area | Enhancement | Rationale |
| --- | --- | --- |
| **Sales and Marketing** | **Coupon/Promotion Service** | Handles discount codes, promotional rules, and campaign management. |
| **Personalization** | **Recommendation Service** | Utilizes user behavior and purchase history to suggest products. |
| **API Management** | Use dedicated API Management Platform (e.g., Apigee) | Enhanced governance, security, and developer portal. |
| **Caching** | Implement Redis/Memcached | Cache frequently accessed data (e.g., Product details, IAM tokens) to reduce database load. |


---


*Prepared By: [smart-chip]**Date: [smart-chip]**Version: 1.0*

