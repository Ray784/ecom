# E-Commerce Microservices Platform (`ecommerce-spring-kafka`)

A self-contained, event-driven E-Commerce Microservices Platform built with **Java 21**, **Spring Boot 3.3.4**, **Spring Kafka**, **Spring Data JPA**, and **H2 Database**.

## Modules & Ports

| Module | Port | Description |
| :--- | :--- | :--- |
| `commons-jar` | — | Shared DTOs, 83-code `MappedError` catalog, `CodedErrorFactory`, `ErrorMapper`, `GlobalExceptionHandler`, `JwtUtil`, `KafkaTopicNames` |
| `api-gateway` | `8080` | Unified HTTP entry point, JWT validation via `iam-service`, RBAC (`ROLE_CUSTOMER`, `ROLE_ADMIN`), request routing |
| `iam-service` | `8081` | Authentication, registration, BCrypt credentials, JWT issuance/validation, optional Embedded Kafka Broker on `9092` |
| `user-service` | `8082` | User profiles and shipping address management |
| `product-service` | `8083` | Product catalog, categories, optimistic-locked inventory reservation, search index push |
| `order-service` | `8084` | Shopping cart and asynchronous Kafka order saga state machine |
| `payment-service` | `8085` | Order payment processing and `PaymentResultEvent` Kafka publisher |
| `invoice-service` | `8086` | Discount/tax calculation and PDF invoice generation (`/tmp/invoices`) |
| `notification-service` | `8087` | Template-based email/SMS notification dispatch and audit logs |
| `review-service` | `8088` | Customer product reviews, content moderation, and aggregate ratings |
| `search-service` | `8089` | In-memory full-text product search, category filtering, sorting, and pagination |

## Build & Run Unit/Integration Tests

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B clean test
```

## Run Live Multi-Server End-to-End Verification

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 bash test/run_e2e_live.sh
```

## Documentation & Reports

- [`docs/HLD.md`](docs/HLD.md) — High-Level Architecture Design
- [`docs/LLD.md`](docs/LLD.md) — Low-Level Component & API Design
- [`docs/ERROR_CATALOG.md`](docs/ERROR_CATALOG.md) — Standardized Coded Error Catalog
- [`test/E2E_TEST_REPORT.md`](test/E2E_TEST_REPORT.md) — Unit, Integration & Live E2E Test Report
- [`test/MILESTONES.md`](test/MILESTONES.md) — 6-Month Engineering Milestones (`M1`–`M6`)
