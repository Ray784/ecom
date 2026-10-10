# End-to-End & Reactor Verification Report (`ecommerce-spring-kafka`)

**Date:** `2026-10-10`  
**Repository:** `/usr/local/google/home/abhaybhat/ecommerce-spring-kafka`  
**Java Runtime:** OpenJDK 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`)  
**Spring Boot Version:** `3.3.4` (`spring-kafka` `3.2.4`, `H2` in-memory MySQL mode)  
**Master Test Plan:** [`test/TEST_PLAN.md`](./TEST_PLAN.md)  
**Overall Status:** **PASS** (**1,021/1,021** Unit & Integration Tests Passed, **113/113** Live Multi-Server E2E Assertions Passed)

---

## 1. Architecture, Port, Database & Kafka Topology

### 1.1 Microservice Topology

| Module | Port | Storage / State | Role & Key Responsibilities |
| :--- | :--- | :--- | :--- |
| `commons-jar` | Library | Stateless | 27 shared DTOs, 83 `MappedError` codes, `CodedErrorFactory`, `ErrorMapper`, `GlobalExceptionHandler`, `JwtUtil`, `KafkaTopicNames` |
| `api-gateway` | `8080` | Stateless | Single entry point, JWT validation via `iam-service` `/auth/validate`, RBAC (`ROLE_CUSTOMER`, `ROLE_ADMIN`), header propagation (`X-User-Id`, `X-User-Roles`), Razorpay Basic Auth & Webhook header pass-through |
| `iam-service` | `8081` | `jdbc:h2:mem:iamdb` | User credentials, BCrypt password hashing, role assignment, JWT issuance/validation/revocation, self/admin password update RBAC (`PUT /users/{id}/password`), optional Embedded Kafka Broker host on port `9092` |
| `user-service` | `8082` | `jdbc:h2:mem:userdb` | Customer profiles, shipping address book (max 5, default handling), auto-initialization from `iam-service` |
| `product-service` | `8083` | `jdbc:h2:mem:productdb` | Product catalog, seeded categories, optimistic-locked (`@Version`) inventory reservation, post-payment stock decrement & failed-payment stock release, search auto-indexing push |
| `order-service` | `8084` | `jdbc:h2:mem:orderdb` | Shopping cart (`Cart`, `CartItem`), order lifecycle state machine (`PENDING` -> `INVENTORY_RESERVED` -> `PAID`/`FAILED`/`CANCELLED`), verified purchase query (`GET /orders/verified-purchase`) |
| `payment-service` | `8085` | `jdbc:h2:mem:paymentdb` | Fake Razorpay Orders & Payments Emulator (`/v1/orders`, `/v1/payments` with Basic Auth `rzp_test_key_id:rzp_test_key_secret`), HMAC-SHA256 webhook verification (`POST /payments/razorpay/webhook`), `PaymentResultEvent` publisher |
| `invoice-service` | `8086` | `jdbc:h2:mem:invoicedb` + `/tmp/invoices` | Tiered discount (5% >= $100) & tax (8%) calculation, PDF invoice generator (`/tmp/invoices/invoice-{orderId}.pdf`) |
| `notification-service` | `8087` | In-memory store | Template-driven email/SMS dispatch (`ORDER_PLACED_TEMPLATE`, `PAYMENT_SUCCESS_TEMPLATE`, `PAYMENT_FAILED_TEMPLATE`) |
| `review-service` | `8088` | `jdbc:h2:mem:reviewdb` | Product reviews, product existence check (`REVIEW_4004`), verified purchaser enforcement (`REVIEW_4006`), prohibited content moderation (`REVIEW_4001`), duplicate prevention (`REVIEW_4002`), aggregate rating calculation |
| `search-service` | `8089` | In-memory index | Tokenized full-text product search, category filtering, sorting (`price`, `name`, `sku`), and pagination |

### 1.2 Kafka Event Pipeline (`localhost:9092`)

| Topic Name | Producer | Consumers | Event Payload |
| :--- | :--- | :--- | :--- |
| `order-placed-topic` | `order-service` | `product-service`, `payment-service`, `notification-service` | `OrderPlacedEvent` (`orderId`, `userId`, `items`, `totalAmount`) |
| `inventory-reserved-topic` | `product-service` | `order-service` | `InventoryReservedEvent` (`orderId`, `success=true`, `message`) |
| `inventory-failed-topic` | `product-service` | `order-service` | `InventoryReservedEvent` (`orderId`, `success=false`, `message`) |
| `payment-successful-topic` | `payment-service` | `product-service`, `order-service`, `invoice-service`, `notification-service` | `PaymentResultEvent` (`orderId`, `transactionId`, `success=true`) |
| `payment-failed-topic` | `payment-service` | `product-service`, `order-service`, `notification-service` | `PaymentResultEvent` (`orderId`, `transactionId`, `success=false`, `failureReason`) |

---

## 2. Unit & Integration Test Suite Summary

Command executed:
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B clean test
```
Log: `/tmp/swarm_ecom1/int_mvn_test.log` (see [`test/TEST_PLAN.md`](./TEST_PLAN.md) for the full 48-class & 83-error-code breakdown).

| # | Maven Module | Tests Run | Failures | Errors | Skipped | Status |
| :--- | :--- | ---: | ---: | ---: | ---: | :--- |
| 1 | `commons-jar` | 351 | 0 | 0 | 0 | **PASS** |
| 2 | `iam-service` | 62 | 0 | 0 | 0 | **PASS** |
| 3 | `user-service` | 42 | 0 | 0 | 0 | **PASS** |
| 4 | `product-service` | 92 | 0 | 0 | 0 | **PASS** |
| 5 | `search-service` | 25 | 0 | 0 | 0 | **PASS** |
| 6 | `order-service` | 87 | 0 | 0 | 0 | **PASS** |
| 7 | `payment-service` | 122 | 0 | 0 | 0 | **PASS** |
| 8 | `invoice-service` | 52 | 0 | 0 | 0 | **PASS** |
| 9 | `notification-service` | 45 | 0 | 0 | 0 | **PASS** |
| 10 | `review-service` | 56 | 0 | 0 | 0 | **PASS** |
| 11 | `api-gateway` | 87 | 0 | 0 | 0 | **PASS** |
| — | **Total Reactor** | **1,021** | **0** | **0** | **0** | **BUILD SUCCESS** |

---

## 3. Live Multi-Server E2E Verification (`test/run_e2e_live.sh`)

Command executed:
```bash
bash test/run_e2e_live.sh
```
Log: `/tmp/swarm_ecom1/int_e2e_live.log` (and `/tmp/swarm_ecom1/int.log`)

### 3.1 Multi-Process Server Boot Sequence
All 10 Spring Boot executable JARs were packaged and launched simultaneously against the live Embedded Kafka broker on `localhost:9092`:
- `embedded-kafka`: listening on port `9092` (5 topics auto-created)
- `iam-service`: listening on port `8081`
- `user-service`: listening on port `8082`
- `product-service`: listening on port `8083`
- `order-service`: listening on port `8084`
- `payment-service`: listening on port `8085`
- `invoice-service`: listening on port `8086`
- `notification-service`: listening on port `8087`
- `review-service`: listening on port `8088`
- `search-service`: listening on port `8089`
- `api-gateway`: listening on port `8080`

### 3.2 Positive End-to-End API Flows & Captured Payloads

#### A. Customer Registration & Authentication (`POST /users/register`, `POST /auth/login`, `POST /auth/validate`)
- **Request:** `POST http://localhost:8080/users/register` (`alice` and `bob`)
- **Response (`HTTP 201`):**
  ```json
  {"success":true,"statusCode":201,"message":"User registered successfully","userId":"e3495086-2f5e-4bd6-9af8-3a207c61be85","username":"alice","roles":["ROLE_CUSTOMER"],"email":"alice@example.com","valid":true}
  ```
- **Login (`POST http://localhost:8080/auth/login`, `HTTP 200`):** Issued signed HMAC-SHA256 JWT tokens for `admin`, `alice`, and `bob`.
- **Validate (`POST http://localhost:8081/auth/validate`, `HTTP 200`):**
  ```json
  {"success":true,"statusCode":200,"message":"Token is valid","userId":"e3495086-2f5e-4bd6-9af8-3a207c61be85","username":"alice","roles":["ROLE_CUSTOMER"],"email":"alice@example.com","valid":true}
  ```

#### B. Customer Profile & Shipping Address Management (`GET/PUT /profiles/{id}`, `POST /profiles/{id}/addresses`)
- **Auto-Initialized & Updated Profile (`PUT /profiles/e3495086-2f5e-4bd6-9af8-3a207c61be85`, `HTTP 200`):**
  ```json
  {"success":true,"statusCode":200,"message":"Success","userId":"e3495086-2f5e-4bd6-9af8-3a207c61be85","firstName":"Alice","lastName":"Wonderland","email":"alice@example.com","addresses":[]}
  ```
- **Added Default Shipping Address (`POST /profiles/e3495086-2f5e-4bd6-9af8-3a207c61be85/addresses`, `HTTP 201`):**
  ```json
  {"id":1,"street":"742 Evergreen Terrace","city":"Springfield","zipCode":"62704","isDefault":true}
  ```

#### C. Admin Product Catalog & Auto-Indexing into Search (`POST/PUT/GET /products`, `GET /search/products`)
- **Admin Created & Updated Product (`PUT http://localhost:8080/products/SKU-LAPTOP-01`, `HTTP 200`):**
  ```json
  {"success":true,"statusCode":200,"message":"Product SKU-LAPTOP-01 updated","sku":"SKU-LAPTOP-01","name":"Pro Ultrabook 15 Gen2","description":"Updated developer laptop with 32GB RAM","price":1250.00,"categoryId":"ELECTRONICS","currentStock":15,"reservedStock":0,"averageRating":null}
  ```
- **Search via Gateway (`GET http://localhost:8080/search/products?q=Ultrabook&category=ELECTRONICS&sortField=price&sortOrder=asc`, `HTTP 200`):**
  ```json
  {"success":true,"statusCode":200,"message":"Found 1 products","pagination":{"page":0,"size":10,"totalElements":1,"totalPages":1},"results":[{"sku":"SKU-LAPTOP-01","name":"Pro Ultrabook 15 Gen2","description":"Updated developer laptop with 32GB RAM","price":1250.00,"categoryId":"ELECTRONICS","imageUrl":"https://cdn.ecommerce.local/laptop01-v2.png"}]}
  ```

#### D. Shopping Cart, Order Placement & Post-Payment Stock Decrement Saga (`Converged in 1s`)
1. **Order Created (`POST http://localhost:8080/orders`, `HTTP 201`):**
   ```json
   {"success":true,"statusCode":201,"message":"Order created","orderId":"c43b5ba3-e377-4ef1-9869-ffb63a174615","userId":"e3495086-2f5e-4bd6-9af8-3a207c61be85","status":"PENDING","totalAmount":1350.00,"shippingAddress":"742 Evergreen Terrace, Springfield, IL 62704","items":[{"sku":"SKU-LAPTOP-01","quantity":1,"unitPrice":1250.00},{"sku":"SKU-MOUSE-02","quantity":2,"unitPrice":50.00}],"creationDate":"2026-10-10T09:59:44.462975678Z"}
   ```
2. **Order Status Transitioned to `PAID` & Verified Purchase Confirmed (`GET /orders/c43b5ba3-e377-4ef1-9869-ffb63a174615`, `GET /orders/verified-purchase`):**
   ```json
   {"userId":"e3495086-2f5e-4bd6-9af8-3a207c61be85","sku":"SKU-LAPTOP-01","verified":true}
   ```
3. **Post-Payment Stock Decremented & Reserved Stock Settled (`GET /products/SKU-LAPTOP-01`, `HTTP 200`):**
   - `SKU-LAPTOP-01`: `currentStock` decremented `15 -> 14`, `reservedStock` settled `1 -> 0`
   - `SKU-MOUSE-02`: `currentStock` decremented `30 -> 28`, `reservedStock` settled `2 -> 0`
   ```json
   {"success":true,"statusCode":200,"message":"OK","sku":"SKU-LAPTOP-01","name":"Pro Ultrabook 15 Gen2","description":"Updated developer laptop with 32GB RAM","price":1250.00,"categoryId":"ELECTRONICS","currentStock":14,"reservedStock":0,"averageRating":null}
   ```
4. **Payment Processed via Fake Razorpay (`GET /payments/order/c43b5ba3-e377-4ef1-9869-ffb63a174615`, `HTTP 200`):**
   ```json
   {"transactionId":"TXN-597cdaac-d710-44ef-8122-17b5835265d9","orderId":"c43b5ba3-e377-4ef1-9869-ffb63a174615","userId":"e3495086-2f5e-4bd6-9af8-3a207c61be85","amount":1350.00,"gatewayRef":"pay_38F0NfldwiBE7g","status":"SUCCESS","failureReason":null,"createdAt":"2026-10-10T09:59:44.827045Z","razorpayOrderId":"order_SQMOgxjgNVTjHf"}
   ```
5. **Invoice & PDF Generated (`GET /invoices/c43b5ba3-e377-4ef1-9869-ffb63a174615`, `HTTP 200`):**
   ```json
   {"success":true,"statusCode":200,"message":"Invoice available for order c43b5ba3-e377-4ef1-9869-ffb63a174615","id":"INV-1f3bf36b-1666-45e5-bff2-bd133b62a17f","orderId":"c43b5ba3-e377-4ef1-9869-ffb63a174615","pdfStorageLink":"/tmp/invoices/invoice-c43b5ba3-e377-4ef1-9869-ffb63a174615.pdf","subtotal":1350.00,"discount":67.50,"taxAmount":102.60,"finalTotal":1385.10,"issueDate":"2026-10-10T09:59:45.231870Z"}
   ```
6. **Notifications Dispatched (`GET /notifications/order/c43b5ba3-e377-4ef1-9869-ffb63a174615`, `HTTP 200`):**
   - Verified both `ORDER_PLACED_TEMPLATE` and `PAYMENT_SUCCESS_TEMPLATE` email notifications were recorded with `status="SENT"`.

#### E. Fake Razorpay Emulator (`/v1/orders`, `/v1/payments`) & HMAC-SHA256 Webhook (`POST /payments/razorpay/webhook`)
- **Unauthenticated `POST /v1/orders` (`HTTP 400 PAY_4002`):**
  ```json
  {"httpStatusCode":400,"errorCode":"PAY_4002","errorDetails":"Invalid payment method or details provided.","timestamp":"2026-10-10T09:59:47.069937756Z"}
  ```
- **Authenticated `POST /v1/orders` (`-u rzp_test_key_id:rzp_test_key_secret`, `HTTP 200`):**
  ```json
  {"id":"order_GZ3xTaMQlFEosS","entity":"order","amount":50000,"amount_paid":0,"amount_due":50000,"currency":"INR","receipt":"rcpt-e2e-1","notes":{},"status":"created","attempts":0,"created_at":1791626387}
  ```
- **Authenticated `POST /v1/payments` (`-u rzp_test_key_id:rzp_test_key_secret`, `HTTP 200`):**
  ```json
  {"id":"pay_4m1Cu1k5C7Hgc8","entity":"payment","amount":50000,"currency":"INR","order_id":"order_GZ3xTaMQlFEosS","receipt":"rcpt-e2e-1","method":"card","payer_user_id":"e3495086-2f5e-4bd6-9af8-3a207c61be85","description":null,"created_at":1791626387,"status":"captured","captured":true,"error_code":null,"error_description":null}
  ```
- **Invalid Webhook Signature (`X-Razorpay-Signature: deadbeef`, `HTTP 400 PAY_4002`):**
  ```json
  {"httpStatusCode":400,"errorCode":"PAY_4002","errorDetails":"Invalid payment method or details provided.","timestamp":"2026-10-10T09:59:47.369899899Z"}
  ```
- **Valid HMAC-SHA256 Webhook Signature (`X-Razorpay-Signature` signed with `rzp_test_webhook_secret`, `HTTP 200`):**
  ```json
  {"status":"already_processed","event":"payment.captured","transactionId":"TXN-597cdaac-d710-44ef-8122-17b5835265d9","orderId":"c43b5ba3-e377-4ef1-9869-ffb63a174615","paymentStatus":"SUCCESS","gatewayRef":"pay_38F0NfldwiBE7g"}
  ```

#### F. Payment-Failed Compensation Saga & Reserved Stock Release (`user-decline-01`)
1. **Order Placed (`qty=2` of `SKU-LAPTOP-01`, `orderId=ff6d19bc-0cb6-4d5b-b7cb-e38d06db200e`):**
   - `product-service` reserved 2 units (`reservedStock: 0 -> 2`, `currentStock: 14`).
2. **Payment Declined (`GET /payments/order/ff6d19bc-0cb6-4d5b-b7cb-e38d06db200e`, `HTTP 200`):**
   ```json
   {"transactionId":"TXN-474e7bf1-767c-4067-ba2f-aa8b0a7e5497","orderId":"ff6d19bc-0cb6-4d5b-b7cb-e38d06db200e","userId":"user-decline-01","amount":2500.00,"gatewayRef":"pay_Gs4dFbPYO5SLNo","status":"FAILED","failureReason":"PAY_4001: Payment failed for Order ff6d19bc-0cb6-4d5b-b7cb-e38d06db200e: Card declined by gateway.","createdAt":"2026-10-10T09:59:47.503848Z","razorpayOrderId":"order_Qg5xcpcYIrXZfG"}
   ```
3. **Reserved Stock Released & Order Marked `FAILED` (`GET /products/SKU-LAPTOP-01`, `GET /orders/ff6d19bc-0cb6-4d5b-b7cb-e38d06db200e`):**
   - `product-service` consumed `payment-failed-topic` and released `reservedStock` back to `0` while keeping `currentStock` intact at `14`:
   ```json
   {"success":true,"statusCode":200,"message":"OK","sku":"SKU-LAPTOP-01","name":"Pro Ultrabook 15 Gen2","description":"Updated developer laptop with 32GB RAM","price":1250.00,"categoryId":"ELECTRONICS","currentStock":14,"reservedStock":0,"averageRating":null}
   ```

#### G. Gateway Password Update & Self/Admin Ownership Enforcement (`PUT /users/{id}/password`)
- **Cross-User Password Update Blocked (`PUT /users/{bobId}/password` with `alice` token, `HTTP 403 IAM_4008`):**
  ```json
  {"httpStatusCode":403,"errorCode":"IAM_4008","errorDetails":"User e3495086-2f5e-4bd6-9af8-3a207c61be85 does not possess the required role: ROLE_ADMIN.","timestamp":"2026-10-10T09:59:49.653371885Z"}
  ```
- **Self Password Update & Re-Login (`PUT /users/{aliceId}/password` with `alice` token, `HTTP 200`):**
  ```json
  {"success":true,"statusCode":200,"message":"Password updated"}
  ```
  Followed by `POST /auth/login` with `"AliceNew@5678"` returning `HTTP 200` and a fresh JWT token.

---

### 3.3 Negative Error Catalog Verification Table (Live Multi-Server E2E)

| Error Code | HTTP Status | Endpoint Triggered | Live Response `errorDetails` | Result |
| :--- | :---: | :--- | :--- | :---: |
| `IAM_4001` | `401` | `POST /auth/login` (wrong password) | `Invalid username or password provided.` | **PASS** |
| `IAM_4002` | `401` | `GET /orders/{id}` (invalid JWT) | `Access Token is missing, invalid, or expired.` | **PASS** |
| `IAM_4004` | `400` | `POST /users/register` (duplicate `alice`) | `Username alice is already taken.` | **PASS** |
| `IAM_4008` | `403` | `POST /products` (customer token on admin route) | `User alice does not possess the required role: ROLE_ADMIN.` | **PASS** |
| `IAM_4008` | `403` | `PUT /users/{bobId}/password` (customer updating another user) | `User e3495086-2f5e-4bd6-9af8-3a207c61be85 does not possess the required role: ROLE_ADMIN.` | **PASS** |
| `USER_4007` | `403` | `PUT /profiles/{bobId}` (using `alice` token) | `Cannot modify profile 76364b1c-f737-4aa5-b64d-0140ea64334f: access denied.` | **PASS** |
| `PROD_4001` | `404` | `GET /products/SKU-DOES-NOT-EXIST` | `Product with SKU SKU-DOES-NOT-EXIST not found in the catalog.` | **PASS** |
| `PROD_4002` | `400` | `POST /inventory/reserve` (`qty=999` > `14`) | `Insufficient stock for product SKU-LAPTOP-01. Only 14 available.` | **PASS** |
| `ORDER_4001` | `400` | `POST /orders` (after cart cleared) | `Cannot place order: cart is empty for user e3495086-2f5e-4bd6-9af8-3a207c61be85.` | **PASS** |
| `PAY_4002` | `400` | `POST /v1/orders` (missing HTTP Basic Auth) | `Invalid payment method or details provided.` | **PASS** |
| `PAY_4002` | `400` | `POST /payments/razorpay/webhook` (invalid HMAC signature) | `Invalid payment method or details provided.` | **PASS** |
| `INV_4001` | `404` | `GET /invoices/ORD-NONEXISTENT-999` | `Invoice for Order ORD-NONEXISTENT-999 has not yet been generated.` | **PASS** |
| `REVIEW_4001` | `400` | `POST /reviews` (prohibited word `spam`) | `Review contains prohibited language or content.` | **PASS** |
| `REVIEW_4002` | `409` | `POST /reviews` (second review on `SKU-LAPTOP-01`) | `User e3495086-2f5e-4bd6-9af8-3a207c61be85 has already submitted a review for product SKU-LAPTOP-01.` | **PASS** |
| `REVIEW_4003` | `400` | `POST /reviews` (`rating=9`) | `Rating value 9 is outside the allowed range (1 to 5).` | **PASS** |
| `REVIEW_4004` | `404` | `POST /reviews` (non-existent product `SKU-GHOST-999`) | `Product SKU-GHOST-999 does not exist, cannot submit review.` | **PASS** |
| `REVIEW_4006` | `403` | `POST /reviews` (`bob` not a verified purchaser of `SKU-LAPTOP-01`) | `User 76364b1c-f737-4aa5-b64d-0140ea64334f cannot submit a review for product SKU-LAPTOP-01: must be a verified purchaser.` | **PASS** |
| `SEARCH_4001` | `400` | `GET /search/products?q=a` | `Search query text must be at least 2 characters long.` | **PASS** |

---

## 4. 6-Month Milestone Summary (`M1`..`M6`)

See [`test/MILESTONES.md`](./MILESTONES.md) for the full 24-commit chronological history from **2026-04-10** through **2026-10-10**:
- **M1 (Apr 2026):** Architecture docs, root Maven reactor, `commons-jar`, `iam-service`, `user-service`.
- **M2 (May 2026):** `product-service` catalog/inventory/Kafka saga, `search-service` indexing/querying.
- **M3 (Jun 2026):** `order-service` shopping cart, order state machine, Kafka event producers/listeners.
- **M4 (Jul 2026):** `payment-service`, `invoice-service` PDF/tax engine, `notification-service` templates/listeners.
- **M5 (Aug–Sep 2026):** `review-service` ratings/moderation, `api-gateway` routing & JWT RBAC enforcement.
- **M6 (Oct 2026):** Fake Razorpay emulator & HMAC webhooks, post-payment stock decrement/release, verified purchaser review enforcement (`REVIEW_4004`/`4006`), gateway password RBAC, **1,021 unit/integration tests**, `test/TEST_PLAN.md`, live multi-server E2E harness `test/run_e2e_live.sh` (**113 assertions**), `MILESTONES.md`, and `E2E_TEST_REPORT.md`.
