# E-Commerce Microservices Platform (`ecommerce-spring-kafka`)

A self-contained, event-driven E-Commerce Microservices Platform built with **Java 21**, **Spring Boot 3.3.4**, **Spring Kafka**, **Spring Data JPA**, and **H2 Database**.

## Modules & Ports

| Module | Port | Tests | Description |
| :--- | :--- | ---: | :--- |
| `commons-jar` | — | 351 | Shared DTOs, 83-code `MappedError` catalog, `CodedErrorFactory`, `ErrorMapper`, `GlobalExceptionHandler`, `JwtUtil`, `KafkaTopicNames` |
| `api-gateway` | `8080` | 87 | Unified HTTP entry point, JWT validation via `iam-service`, RBAC (`ROLE_CUSTOMER`, `ROLE_ADMIN`), Razorpay Basic Auth & Webhook routing |
| `iam-service` | `8081` | 62 | Authentication, registration, BCrypt credentials, self/admin password update RBAC, JWT issuance/validation, Embedded Kafka Broker on `9092` |
| `user-service` | `8082` | 42 | User profiles and shipping address management |
| `product-service` | `8083` | 92 | Product catalog, categories, optimistic-locked inventory reservation, post-payment stock decrement & release, search index push |
| `order-service` | `8084` | 87 | Shopping cart, asynchronous Kafka order saga state machine, verified purchase endpoint (`GET /orders/verified-purchase`) |
| `payment-service` | `8085` | 122 | Fake Razorpay emulator (`/v1/orders`, `/v1/payments`), HMAC-SHA256 webhook verification (`/payments/razorpay/webhook`), Kafka `PaymentResultEvent` publisher |
| `invoice-service` | `8086` | 52 | Discount/tax calculation and PDF invoice generation (`/tmp/invoices`) |
| `notification-service` | `8087` | 45 | Template-based email/SMS notification dispatch and audit logs |
| `review-service` | `8088` | 56 | Customer product reviews, product existence & verified purchaser checks (`REVIEW_4004`, `REVIEW_4006`), content moderation, and aggregate ratings |
| `search-service` | `8089` | 25 | In-memory full-text product search, category filtering, sorting, and pagination |
| **Total Reactor** | — | **1,021** | **100% Passing (0 Failures, 0 Errors, 0 Skipped)** |

## Build & Run Unit/Integration Tests

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B clean test
```

## Run Live Multi-Server End-to-End Verification (113 Live Assertions)

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash test/run_e2e_live.sh
```

## Documentation & Reports

- [`docs/HLD.md`](docs/HLD.md) — High-Level Architecture Design
- [`docs/LLD.md`](docs/LLD.md) — Low-Level Component & API Design
- [`docs/ERROR_CATALOG.md`](docs/ERROR_CATALOG.md) — Standardized Coded Error Catalog (83 error codes)
- [`test/TEST_PLAN.md`](test/TEST_PLAN.md) — Master Test Plan (Parts 1–4: Module Matrix, 83-Code Catalog Coverage, Fake Razorpay, Kafka Stock Settlement, Verified Purchaser & Password RBAC)
- [`test/E2E_TEST_REPORT.md`](test/E2E_TEST_REPORT.md) — Unit, Integration & Live E2E Test Report (1,021 Unit/Integration + 113 Live E2E Assertions)
- [`test/MILESTONES.md`](test/MILESTONES.md) — 6-Month Engineering Milestones (`M1`–`M6`, 24 Commits)
