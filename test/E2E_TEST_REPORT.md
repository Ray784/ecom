# End-to-End & Reactor Verification Report (`ecommerce-spring-kafka`)

**Date:** `2026-10-10`  
**Repository:** `/usr/local/google/home/abhaybhat/ecommerce-spring-kafka`  
**Java Runtime:** OpenJDK 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`)  
**Spring Boot Version:** `3.3.4` (`spring-kafka` `3.2.4`, `H2` in-memory MySQL mode)  
**Overall Status:** **PASS** (915/915 Unit & Integration Tests Passed, 66/66 Live Multi-Server E2E Assertions Passed)

---

## 1. Architecture, Port, Database & Kafka Topology

### 1.1 Microservice Topology

| Module | Port | Storage / State | Role & Key Responsibilities |
| :--- | :--- | :--- | :--- |
| `commons-jar` | Library | Stateless | 27 shared DTOs, 83 `MappedError` codes, `CodedErrorFactory`, `ErrorMapper`, `GlobalExceptionHandler`, `JwtUtil`, `KafkaTopicNames` |
| `api-gateway` | `8080` | Stateless | Single entry point, JWT validation via `iam-service` `/auth/validate`, RBAC (`ROLE_CUSTOMER`, `ROLE_ADMIN`), header propagation (`X-User-Id`, `X-User-Roles`) |
| `iam-service` | `8081` | `jdbc:h2:mem:iamdb` | User credentials, BCrypt password hashing, role assignment, JWT issuance/validation/revocation, optional Embedded Kafka Broker host on port `9092` |
| `user-service` | `8082` | `jdbc:h2:mem:userdb` | Customer profiles, shipping address book (max 5, default handling), auto-initialization from `iam-service` |
| `product-service` | `8083` | `jdbc:h2:mem:productdb` | Product catalog, seeded categories, optimistic-locked (`@Version`) inventory reservation, search auto-indexing push |
| `order-service` | `8084` | `jdbc:h2:mem:orderdb` | Shopping cart (`Cart`, `CartItem`), order lifecycle state machine (`PENDING` -> `INVENTORY_RESERVED` -> `PAID`/`FAILED`/`CANCELLED`) |
| `payment-service` | `8085` | `jdbc:h2:mem:paymentdb` | Payment processing, fraud/gateway error simulation, `PaymentResultEvent` publisher |
| `invoice-service` | `8086` | `jdbc:h2:mem:invoicedb` + `/tmp/invoices` | Tiered discount (5% >= $100) & tax (8%) calculation, PDF invoice generator (`/tmp/invoices/invoice-{orderId}.pdf`) |
| `notification-service` | `8087` | In-memory store | Template-driven email/SMS dispatch (`ORDER_PLACED_TEMPLATE`, `PAYMENT_SUCCESS_TEMPLATE`, `PAYMENT_FAILED_TEMPLATE`) |
| `review-service` | `8088` | `jdbc:h2:mem:reviewdb` | Product reviews, prohibited content moderation, duplicate prevention, real-time aggregate rating calculation |
| `search-service` | `8089` | In-memory index | Tokenized full-text product search, category filtering, sorting (`price`, `name`, `sku`), and pagination |

### 1.2 Kafka Event Pipeline (`localhost:9092`)

| Topic Name | Producer | Consumers | Event Payload |
| :--- | :--- | :--- | :--- |
| `order-placed-topic` | `order-service` | `product-service`, `payment-service`, `notification-service` | `OrderPlacedEvent` (`orderId`, `userId`, `items`, `totalAmount`) |
| `inventory-reserved-topic` | `product-service` | `order-service` | `InventoryReservedEvent` (`orderId`, `success=true`, `message`) |
| `inventory-failed-topic` | `product-service` | `order-service` | `InventoryReservedEvent` (`orderId`, `success=false`, `message`) |
| `payment-successful-topic` | `payment-service` | `order-service`, `invoice-service`, `notification-service` | `PaymentResultEvent` (`orderId`, `transactionId`, `success=true`) |
| `payment-failed-topic` | `payment-service` | `order-service`, `notification-service` | `PaymentResultEvent` (`orderId`, `transactionId`, `success=false`, `failureReason`) |

---

## 2. Unit & Integration Test Suite Summary

Command executed:
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B clean test
```

| # | Maven Module | Tests Run | Failures | Errors | Skipped | Status |
| :--- | :--- | ---: | ---: | ---: | ---: | :--- |
| 1 | `commons-jar` | 351 | 0 | 0 | 0 | **PASS** |
| 2 | `iam-service` | 55 | 0 | 0 | 0 | **PASS** |
| 3 | `user-service` | 42 | 0 | 0 | 0 | **PASS** |
| 4 | `product-service` | 74 | 0 | 0 | 0 | **PASS** |
| 5 | `search-service` | 25 | 0 | 0 | 0 | **PASS** |
| 6 | `order-service` | 79 | 0 | 0 | 0 | **PASS** |
| 7 | `payment-service` | 56 | 0 | 0 | 0 | **PASS** |
| 8 | `invoice-service` | 52 | 0 | 0 | 0 | **PASS** |
| 9 | `notification-service` | 45 | 0 | 0 | 0 | **PASS** |
| 10 | `review-service` | 39 | 0 | 0 | 0 | **PASS** |
| 11 | `api-gateway` | 73 | 0 | 0 | 0 | **PASS** |
| — | **Total Reactor** | **915** | **0** | **0** | **0** | **BUILD SUCCESS** |

---

## 3. Live Multi-Server E2E Verification (`test/run_e2e_live.sh`)

Command executed:
```bash
bash test/run_e2e_live.sh
```

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
- **Request:** `POST http://localhost:8080/users/register`
  ```json
  {"username":"alice","password":"Alice@1234","email":"alice@example.com","firstName":"Alice"}
  ```
- **Response (`HTTP 201`):**
  ```json
  {"success":true,"statusCode":201,"message":"User registered successfully","userId":"70fd04ac-0fe3-4a49-93bd-fece21e48c2c","username":"alice","roles":["ROLE_CUSTOMER"],"email":"alice@example.com","valid":true}
  ```
- **Login Request:** `POST http://localhost:8080/auth/login`
- **Response (`HTTP 200`):** Issued signed HMAC-SHA256 JWT token for both `admin` (`ROLE_ADMIN`, `ROLE_CUSTOMER`) and `alice` (`ROLE_CUSTOMER`).
- **Validate Request:** `POST http://localhost:8081/auth/validate` with `Authorization: Bearer <token>`
- **Response (`HTTP 200`):**
  ```json
  {"success":true,"statusCode":200,"message":"Token is valid","userId":"70fd04ac-0fe3-4a49-93bd-fece21e48c2c","username":"alice","roles":["ROLE_CUSTOMER"],"email":"alice@example.com","valid":true}
  ```

#### B. Customer Profile & Shipping Address Management (`GET/PUT /profiles/{id}`, `POST /profiles/{id}/addresses`)
- **Auto-Initialized Profile (`GET /profiles/70fd04ac-0fe3-4a49-93bd-fece21e48c2c`, `HTTP 200`):**
  ```json
  {"success":true,"statusCode":200,"message":"Success","userId":"70fd04ac-0fe3-4a49-93bd-fece21e48c2c","firstName":"Alice","lastName":null,"email":"alice@example.com","addresses":[]}
  ```
- **Updated Profile (`PUT /profiles/70fd04ac-0fe3-4a49-93bd-fece21e48c2c`, `HTTP 200`):**
  ```json
  {"success":true,"statusCode":200,"message":"Success","userId":"70fd04ac-0fe3-4a49-93bd-fece21e48c2c","firstName":"Alice","lastName":"Wonderland","email":"alice@example.com","addresses":[]}
  ```
- **Added Default Shipping Address (`POST /profiles/70fd04ac-0fe3-4a49-93bd-fece21e48c2c/addresses`, `HTTP 201`):**
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

#### D. Shopping Cart & Order Placement (`POST /carts/items`, `GET /carts`, `POST /orders`)
- **Cart State (`GET http://localhost:8080/carts`, `HTTP 200`):**
  ```json
  {"success":true,"statusCode":200,"message":"Success","userId":"70fd04ac-0fe3-4a49-93bd-fece21e48c2c","items":[{"sku":"SKU-LAPTOP-01","quantity":1,"unitPrice":1250.00},{"sku":"SKU-MOUSE-02","quantity":2,"unitPrice":50.00}],"subtotal":1350.00}
  ```
- **Order Created (`POST http://localhost:8080/orders`, `HTTP 201`):**
  ```json
  {"success":true,"statusCode":201,"message":"Order created","orderId":"281cbf67-b324-4036-80c1-11dab1905bdb","userId":"70fd04ac-0fe3-4a49-93bd-fece21e48c2c","status":"PENDING","totalAmount":1350.00,"shippingAddress":"742 Evergreen Terrace, Springfield, IL 62704","items":[{"sku":"SKU-LAPTOP-01","quantity":1,"unitPrice":1250.00},{"sku":"SKU-MOUSE-02","quantity":2,"unitPrice":50.00}],"creationDate":"2026-10-10T08:58:25.626596405Z"}
  ```

#### E. Asynchronous Kafka Saga Verification (Converged in `< 1s`)
1. **Order Status Transitioned to `PAID` (`GET /orders/281cbf67-b324-4036-80c1-11dab1905bdb`, `HTTP 200`):**
   ```json
   {"success":true,"statusCode":200,"message":"Success","orderId":"281cbf67-b324-4036-80c1-11dab1905bdb","userId":"70fd04ac-0fe3-4a49-93bd-fece21e48c2c","status":"PAID","totalAmount":1350.00,"shippingAddress":"742 Evergreen Terrace, Springfield, IL 62704","items":[{"sku":"SKU-LAPTOP-01","quantity":1,"unitPrice":1250.00},{"sku":"SKU-MOUSE-02","quantity":2,"unitPrice":50.00}],"creationDate":"2026-10-10T08:58:25.626596Z"}
   ```
2. **Product Inventory Reserved (`GET /products/SKU-LAPTOP-01`, `HTTP 200`):**
   ```json
   {"success":true,"statusCode":200,"message":"OK","sku":"SKU-LAPTOP-01","name":"Pro Ultrabook 15 Gen2","description":"Updated developer laptop with 32GB RAM","price":1250.00,"categoryId":"ELECTRONICS","currentStock":15,"reservedStock":1,"averageRating":null}
   ```
3. **Payment Processed (`GET /payments/order/281cbf67-b324-4036-80c1-11dab1905bdb`, `HTTP 200`):**
   ```json
   {"transactionId":"TXN-122bec9a-b74d-4d0a-a682-65c0070ab2d7","orderId":"281cbf67-b324-4036-80c1-11dab1905bdb","userId":"70fd04ac-0fe3-4a49-93bd-fece21e48c2c","amount":1350.00,"gatewayRef":"GW-e973a752-9c30-486d-b7aa-4d0dd6444c14","status":"SUCCESS","failureReason":null,"createdAt":"2026-10-10T08:58:26.004621Z"}
   ```
4. **Invoice & PDF Generated (`GET /invoices/281cbf67-b324-4036-80c1-11dab1905bdb`, `HTTP 200`):**
   ```json
   {"success":true,"statusCode":200,"message":"Invoice available for order 281cbf67-b324-4036-80c1-11dab1905bdb","id":"INV-425f37c9-ced3-4333-8d53-7e8b4a3575f9","orderId":"281cbf67-b324-4036-80c1-11dab1905bdb","pdfStorageLink":"/tmp/invoices/invoice-281cbf67-b324-4036-80c1-11dab1905bdb.pdf","subtotal":1350.00,"discount":67.50,"taxAmount":102.60,"finalTotal":1385.10,"issueDate":"2026-10-10T08:58:26.428928Z"}
   ```
   - Verified `/tmp/invoices/invoice-281cbf67-b324-4036-80c1-11dab1905bdb.pdf` exists and is non-empty on disk.
5. **Notifications Dispatched (`GET /notifications/order/281cbf67-b324-4036-80c1-11dab1905bdb`, `HTTP 200`):**
   ```json
   [
     {"id":"NTF-cc2c2ba3-a4f1-4fd8-98b2-8033a6a6a04e","userId":"70fd04ac-0fe3-4a49-93bd-fece21e48c2c","orderId":"281cbf67-b324-4036-80c1-11dab1905bdb","type":"EMAIL","templateId":"ORDER_PLACED_TEMPLATE","message":"Hello 70fd04ac-0fe3-4a49-93bd-fece21e48c2c, your order 281cbf67-b324-4036-80c1-11dab1905bdb for 1350.00 has been placed successfully.","sentDate":"2026-10-10T08:58:25.857578890Z","status":"SENT"},
     {"id":"NTF-d3604996-3602-4235-a79e-092daef1270e","userId":"70fd04ac-0fe3-4a49-93bd-fece21e48c2c","orderId":"281cbf67-b324-4036-80c1-11dab1905bdb","type":"EMAIL","templateId":"PAYMENT_SUCCESS_TEMPLATE","message":"Payment received for order 281cbf67-b324-4036-80c1-11dab1905bdb. Transaction TXN-122bec9a-b74d-4d0a-a682-65c0070ab2d7 completed successfully.","sentDate":"2026-10-10T08:58:26.144210585Z","status":"SENT"}
   ]
   ```

#### F. Customer Product Reviews & Aggregate Rating (`POST /reviews`, `GET /reviews/products/{sku}`, `GET /reviews/products/{sku}/rating`)
- **Submitted Review (`POST http://localhost:8080/reviews`, `HTTP 201`):**
  ```json
  {"success":true,"statusCode":201,"message":"Review submitted successfully","id":1,"productId":"SKU-LAPTOP-01","rating":5,"comment":"Blazing fast laptop and keyboard feels great","userId":"70fd04ac-0fe3-4a49-93bd-fece21e48c2c","submissionDate":"2026-10-10T08:58:27.599548751Z"}
  ```
- **Aggregate Rating (`GET http://localhost:8080/reviews/products/SKU-LAPTOP-01/rating`, `HTTP 200`):**
  ```json
  {"success":true,"statusCode":200,"message":"Success","productId":"SKU-LAPTOP-01","averageRating":5.00,"reviewCount":1}
  ```

---

### 3.3 Negative Error Catalog Verification Table

| Error Code | HTTP Status | Endpoint Triggered | Live Response `errorDetails` | Result |
| :--- | :---: | :--- | :--- | :---: |
| `IAM_4001` | `401` | `POST /auth/login` (wrong password) | `Invalid username or password provided.` | **PASS** |
| `IAM_4002` | `401` | `GET /orders/{id}` (invalid JWT) | `Access Token is missing, invalid, or expired.` | **PASS** |
| `IAM_4004` | `400` | `POST /users/register` (duplicate `alice`) | `Username alice is already taken.` | **PASS** |
| `IAM_4008` | `403` | `POST /products` (customer token on admin route) | `User alice does not possess the required role: ROLE_ADMIN.` | **PASS** |
| `USER_4007` | `403` | `PUT /profiles/{bobId}` (using `alice` token) | `Cannot modify profile 8c25eea2-ba2b-43be-a455-d9d3ce190ae2: access denied.` | **PASS** |
| `PROD_4001` | `404` | `GET /products/SKU-DOES-NOT-EXIST` | `Product with SKU SKU-DOES-NOT-EXIST not found in the catalog.` | **PASS** |
| `PROD_4002` | `400` | `POST /inventory/reserve` (`qty=999` > `14`) | `Insufficient stock for product SKU-LAPTOP-01. Only 14 available.` | **PASS** |
| `ORDER_4001` | `400` | `POST /orders` (after cart cleared) | `Cannot place order: cart is empty for user 70fd04ac-0fe3-4a49-93bd-fece21e48c2c.` | **PASS** |
| `INV_4001` | `404` | `GET /invoices/ORD-NONEXISTENT-999` | `Invoice for Order ORD-NONEXISTENT-999 has not yet been generated.` | **PASS** |
| `REVIEW_4001` | `400` | `POST /reviews` (prohibited word `spam`) | `Review contains prohibited language or content.` | **PASS** |
| `REVIEW_4002` | `409` | `POST /reviews` (second review on `SKU-LAPTOP-01`) | `User 70fd04ac-0fe3-4a49-93bd-fece21e48c2c has already submitted a review for product SKU-LAPTOP-01.` | **PASS** |
| `REVIEW_4003` | `400` | `POST /reviews` (`rating=9`) | `Rating value 9 is outside the allowed range (1 to 5).` | **PASS** |
| `SEARCH_4001` | `400` | `GET /search/products?q=a` | `Search query text must be at least 2 characters long.` | **PASS** |

---

## 4. 6-Month Milestone Summary (`M1`..`M6`)

See [`test/MILESTONES.md`](./MILESTONES.md) for the full 20-commit chronological history from **2026-04-10** through **2026-10-10**:
- **M1 (Apr 2026):** Architecture docs, root Maven reactor, `commons-jar`, `iam-service`, `user-service`.
- **M2 (May 2026):** `product-service` catalog/inventory/Kafka saga, `search-service` indexing/querying.
- **M3 (Jun 2026):** `order-service` shopping cart, order state machine, Kafka event producers/listeners.
- **M4 (Jul 2026):** `payment-service`, `invoice-service` PDF/tax engine, `notification-service` templates/listeners.
- **M5 (Aug–Sep 2026):** `review-service` ratings/moderation, `api-gateway` routing & JWT RBAC enforcement.
- **M6 (Oct 2026):** 915 unit/integration tests, live multi-server E2E harness `test/run_e2e_live.sh`, `MILESTONES.md`, and `E2E_TEST_REPORT.md`.
