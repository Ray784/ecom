# E-Commerce Spring Boot & Kafka Platform — 6-Month Delivery Milestones

This document tracks the 6-month engineering roadmap, architectural milestones, and commit breakdown from **April 2026 to October 2026** across all 11 Maven modules in `ecommerce-spring-kafka`.

---

## Milestone Overview

| Milestone | Window | Theme | Modules Delivered | Commits |
| :--- | :--- | :--- | :--- | :--- |
| **M1** | Apr 2026 | Platform Foundation, Shared Contracts, Identity & Profiles | `docs`, `commons-jar`, `iam-service`, `user-service` | 4 |
| **M2** | May 2026 | Product Catalog, Inventory Reservation & Search Indexing | `product-service`, `search-service` | 3 |
| **M3** | Jun 2026 | Shopping Cart, Order Lifecycle & Asynchronous Kafka Saga | `order-service` | 3 |
| **M4** | Jul 2026 | Payment Processing, PDF Invoicing & Notification Engine | `payment-service`, `invoice-service`, `notification-service` | 3 |
| **M5** | Aug–Sep 2026 | Customer Reviews, Aggregate Ratings & Unified API Gateway | `review-service`, `api-gateway` | 3 |
| **M6** | Oct 2026 | Full Reactor Test Suite, Live Multi-Server E2E & Reports | All 11 modules, `test/` | 4 |

---

## Detailed Milestone Breakdown

### Milestone 1 — Platform Foundation, Shared Contracts & Identity Services
**Target Dates:** `2026-04-10` to `2026-04-28`

- **M1.1 (`2026-04-10`) — Architecture Specs & Multi-Module Maven Parent:**
  - Authored `docs/HLD.md`, `docs/LLD.md`, and `docs/ERROR_CATALOG.md` defining the 10-microservice topology, database schemas, Kafka event contracts, and 78+ standardized error codes.
  - Configured root `pom.xml` with Spring Boot `3.3.4`, Java 21 compiler target, managed dependencies, and `.gitignore`.
- **M1.2 (`2026-04-16`) — Shared `commons-jar` Library:**
  - Implemented `BaseResponseDTO`, `ErrorResponseDTO`, `PaginationDTO`, domain request/response DTOs across all services, Kafka event payloads (`OrderPlacedEvent`, `InventoryReservationRequest`, `InventoryReservedEvent`, `PaymentResultEvent`), `KafkaTopicNames`, stateless HMAC-SHA256 `JwtUtil`, exception hierarchy (`ApplicationBaseException`, `ApiExternalException`, `ApiInternalException`), `MappedError` catalog, `CodedErrorFactory`, `ErrorMapper`, and `GlobalExceptionHandler`.
- **M1.3 (`2026-04-22`) — Identity & Access Management (`iam-service`, Port 8081):**
  - Implemented user credential storage, BCrypt password hashing, role assignment (`ROLE_CUSTOMER`, `ROLE_ADMIN`, `ROLE_SERVICE`), seeded `admin` user, JWT issuance and validation (`POST /auth/login`, `POST /auth/validate`, `POST /users/register`, `PUT /users/{userId}/password`, `POST /users/{userId}/lock`), and optional embedded Kafka broker hosting on port `9092`.
- **M1.4 (`2026-04-28`) — Customer Profile & Address Management (`user-service`, Port 8082):**
  - Implemented `UserProfile` and `Address` JPA entities, automatic profile initialization via `IamClient`, ownership verification (`USER_4007`), address limit/default rules (`USER_4003`, `USER_4006`), and REST endpoints (`GET/PUT /profiles/{id}`, `POST/DELETE /profiles/{id}/addresses`).

---

### Milestone 2 — Product Catalog, Inventory Reservation & Search Service
**Target Dates:** `2026-05-06` to `2026-05-26`

- **M2.1 (`2026-05-06`) — Product Catalog & Optimistic-Locked Inventory (`product-service`, Port 8083):**
  - Implemented `Product`, `Category`, and `@Version`-guarded `Inventory` JPA models, seeded standard categories (`ELECTRONICS`, `BOOKS`, `CLOTHING`, `HOME`, `SPORTS`), and CRUD endpoints (`POST/GET/PUT /products`, `GET/POST /categories`, `POST /inventory/reserve`).
- **M2.2 (`2026-05-15`) — Product Kafka Saga Consumer/Producer & Search Index Hook:**
  - Added `KafkaListenerService` consuming `order-placed-topic`, atomic all-or-nothing stock reservation, `KafkaProducerService` publishing `InventoryReservedEvent` to `inventory-reserved-topic` or `inventory-failed-topic`, and automatic synchronous indexing push to `search-service`.
- **M2.3 (`2026-05-26`) — Full-Text Product Search & Filtering (`search-service`, Port 8089):**
  - Implemented `ProductSearchIndex`, `ProductIndexStore`, `ProductDataIndexer` (`POST /search/index`), and `SearchQueryService` (`GET /search/products`) supporting keyword tokenization, multi-category filtering, sorting by `price`/`name`/`sku`, and pagination (`SEARCH_4001`..`SEARCH_5003`).

---

### Milestone 3 — Shopping Cart, Order Lifecycle & Kafka Saga Coordination
**Target Dates:** `2026-06-08` to `2026-06-25`

- **M3.1 (`2026-06-08`) — Shopping Cart & Live Catalog Price Resolution (`order-service`, Port 8084):**
  - Implemented `Cart` and `CartItem` entities, `ProductServiceClient` for live price/stock verification against `product-service`, TTL expiration checks, max item limits (`ORDER_4009`), and cart endpoints (`POST /carts/items`, `GET /carts`).
- **M3.2 (`2026-06-17`) — Order Creation & State Machine (`order-service`):**
  - Implemented `Order`, `OrderItem`, `OrderStatus` state machine (`PENDING` -> `INVENTORY_RESERVED` -> `PAID` / `FAILED` / `CANCELLED`), total verification (`ORDER_4008`), shipping address validation (`ORDER_4007`), and REST endpoints (`POST /orders`, `GET /orders/{orderId}`, `PUT /orders/{orderId}/cancel`).
- **M3.3 (`2026-06-25`) — Order Kafka Event Producer & Saga Listeners:**
  - Added `KafkaProducerService` publishing `OrderPlacedEvent` to `order-placed-topic` and `KafkaListenerService` consuming `inventory-reserved-topic`, `inventory-failed-topic`, `payment-successful-topic`, and `payment-failed-topic`.

---

### Milestone 4 — Payment Processing, PDF Invoicing & Notification Engine
**Target Dates:** `2026-07-07` to `2026-07-28`

- **M4.1 (`2026-07-07`) — Payment Processing & Gateway Simulation (`payment-service`, Port 8085):**
  - Implemented `PaymentTransaction` persistence, `SimulatedPaymentGateway`, `GatewayErrorMapper`, `PaymentProcessorService`, `KafkaListenerService` on `order-placed-topic`, `KafkaProducerService` publishing `PaymentResultEvent` to `payment-successful-topic`/`payment-failed-topic`, and inspection endpoint `GET /payments/order/{orderId}`.
- **M4.2 (`2026-07-17`) — Automated Tax/Discount Billing & PDF Invoice Generator (`invoice-service`, Port 8086):**
  - Implemented `Invoice` and `BillingRecord` models, tiered discount (5% on orders >= 100) and tax (8%) calculation, real PDF document writer storing `/tmp/invoices/invoice-{orderId}.pdf`, Kafka listener on `payment-successful-topic`, and `GET /invoices/{orderId}`.
- **M4.3 (`2026-07-28`) — Multi-Channel Template Notification Service (`notification-service`, Port 8087):**
  - Implemented `NotificationLog`, `NotificationTemplate` (`ORDER_PLACED_TEMPLATE`, `PAYMENT_SUCCESS_TEMPLATE`, `PAYMENT_FAILED_TEMPLATE`), `NotificationSenderService`, Kafka listeners for order and payment events, and query endpoints (`GET /notifications/order/{orderId}`, `GET /notifications/user/{userId}`).

---

### Milestone 5 — Customer Reviews, Ratings & Unified API Gateway
**Target Dates:** `2026-08-14` to `2026-09-22`

- **M5.1 (`2026-08-14`) — Product Reviews, Moderation & Aggregate Ratings (`review-service`, Port 8088):**
  - Implemented `Review` and `AggregateRating` entities, prohibited word moderation (`REVIEW_4001`), duplicate review prevention (`REVIEW_4002`), rating range enforcement (`REVIEW_4003`), and endpoints (`POST /reviews`, `GET /reviews/products/{productId}`, `GET /reviews/products/{productId}/rating`, `GET /reviews/{id}`).
- **M5.2 (`2026-09-04`) — API Gateway Routing & Downstream Proxy (`api-gateway`, Port 8080):**
  - Implemented `RouteResolver`, `ServiceId`, `GatewayRouteProperties`, `DownstreamProxyClient`, and `GatewayController` forwarding public and protected routes across ports `8081..8089`.
- **M5.3 (`2026-09-22`) — Gateway JWT Authentication & Role-Based Access Control:**
  - Implemented `AccessControlService` and `IamAuthClient` validating Bearer tokens against `iam-service` `/auth/validate` with local `JwtUtil` fallback, enforcing `ROLE_CUSTOMER`/`ROLE_ADMIN` (`IAM_4002`, `IAM_4008`), stripping spoofed headers, and injecting trusted `X-User-Id`, `X-User-Name`, and `X-User-Roles` headers.

---

### Milestone 6 — Comprehensive Unit/Integration Tests, Live E2E Harness & Final Reports
**Target Dates:** `2026-10-01` to `2026-10-10`

- **M6.1 (`2026-10-01`) — Foundation, IAM, User, Product & Search Test Suites:**
  - Added 571 unit and controller integration tests across `commons-jar` (351), `iam-service` (55), `user-service` (42), `product-service` (74), and `search-service` (25).
- **M6.2 (`2026-10-05`) — Order, Payment, Invoice, Notification, Review & Gateway Test Suites:**
  - Added 344 unit and integration tests across `order-service` (79), `payment-service` (56), `invoice-service` (52), `notification-service` (45), `review-service` (39), and `api-gateway` (73), bringing the reactor total to **915 passing tests**.
- **M6.3 (`2026-10-08`) — Multi-Process Live Server E2E Verification Harness (`test/run_e2e_live.sh`):**
  - Created repeatable bash harness booting embedded Kafka (`localhost:9092`) and all 10 Spring Boot microservices (`localhost:8080..8089`), executing 66 live HTTP assertions covering full order-to-invoice Kafka saga and 13 Error Catalog negative cases, and cleanly terminating all server processes.
- **M6.4 (`2026-10-10`) — E2E Verification Report, Milestones Documentation & Repository README:**
  - Authored `test/E2E_TEST_REPORT.md`, `test/MILESTONES.md`, and `README.md` with full test metrics, live curl request/response transcripts, and architecture documentation.
