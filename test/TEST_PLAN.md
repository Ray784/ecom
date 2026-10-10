# Comprehensive Master Test Plan (`ecommerce-spring-kafka`)

**Repository:** `/usr/local/google/home/abhaybhat/ecommerce-spring-kafka`  
**Java Runtime:** OpenJDK 21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`)  
**Framework Stack:** Spring Boot `3.3.4`, Spring Kafka `3.2.4`, Spring Data JPA, H2 (MySQL compatibility mode), JUnit 5, MockMvc, EmbeddedKafka  
**Total Automated Coverage:** **1,021 Unit & Integration Tests** across 11 Maven modules + **Live Multi-Server E2E Test Harness** (`test/run_e2e_live.sh`)

---

## Part 1 — Module-by-Module Unit & Integration Test Matrix & 83-Code `MappedError` Coverage

### 1.1 Module & Test Class Breakdown (1,021 Reactor Tests)

| # | Module | Port | Test Class | Tests | Scope & Layer |
| :- | :--- | :--- | :--- | ---: | :--- |
| 1 | `commons-jar` | — | `MappedErrorTest` | 173 | Parameterized verification of all 83 `MappedError` enum constants, HTTP statuses, message templates, `fromCode`, and client/server classification |
| 1 | `commons-jar` | — | `CodedErrorFactoryTest` | 91 | `CodedErrorFactory.create`, `createExternal`, `createInternal`, defensive arg copies, cause chaining across all 83 codes |
| 1 | `commons-jar` | — | `DtoSerializationTest` | 40 | Jackson round-trip serialization, builders, `equals`/`hashCode`/`toString` for all 27 shared DTOs and Kafka event payloads |
| 1 | `commons-jar` | — | `JwtUtilTest` | 26 | HMAC-SHA256 JWT issuance, claims extraction, expiration detection (`IAM_4002`), tampering rejection, signing error (`IAM_5002`) |
| 1 | `commons-jar` | — | `ErrorMapperTest` | 9 | Locale-aware `ResourceBundle` lookup (`error-messages_fr.properties`), fallback to `MappedError` template, `MessageFormat` rendering |
| 1 | `commons-jar` | — | `GlobalExceptionHandlerTest` | 7 | `@RestControllerAdvice` mapping of `ApplicationBaseException`, Spring MVC errors, and unhandled exceptions to `ErrorResponseDTO` |
| 1 | `commons-jar` | — | `CommonsAutoConfigurationTest` | 3 | Spring Boot auto-configuration registration of `ErrorMapper` and `GlobalExceptionHandler` |
| 1 | `commons-jar` | — | `KafkaTopicNamesTest` | 2 | Verification of canonical 5 Kafka topic constants and `ALL_TOPICS` set |
| 2 | `iam-service` | `8081` | `UserServiceTest` | 22 | Registration validation (`IAM_4004..4006`), BCrypt password hashing, password update & token revocation (`IAM_4009`, `USER_4001`), `requireSelfOrAdmin` (`IAM_4008`), account locking |
| 2 | `iam-service` | `8081` | `AuthServiceTest` | 20 | Credential login (`IAM_4001`, `IAM_4003`, `IAM_5001`), JWT issuance (`IAM_5002`), token validation & blacklist enforcement (`IAM_4002`, `IAM_4007`) |
| 2 | `iam-service` | `8081` | `IamControllerIntegrationTest` | 17 | Full Spring Boot + H2 + MockMvc integration tests for `/auth/login`, `/auth/validate`, `/users/register`, `/users/{id}/password` (self, admin, cross-user `IAM_4008`), `/users/{id}/lock` |
| 2 | `iam-service` | `8081` | `EmbeddedKafkaBrokerConfigTest` | 3 | Conditional `EmbeddedKafkaKraftBroker` startup and creation of all 5 platform topics |
| 3 | `user-service` | `8082` | `UserManagerServiceTest` | 27 | Profile retrieval/auto-init, email uniqueness (`USER_4002`) & format (`USER_4004`), ownership check (`USER_4007`), address cap (`USER_4003`), default address rules (`USER_4006`), zip validation (`USER_4009`), DB error (`USER_5001`) |
| 3 | `user-service` | `8082` | `UserProfileControllerIntegrationTest` | 9 | MockMvc + H2 end-to-end profile & address lifecycle, RBAC header enforcement, IAM fallback (`USER_5002`) |
| 3 | `user-service` | `8082` | `IamClientTest` | 6 | Outbound REST client to `iam-service` (`GET /users/{id}`), 404 handling, 5xx/unreachable mapping to `USER_5002` |
| 4 | `product-service` | `8083` | `InventoryManagerServiceTest` | 20 | All-or-nothing stock reservation (`PROD_4001`, `PROD_4002`, `PROD_4006..4008`, `PROD_5001`), per-order reservation tracking, post-payment `decrementStockForOrder` and `releaseReservedStockForOrder` idempotency |
| 4 | `product-service` | `8083` | `ProductCatalogServiceTest` | 17 | Product CRUD (`PROD_4001`, `PROD_4003..4005`, `PROD_4007`, `PROD_4010`), initial stock setup, automatic search index push |
| 4 | `product-service` | `8083` | `ProductControllersTest` | 15 | MockMvc tests for `/products`, `/categories`, `/inventory/reserve`, `/events/order-placed`, and `/events/payment-result` |
| 4 | `product-service` | `8083` | `KafkaListenerServiceTest` | 14 | Unit tests for `order-placed-topic`, `payment-successful-topic`, and `payment-failed-topic` listeners, idempotency, and error handling |
| 4 | `product-service` | `8083` | `CategoryServiceTest` | 7 | Category seeding and CRUD validation (`PROD_4003`, `PROD_4009`) |
| 4 | `product-service` | `8083` | `KafkaProducerServiceTest` | 6 | Publishing `InventoryReservedEvent` to `inventory-reserved-topic` / `inventory-failed-topic`, webhook fallback, `PROD_5002` on failure |
| 4 | `product-service` | `8083` | `OutboundClientsTest` | 6 | `SearchIndexClient` and `OrderWebhookClient` HTTP client tests |
| 4 | `product-service` | `8083` | `CategorySeederTest` | 2 | Startup seeding of `ELECTRONICS`, `BOOKS`, `CLOTHING`, `HOME`, `SPORTS` |
| 4 | `product-service` | `8083` | `ProductServiceIntegrationTest` | 2 | Full Spring Boot + H2 optimistic locking (`@Version`) and persistence verification |
| 4 | `product-service` | `8083` | `KafkaPaymentStockFlowIntegrationTest` | 2 | EmbeddedKafka end-to-end verification of `OrderPlacedEvent` -> `PaymentResultEvent(success=true)` stock decrement (`15->14, reserved 1->0`) and `PaymentResultEvent(success=false)` stock release (`15->15, reserved 2->0`) |
| 4 | `product-service` | `8083` | `KafkaInventoryFlowIntegrationTest` | 1 | EmbeddedKafka verification of `order-placed-topic` -> `inventory-reserved-topic` and `inventory-failed-topic` (`PROD_4002`) |
| 5 | `search-service` | `8089` | `SearchQueryServiceTest` | 12 | Full-text tokenization, category filtering (`SEARCH_4003`), sorting (`SEARCH_4002`), pagination (`SEARCH_4004`), min query length (`SEARCH_4001`), index errors (`SEARCH_5001`, `SEARCH_5003`) |
| 5 | `search-service` | `8089` | `SearchControllerTest` | 7 | MockMvc tests for `GET /search/products` and `POST /search/index` |
| 5 | `search-service` | `8089` | `ProductDataIndexerTest` | 6 | Index upsert, summary/detail conversion, and `SEARCH_5002` validation |
| 6 | `order-service` | `8084` | `OrderManagerServiceTest` | 18 | Order creation from cart (`ORDER_4001`, `ORDER_4007`, `ORDER_4008`), state transitions (`ORDER_4003`, `ORDER_4010`), user order listing, `isVerifiedPurchase` (`PAID` check) |
| 6 | `order-service` | `8084` | `ControllersTest` | 18 | MockMvc tests for `/carts`, `/carts/items`, `/orders`, `/orders/{id}`, `/orders/{id}/cancel`, `/orders/verified-purchase`, `/events/**` |
| 6 | `order-service` | `8084` | `KafkaListenerServiceTest` | 16 | Saga state transitions on `inventory-reserved-topic`, `inventory-failed-topic`, `payment-successful-topic`, and `payment-failed-topic` |
| 6 | `order-service` | `8084` | `CartServiceTest` | 15 | Cart item addition (`ORDER_4004`, `ORDER_4006`, `ORDER_4009`), live catalog pricing, TTL expiration (`ORDER_4005`) |
| 6 | `order-service` | `8084` | `ProductCatalogClientTest` | 6 | Outbound REST client to `product-service` (`ORDER_4004` on 404/out-of-stock, `ORDER_5002` on 5xx/malformed payload) |
| 6 | `order-service` | `8084` | `KafkaProducerServiceTest` | 5 | Publishing `OrderPlacedEvent` to `order-placed-topic` and `ORDER_5001` on broker failure |
| 6 | `order-service` | `8084` | `OrderServiceIntegrationTest` | 5 | Full H2 + MockMvc saga lifecycle, verified purchase queries, and cancellation rules |
| 6 | `order-service` | `8084` | `OrderStatusTest` | 4 | State machine transition matrix unit tests |
| 7 | `payment-service` | `8085` | `RazorpayEmulatorServiceTest` | 27 | Fake Razorpay `/v1/orders` and `/v1/payments` state machine, Basic Auth (`PAY_4002`), paise validation (`PAY_4005`), duplicate payment (`PAY_4003`), unknown ID (`PAY_4004`), marker simulation (`decline`, `fraud`, `timeout`, `invalidcard`, `gatewaydown`, `unmapped`) |
| 7 | `payment-service` | `8085` | `PaymentProcessorServiceTest` | 24 | `initiatePayment` & `handleGatewayCallback` flows, `gatewayRef` (`pay_...`) & `razorpayOrderId` (`order_...`) persistence, `PAY_4001..5004` error handling |
| 7 | `payment-service` | `8085` | `RazorpayWebhookServiceTest` | 24 | `X-Razorpay-Signature` HMAC-SHA256 verification (`PAY_4002`), `payment.captured` / `payment.failed` / `order.paid` handling, lookup by `transactionId`/`paymentId`/`razorpayOrderId`/`orderId`, idempotent replay (`already_processed`), conflicting verdict (`PAY_4003`) |
| 7 | `payment-service` | `8085` | `GatewayTest` | 16 | `SimulatedPaymentGateway`, `RazorpayClient`, and `GatewayErrorMapper` unit tests |
| 7 | `payment-service` | `8085` | `PaymentControllerTest` | 8 | MockMvc tests for `GET /payments/order/{orderId}`, `POST /payments/process`, and `/events/order-placed` |
| 7 | `payment-service` | `8085` | `RazorpayEmulatorControllerTest` | 7 | MockMvc tests for `POST /v1/orders`, `GET /v1/orders/{id}`, `POST /v1/payments`, `GET /v1/payments/{id}` with Basic Auth |
| 7 | `payment-service` | `8085` | `KafkaProducerServiceTest` | 6 | Publishing `PaymentResultEvent` to `payment-successful-topic` and `payment-failed-topic` (`PAY_5003`) |
| 7 | `payment-service` | `8085` | `RazorpayWebhookControllerTest` | 5 | MockMvc tests for `POST /payments/razorpay/webhook` valid/invalid HMAC signatures and conflict responses |
| 7 | `payment-service` | `8085` | `PaymentKafkaIntegrationTest` | 3 | EmbeddedKafka end-to-end tests for approved payment, declined payment (`PAY_4001`), and Razorpay webhook publishing to Kafka |
| 7 | `payment-service` | `8085` | `KafkaListenerServiceTest` | 2 | Consumer listener delegation and catalog exception swallowing |
| 8 | `invoice-service` | `8086` | `InvoiceGeneratorServiceTest` | 34 | Tiered discount (5% >= $100) & tax (8%) calculation, PDF storage, idempotency, `INV_4001..5003` coverage |
| 8 | `invoice-service` | `8086` | `InvoiceControllerTest` | 8 | MockMvc tests for `GET /invoices/{orderId}`, `POST /invoices/generate/{orderId}`, and `/events/payment-successful` |
| 8 | `invoice-service` | `8086` | `InvoicePdfRendererTest` | 4 | PDF-1.4 document generation and byte validation |
| 8 | `invoice-service` | `8086` | `OrderServiceClientTest` | 3 | Outbound REST client to `order-service` (`GET /orders/{orderId}`) |
| 8 | `invoice-service` | `8086` | `KafkaListenerServiceTest` | 2 | Consumer listener for `payment-successful-topic` |
| 8 | `invoice-service` | `8086` | `InvoiceKafkaIntegrationTest` | 1 | EmbeddedKafka test verifying `PaymentResultEvent` triggers PDF invoice creation |
| 9 | `notification-service` | `8087` | `NotificationSenderServiceTest` | 31 | Template rendering (`ORDER_PLACED_TEMPLATE`, `PAYMENT_SUCCESS_TEMPLATE`, `PAYMENT_FAILED_TEMPLATE`), email/SMS dispatch, `NOTIF_4001..5003` |
| 9 | `notification-service` | `8087` | `NotificationControllerTest` | 9 | MockMvc tests for `/notifications/user/{userId}`, `/notifications/order/{orderId}`, `/notifications/send`, `/events/**` |
| 9 | `notification-service` | `8087` | `KafkaListenerServiceTest` | 4 | Consumer listener tests for order and payment topics |
| 9 | `notification-service` | `8087` | `NotificationKafkaIntegrationTest` | 1 | EmbeddedKafka end-to-end notification dispatch test |
| 10 | `review-service` | `8088` | `ReviewManagerServiceTest` | 33 | Content moderation (`REVIEW_4001`), duplicate check (`REVIEW_4002`), rating range (`REVIEW_4003`), product existence check (`REVIEW_4004`), comment length (`REVIEW_4005`), verified purchaser check (`REVIEW_4006`), missing review (`REVIEW_4007`), aggregate rating (`REVIEW_5001`) |
| 10 | `review-service` | `8088` | `ReviewControllerTest` | 14 | MockMvc tests for `POST /reviews` (including `REVIEW_4004` 404 and `REVIEW_4006` 403), `GET /reviews/products/{id}`, `GET /reviews/products/{id}/rating`, `GET /reviews/{id}` |
| 10 | `review-service` | `8088` | `ProductCatalogClientTest` | 5 | Outbound REST client to `product-service` (`GET /products/{sku}`) |
| 10 | `review-service` | `8088` | `OrderVerificationClientTest` | 4 | Outbound REST client to `order-service` (`GET /orders/verified-purchase`) |
| 11 | `api-gateway` | `8080` | `RouteResolverTest` | 41 | Route resolution and RBAC policy mapping for all public, `ROLE_CUSTOMER`, and `ROLE_ADMIN` routes (including `/v1/orders/**`, `/v1/payments/**`, `/payments/razorpay/webhook`, `PUT /users/{id}/password`, `GET /orders/verified-purchase`) |
| 11 | `api-gateway` | `8080` | `GatewayControllerTest` | 20 | End-to-end proxy forwarding, `X-User-Id`/`X-User-Roles` injection, spoofed header stripping, `Authorization` & `X-Razorpay-Signature` pass-through, `IAM_4002` & `IAM_4008` enforcement |
| 11 | `api-gateway` | `8080` | `AccessControlServiceTest` | 15 | Bearer token extraction, IAM validation with local `JwtUtil` fallback, role enforcement (`IAM_4002`, `IAM_4008`, `IAM_5001`) |
| 11 | `api-gateway` | `8080` | `IamAuthClientTest` | 11 | Outbound HTTP client to `iam-service` `POST /auth/validate` |
| — | **Total Reactor** | — | **48 Test Classes** | **1,021** | **0 Failures, 0 Errors, 0 Skipped** |

---

### 1.2 Complete 83-Code `MappedError` Catalog Coverage Table

Every one of the 83 error codes in `docs/ERROR_CATALOG.md` (`com.ecommerce.common.error.MappedError`) is verified both in `commons-jar` (`MappedErrorTest`, `CodedErrorFactoryTest`) and in its owning microservice's unit/integration test suite:

| Error Code | HTTP | Owning Module | Primary Service / Controller Verification Test Method(s) |
| :--- | :---: | :--- | :--- |
| `IAM_4001` | 401 | `iam-service` | `IamControllerIntegrationTest#login_wrongPassword_returnsIam4001`, `AuthServiceTest#login_wrongPassword_throwsIam4001` |
| `IAM_4002` | 401 | `iam-service`, `api-gateway` | `AccessControlServiceTest#authorize_missingOrMalformedHeader_throwsIam4002`, `IamControllerIntegrationTest#validate_expiredOrTampered_returnsIam4002` |
| `IAM_4003` | 403 | `iam-service` | `IamControllerIntegrationTest#lock_thenLoginReturnsIam4003_andOldTokenIsRevoked`, `AuthServiceTest#login_disabledAccount_throwsIam4003` |
| `IAM_4004` | 400 | `iam-service` | `IamControllerIntegrationTest#register_duplicateUsername_returnsIam4004`, `UserServiceTest#registerNewUser_takenUsername_throwsIam4004` |
| `IAM_4005` | 400 | `iam-service` | `IamControllerIntegrationTest#register_weakPasswordAndMissingField_returnCatalogErrors`, `UserServiceTest#registerNewUser_weakPassword_throwsIam4005` |
| `IAM_4006` | 400 | `iam-service` | `IamControllerIntegrationTest#register_weakPasswordAndMissingField_returnCatalogErrors`, `UserServiceTest#registerNewUser_missingFields_throwIam4006` |
| `IAM_4007` | 401 | `iam-service` | `IamControllerIntegrationTest#validate_basicScheme_returnsIam4007`, `AuthServiceTest#extractToken_otherScheme_throwsIam4007` |
| `IAM_4008` | 403 | `iam-service`, `api-gateway` | `IamControllerIntegrationTest#updatePassword_otherCustomerForbiddenWithIam4008`, `GatewayControllerTest#adminRoute_withCustomerToken_returns403Iam4008` |
| `IAM_4009` | 400 | `iam-service` | `IamControllerIntegrationTest#updatePassword_malformedUserId_returnsIam4009`, `UserServiceTest#updatePassword_malformedUserId_throwsIam4009` |
| `IAM_5001` | 503 | `iam-service`, `api-gateway` | `AuthServiceTest#login_identityStoreDown_throwsIam5001`, `AccessControlServiceTest#authorize_iamUnavailableWithoutFallback_throwsIam5001` |
| `IAM_5002` | 500 | `iam-service`, `commons-jar` | `AuthServiceTest#generateToken_revokedCollisionPersists_throwsIam5002`, `JwtUtilTest#serializationFailureIsReportedAsIam5002` |
| `USER_4001` | 404 | `user-service`, `iam-service` | `UserProfileControllerIntegrationTest#get_unknownProfile_returnsUser4001`, `UserManagerServiceTest#fetchUserProfile_unknownEverywhere_throwsUser4001` |
| `USER_4002` | 409 | `user-service` | `UserProfileControllerIntegrationTest#put_duplicateAndInvalidEmail_returnCatalogErrors`, `UserManagerServiceTest#updateProfile_emailOwnedByOtherUser_throwsUser4002` |
| `USER_4003` | 400 | `user-service` | `UserProfileControllerIntegrationTest#addAddress_beyondLimit_returnsUser4003`, `UserManagerServiceTest#addAddress_limitReached_throwsUser4003` |
| `USER_4004` | 400 | `user-service` | `UserProfileControllerIntegrationTest#put_duplicateAndInvalidEmail_returnCatalogErrors`, `UserManagerServiceTest#updateProfile_invalidEmail_throwsUser4004` |
| `USER_4005` | 400 | `user-service` | `UserProfileControllerIntegrationTest#addAddress_validationErrors`, `UserManagerServiceTest#addAddress_blankFields_throwUser4005` |
| `USER_4006` | 400 | `user-service` | `UserProfileControllerIntegrationTest#deleteAddress_defaultRulesAndNotFound`, `UserManagerServiceTest#deleteAddress_defaultWithOthers_throwsUser4006` |
| `USER_4007` | 403 | `user-service` | `UserProfileControllerIntegrationTest#otherCaller_isDenied_butAdminIsAllowed`, `UserManagerServiceTest#verifyAccess_otherNonAdminCaller_throwsUser4007` |
| `USER_4008` | 404 | `user-service` | `UserProfileControllerIntegrationTest#deleteAddress_defaultRulesAndNotFound`, `UserManagerServiceTest#deleteAddress_missingOrForeignOrNull_throwsUser4008` |
| `USER_4009` | 400 | `user-service` | `UserProfileControllerIntegrationTest#addAddress_validationErrors`, `UserManagerServiceTest#addAddress_badZip_throwsUser4009` |
| `USER_5001` | 500 | `user-service` | `UserManagerServiceTest#updateProfile_saveFails_throwsUser5001`, `UserManagerServiceTest#addAddress_saveFails_throwsUser5001` |
| `USER_5002` | 503 | `user-service` | `UserProfileControllerIntegrationTest#get_iamUnreachable_returnsUser5002`, `IamClientTest#fetchAccount_iamUnreachable_throwsUser5002` |
| `PROD_4001` | 404 | `product-service` | `ProductControllersTest#getProduct_unknown_returns404Prod4001`, `ProductCatalogServiceTest#getProductBySku_missing_throwsProd4001` |
| `PROD_4002` | 400 | `product-service` | `KafkaInventoryFlowIntegrationTest#orderPlaced_reservesStockAndPublishesReservedThenFailed`, `InventoryManagerServiceTest#reserveStock_insufficientStock_throwsProd4002` |
| `PROD_4003` | 400 | `product-service` | `ProductCatalogServiceTest#createProduct_unknownCategory_throwsProd4003`, `CategoryServiceTest#createCategory_missingId_throwsProd4003` |
| `PROD_4004` | 400 | `product-service` | `ProductControllersTest#postProducts_duplicate_returnsProd4004`, `ProductCatalogServiceTest#createProduct_duplicateSku_throwsProd4004` |
| `PROD_4005` | 400 | `product-service` | `ProductCatalogServiceTest#createProduct_nonPositivePrice_throwsProd4005` |
| `PROD_4006` | 400 | `product-service` | `InventoryManagerServiceTest#reserveStock_zeroOrNullQuantity_throwsProd4006`, `InventoryManagerServiceTest#reserveStock_noItems_throwsProd4006` |
| `PROD_4007` | 400 | `product-service` | `InventoryManagerServiceTest#reserveStock_negativeQuantity_throwsProd4007`, `ProductCatalogServiceTest#createProduct_negativeStock_throwsProd4007` |
| `PROD_4008` | 409 | `product-service` | `InventoryManagerServiceTest#reserveStock_optimisticLockConflict_throwsProd4008` |
| `PROD_4009` | 404 | `product-service` | `ProductControllersTest#getCategory_missing_returnsProd4009`, `CategoryServiceTest#getCategory_missingOrBlank_throwsProd4009` |
| `PROD_4010` | 400 | `product-service` | `ProductCatalogServiceTest#createProduct_invalidNameOrDescription_throwsProd4010` |
| `PROD_5001` | 503 | `product-service` | `InventoryManagerServiceTest#reserveStock_databaseDown_throwsProd5001` |
| `PROD_5002` | 500 | `product-service` | `KafkaProducerServiceTest#sendInventoryResult_kafkaThrowsAndNoWebhook_throwsProd5002`, `KafkaProducerServiceTest#sendInventoryResult_kafkaTimeout_throwsProd5002` |
| `ORDER_4001` | 400 | `order-service` | `OrderServiceIntegrationTest#orderWithEmptyCartAndUnknownOrderAreRejected`, `OrderManagerServiceTest#createOrderRejectsMissingOrEmptyCart` |
| `ORDER_4002` | 404 | `order-service` | `OrderServiceIntegrationTest#orderWithEmptyCartAndUnknownOrderAreRejected`, `ControllersTest#getOrderReturnsDetailsOr404` |
| `ORDER_4003` | 400 | `order-service` | `OrderServiceIntegrationTest#fullSagaMovesOrderToPaidAndClearsCart`, `ControllersTest#cancelOrderReturnsCancelledOr400` |
| `ORDER_4004` | 400 | `order-service` | `ProductCatalogClientTest#notFoundMapsToOrder4004`, `CartServiceTest#addToCartRejectsQuantityAboveReportedStock` |
| `ORDER_4005` | 404 | `order-service` | `OrderServiceIntegrationTest#fullSagaMovesOrderToPaidAndClearsCart`, `CartServiceTest#getCartThrowsWhenMissing` |
| `ORDER_4006` | 400 | `order-service` | `ControllersTest#addItemValidationErrorIsMappedToCatalogStatus`, `CartServiceTest#addToCartRejectsNonPositiveQuantity` |
| `ORDER_4007` | 400 | `order-service` | `ControllersTest#createOrderEmptyAddressIsMappedTo400`, `OrderManagerServiceTest#createOrderRejectsBlankAddress` |
| `ORDER_4008` | 400 | `order-service` | `OrderManagerServiceTest#createOrderRejectsTotalMismatch` |
| `ORDER_4009` | 400 | `order-service` | `CartServiceTest#addToCartRejectsExceedingMaxItems` |
| `ORDER_4010` | 400 | `order-service` | `OrderServiceIntegrationTest#paymentFailureThenCancelReturnsOrder4010`, `OrderManagerServiceTest#invalidTransitionAfterPaymentFailureThrowsOrder4010` |
| `ORDER_5001` | 500 | `order-service` | `KafkaProducerServiceTest#assertPublishError`, `OrderManagerServiceTest#createOrderMarksOrderFailedWhenKafkaPublishFails` |
| `ORDER_5002` | 503 | `order-service` | `ProductCatalogClientTest#serverErrorMapsToOrder5002`, `ProductCatalogClientTest#missingPriceMapsToOrder5002` |
| `PAY_4001` | 400 | `payment-service` | `PaymentKafkaIntegrationTest#orderPlacedRecord_declinedUser_publishesFailureWithPay4001`, `PaymentProcessorServiceTest#initiatePayment_declinedCard_marksFailedPublishesAndThrowsPay4001` |
| `PAY_4002` | 400 | `payment-service` | `RazorpayEmulatorControllerTest#createOrder_missingAuthorization_returns400WithPay4002`, `RazorpayWebhookControllerTest#webhook_invalidSignature_returns400WithPay4002` |
| `PAY_4003` | 409 | `payment-service` | `PaymentProcessorServiceTest#initiatePayment_orderAlreadyPaid_throwsPay4003`, `RazorpayWebhookControllerTest#webhook_conflictingVerdict_returns409WithPay4003` |
| `PAY_4004` | 404 | `payment-service` | `PaymentControllerTest#getByOrderId_missing_returns404WithPay4004`, `RazorpayEmulatorControllerTest#getPayment_unknownId_returns404WithPay4004` |
| `PAY_4005` | 400 | `payment-service` | `PaymentProcessorServiceTest#initiatePayment_itemsNotMatchingTotal_throwsPay4005`, `RazorpayEmulatorServiceTest#createOrder_invalidAmount_throwsPay4005` |
| `PAY_4006` | 400 | `payment-service` | `PaymentControllerTest#orderPlacedWebhook_fraud_returns400WithPay4006`, `PaymentProcessorServiceTest#initiatePayment_fraudUser_blocksBeforeGatewayAndThrowsPay4006` |
| `PAY_5001` | 503 | `payment-service` | `PaymentControllerTest#process_gatewayDown_returns503WithPay5001`, `RazorpayEmulatorControllerTest#createPayment_gatewayDownPayer_returns503WithPay5001` |
| `PAY_5002` | 500 | `payment-service` | `GatewayTest#charge_timeoutMarker_throwsCommunicationException`, `PaymentProcessorServiceTest#initiatePayment_gatewayTimeout_marksFailedAndThrowsPay5002` |
| `PAY_5003` | 500 | `payment-service` | `KafkaProducerServiceTest#sendPaymentResult_brokerRejects_throwsPay5003`, `KafkaProducerServiceTest#sendPaymentResult_noAcknowledgementInTime_throwsPay5003` |
| `PAY_5004` | 500 | `payment-service` | `GatewayTest#toMappedError_nullCode_isPay5004`, `PaymentProcessorServiceTest#initiatePayment_unmappedGatewayError_throwsPay5004` |
| `INV_4001` | 404 | `invoice-service` | `InvoiceControllerTest#getInvoice_notGenerated_returns404WithInv4001`, `InvoiceGeneratorServiceTest#getInvoice_missing_throwsInv4001` |
| `INV_4002` | 404 | `invoice-service` | `InvoiceGeneratorServiceTest#generateInvoiceForOrderId_unknownOrder_throwsInv4002`, `InvoiceGeneratorServiceTest#generateInvoice_unsafeOrderId_throwsInv4002` |
| `INV_4003` | 400 | `invoice-service` | `InvoiceControllerTest#generate_unpaidOrder_returns400WithInv4003`, `InvoiceGeneratorServiceTest#generateInvoiceForOrder_unpaidOrder_throwsInv4003` |
| `INV_5001` | 500 | `invoice-service` | `InvoiceGeneratorServiceTest#generateInvoice_rendererFails_throwsInv5001`, `InvoiceGeneratorServiceTest#storeInvoiceDocument_emptyContent_throwsInv5001` |
| `INV_5002` | 503 | `invoice-service` | `InvoiceControllerTest#generate_storageDown_returns503WithInv5002`, `InvoiceGeneratorServiceTest#storeInvoiceDocument_unwritableStorage_throwsInv5002` |
| `INV_5003` | 500 | `invoice-service` | `InvoiceGeneratorServiceTest#generateInvoice_negativeSubtotal_throwsInv5003` |
| `NOTIF_4001` | 400 | `notification-service` | `NotificationControllerTest#send_unsupportedType_returns400WithNotif4001`, `NotificationSenderServiceTest#sendEmail_missingRecipient_throwsNotif4001` |
| `NOTIF_4002` | 404 | `notification-service` | `NotificationControllerTest#send_unknownTemplate_returns404WithNotif4002`, `NotificationSenderServiceTest#sendEmail_unknownTemplate_throwsNotif4002` |
| `NOTIF_4003` | 400 | `notification-service` | `NotificationControllerTest#paymentResultWebhook_missingOrder_returns400WithNotif4003`, `NotificationSenderServiceTest#sendEmail_missingTemplateParameter_throwsNotif4003WithoutLogging` |
| `NOTIF_5001` | 503 | `notification-service` | `NotificationSenderServiceTest#sendEmail_providerDown_logsFailedEntryAndThrowsNotif5001` |
| `NOTIF_5002` | 503 | `notification-service` | `NotificationControllerTest#send_smsGatewayTimeout_returns503WithNotif5002`, `NotificationSenderServiceTest#sendSms_gatewayTimeout_logsFailedEntryAndThrowsNotif5002` |
| `NOTIF_5003` | 500 | `notification-service` | `NotificationSenderServiceTest#sendEmail_storeFailure_throwsNotif5003` |
| `REVIEW_4001` | 400 | `review-service` | `ReviewControllerTest#postReview_prohibitedLanguage_returns400Review4001`, `ReviewManagerServiceTest#submitReview_prohibitedWord_throwsReview4001` |
| `REVIEW_4002` | 409 | `review-service` | `ReviewControllerTest#postReview_duplicate_returns409Review4002`, `ReviewManagerServiceTest#submitReview_existingReviewForUserAndProduct_throwsReview4002` |
| `REVIEW_4003` | 400 | `review-service` | `ReviewControllerTest#postReview_invalidRating_returns400Review4003`, `ReviewManagerServiceTest#submitReview_ratingOutOfRange_throwsReview4003` |
| `REVIEW_4004` | 404 | `review-service` | `ReviewControllerTest#postReview_productUnknownToProductService_returns404Review4004`, `ReviewManagerServiceTest#submitReview_productUnknownToProductService_throwsReview4004` |
| `REVIEW_4005` | 400 | `review-service` | `ReviewControllerTest#postReview_commentTooLong_returns400Review4005`, `ReviewManagerServiceTest#submitReview_commentTooLong_throwsReview4005` |
| `REVIEW_4006` | 403 | `review-service` | `ReviewControllerTest#postReview_unverifiedPurchaser_returns403Review4006`, `ReviewManagerServiceTest#submitReview_unverifiedPurchaser_throwsReview4006` |
| `REVIEW_4007` | 404 | `review-service` | `ReviewControllerTest#getReviewById_existingAndMissing`, `ReviewManagerServiceTest#getReviewById_missing_throwsReview4007` |
| `REVIEW_5001` | 500 | `review-service` | `ReviewManagerServiceTest#calculateAggregateRating_repositoryFailure_throwsInternalReview5001` |
| `SEARCH_4001` | 400 | `search-service` | `SearchControllerTest#searchProducts_shortQuery_returnsSearch4001`, `SearchQueryServiceTest#executeSearch_shortQuery_throwsSearch4001` |
| `SEARCH_4002` | 400 | `search-service` | `SearchControllerTest#searchProducts_badSortField_returnsSearch4002`, `SearchQueryServiceTest#executeSearch_invalidSortFieldOrOrder_throwsSearch4002` |
| `SEARCH_4003` | 400 | `search-service` | `SearchControllerTest#searchProducts_badCategory_returnsSearch4003`, `SearchQueryServiceTest#executeSearch_invalidCategoryFilter_throwsSearch4003` |
| `SEARCH_4004` | 400 | `search-service` | `SearchControllerTest#searchProducts_badPage_returnsSearch4004`, `SearchQueryServiceTest#executeSearch_invalidPagination_throwsSearch4004` |
| `SEARCH_5001` | 503 | `search-service` | `SearchQueryServiceTest#executeSearch_indexUnavailable_throwsSearch5001` |
| `SEARCH_5002` | 500 | `search-service` | `SearchControllerTest#indexProduct_missingSku_returnsSearch5002`, `ProductDataIndexerTest#updateIndex_missingSku_throwsSearch5002` |
| `SEARCH_5003` | 500 | `search-service` | `SearchQueryServiceTest#executeSearch_unexpectedFailure_throwsSearch5003` |

---

## Part 2 — Fake Razorpay Emulator, Client & Webhook Test Matrix

`payment-service` provides a self-contained Fake Razorpay Orders & Payments REST Emulator (`/v1/orders`, `/v1/payments`) protected by HTTP Basic Auth (`rzp_test_key_id:rzp_test_key_secret`) and an HMAC-SHA256 signed Webhook endpoint (`POST /payments/razorpay/webhook` verifying `X-Razorpay-Signature` with `rzp_test_webhook_secret`), exposed both directly on `localhost:8085` and through `api-gateway` on `localhost:8080`.

| Test ID | Scenario | Endpoint & Method | Auth / Header | Expected Outcome | Automated Test Coverage |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **RZP-01** | Create Razorpay order with valid Basic Auth | `POST /v1/orders` | `Authorization: Basic base64(rzp_test_key_id:rzp_test_key_secret)` | `200 OK`, `id="order_<14-alnum>"`, `entity="order"`, `amount` in paise, `status="created"` | `RazorpayEmulatorServiceTest#createOrder_validRequest_returnsOrderWithCreatedStatus`, `RazorpayEmulatorControllerTest#createAndGetOrder_withBasicAuth_roundTrips`, `run_e2e_live.sh` |
| **RZP-02** | Fetch Razorpay order by ID | `GET /v1/orders/{orderId}` | Valid Basic Auth | `200 OK` with stored order JSON; `404 PAY_4004` if unknown | `RazorpayEmulatorServiceTest#getOrder_unknownId_throwsPay4004`, `RazorpayEmulatorControllerTest#createAndGetOrder_withBasicAuth_roundTrips`, `run_e2e_live.sh` |
| **RZP-03** | Reject `/v1/orders` or `/v1/payments` with missing/wrong Basic Auth | `POST /v1/orders`, `POST /v1/payments` | Missing header, Bearer token, or wrong secret | `400 Bad Request` (`errorCode="PAY_4002"`) | `RazorpayEmulatorControllerTest#createOrder_missingAuthorization_returns400WithPay4002`, `RazorpayEmulatorControllerTest#createPayment_wrongSecret_returns400WithPay4002`, `run_e2e_live.sh` |
| **RZP-04** | Capture payment for Razorpay order | `POST /v1/payments` | Valid Basic Auth | `200 OK`, `id="pay_<14-alnum>"`, `status="captured"`, `captured=true`, order transitions to `status="paid"`, `amount_due=0` | `RazorpayEmulatorServiceTest#createPayment_happyPath_capturesAndMarksOrderPaid`, `RazorpayEmulatorControllerTest#createAndGetPayment_capturedFlow_succeeds`, `run_e2e_live.sh` |
| **RZP-05** | Declined payment simulation via `decline` marker | `POST /v1/payments` | Valid Basic Auth (`payer_user_id` contains `decline`) | `200 OK`, `id="pay_<14-alnum>"`, `status="failed"`, `captured=false`, `error_code="BAD_REQUEST_ERROR"`, order `status="attempted"` | `RazorpayEmulatorServiceTest#createPayment_declineMarker_returnsFailedPaymentAndAttemptedOrder`, `PaymentKafkaIntegrationTest#orderPlacedRecord_declinedUser_publishesFailureWithPay4001`, `run_e2e_live.sh` |
| **RZP-06** | Saga payment stores `gatewayRef=pay_...` and `razorpayOrderId=order_...` | `GET /payments/order/{orderId}` | Bearer JWT via Gateway | `200 OK`, `gatewayRef` matches `^pay_[A-Za-z0-9]{14}$`, `razorpayOrderId` matches `^order_[A-Za-z0-9]{14}$` | `PaymentProcessorServiceTest#initiatePayment_happyPath_persistsSuccessAndPublishesEvent`, `PaymentKafkaIntegrationTest#orderPlacedRecord_isConsumedChargedAndPublishedToSuccessTopic`, `run_e2e_live.sh` |
| **RZP-07** | Webhook `payment.captured` with valid `X-Razorpay-Signature` | `POST /payments/razorpay/webhook` | `X-Razorpay-Signature: hex(HMAC_SHA256(body, rzp_test_webhook_secret))` | `200 OK`, `paymentStatus="SUCCESS"`, publishes `PaymentResultEvent(success=true)` to `payment-successful-topic` | `RazorpayWebhookServiceTest#processWebhook_nestedCapturedForInitiatedTransaction_marksSuccessAndPublishes`, `RazorpayWebhookControllerTest#webhook_validCapturedSignature_returns200`, `run_e2e_live.sh` |
| **RZP-08** | Webhook `payment.failed` with valid `X-Razorpay-Signature` | `POST /payments/razorpay/webhook` | Valid `X-Razorpay-Signature` | `200 OK`, `paymentStatus="FAILED"`, publishes `PaymentResultEvent(success=false)` to `payment-failed-topic` | `RazorpayWebhookServiceTest#processWebhook_nestedFailedEvent_marksFailedWithEntityDescriptionAndPublishes`, `PaymentKafkaIntegrationTest#razorpayWebhook_failedEvent_publishesToPaymentFailedTopic`, `run_e2e_live.sh` |
| **RZP-09** | Webhook idempotent replay on already settled transaction | `POST /payments/razorpay/webhook` | Valid `X-Razorpay-Signature` | `200 OK`, `status="already_processed"` without duplicate Kafka publish; `409 PAY_4003` if verdict conflicts | `RazorpayWebhookServiceTest#processWebhook_repeatSameOutcome_isIdempotentAndDoesNotRepublish`, `RazorpayWebhookControllerTest#webhook_conflictingVerdict_returns409WithPay4003`, `run_e2e_live.sh` |
| **RZP-10** | Webhook rejected on missing or invalid HMAC signature | `POST /payments/razorpay/webhook` | `X-Razorpay-Signature: deadbeef` or omitted | `400 Bad Request` (`errorCode="PAY_4002"`) | `RazorpayWebhookServiceTest#processWebhook_wrongSignature_throwsPay4002`, `RazorpayWebhookControllerTest#webhook_invalidSignature_returns400WithPay4002`, `run_e2e_live.sh` |

---

## Part 3 — Asynchronous Kafka Saga & Post-Payment Stock Settlement Matrix

Per LLD Section 2.3.2 (`InventoryManagerService.decrementStock` / `releaseReservedStock`), stock is first **reserved** when `OrderPlacedEvent` arrives on `order-placed-topic`, and then **settled** when `PaymentResultEvent` arrives on `payment-successful-topic` or `payment-failed-topic`:

| Saga Stage | Event & Topic | Producer -> Consumers | Inventory State (`SKU-LAPTOP-01`) | Order & Downstream State | Verification Tests |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1. Initial Catalog State** | — | Admin `POST/PUT /products` | `currentStock = 15`, `reservedStock = 0` | Indexed in `search-service` | `ProductCatalogServiceTest`, `run_e2e_live.sh` |
| **2. Order Placed (Stock Reserved)** | `OrderPlacedEvent` on `order-placed-topic` | `order-service` -> `product-service`, `payment-service`, `notification-service` | `currentStock = 15`, `reservedStock = 1` (`OrderReservation` state = `RESERVED`) | Order `PENDING` -> `INVENTORY_RESERVED`; `ORDER_PLACED_TEMPLATE` email sent | `KafkaInventoryFlowIntegrationTest`, `InventoryManagerServiceTest`, `run_e2e_live.sh` |
| **3A. Payment Captured (Stock Decremented)** | `PaymentResultEvent(success=true)` on `payment-successful-topic` | `payment-service` -> `product-service`, `order-service`, `invoice-service`, `notification-service` | `decrementStockForOrder`: `currentStock = 14`, `reservedStock = 0` (`OrderReservation` state = `DECREMENTED`, idempotent on replay) | Order `status = PAID`; `PaymentTransaction` `SUCCESS` (`pay_...`, `order_...`); PDF `/tmp/invoices/invoice-{orderId}.pdf` generated; `PAYMENT_SUCCESS_TEMPLATE` email sent | `KafkaPaymentStockFlowIntegrationTest#orderPlacedThenPaymentSucceeded_decrementsCurrentStockAndClearsReserved`, `run_e2e_live.sh` |
| **3B. Payment Failed / Declined (Reserved Stock Released)** | `PaymentResultEvent(success=false)` on `payment-failed-topic` | `payment-service` -> `product-service`, `order-service`, `notification-service` |During reservation (`qty=2`): `currentStock = 14`, `reservedStock = 2`. After `releaseReservedStockForOrder`: `currentStock = 14` (unchanged), `reservedStock = 0` (`OrderReservation` state = `RELEASED`, idempotent on replay) | Order `status = FAILED`; `PaymentTransaction` `FAILED` (`PAY_4001`); `PAYMENT_FAILED_TEMPLATE` email sent; no invoice generated | `KafkaPaymentStockFlowIntegrationTest#orderPlacedThenPaymentFailed_releasesReservedAndLeavesCurrentStockIntact`, `run_e2e_live.sh` |

---

## Part 4 — Verified Purchaser Review Enforcement & Gateway Password Security Matrix

### 4.1 Verified Purchaser & Product Existence Review Matrix (`REVIEW_4004` & `REVIEW_4006`)

`review-service` validates both product existence (via `ProductCatalogClient` -> `product-service` `GET /products/{sku}`) and verified purchase status (via `OrderVerificationClient` -> `order-service` `GET /orders/verified-purchase?userId={userId}&sku={sku}`) before persisting any review:

| Test ID | Caller & Product | `product-service` Check | `order-service` Verified Purchase Check | Expected HTTP & Error Code | Automated Test Coverage |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **REV-VP-01** | Customer `alice` reviews non-existent SKU `SKU-GHOST-999` | `GET /products/SKU-GHOST-999` returns `404 Not Found` | Short-circuited (not called) | `404 Not Found` (`errorCode="REVIEW_4004"`) | `ReviewManagerServiceTest#submitReview_productUnknownToProductService_throwsReview4004`, `ReviewControllerTest#postReview_productUnknownToProductService_returns404Review4004`, `run_e2e_live.sh` |
| **REV-VP-02** | Customer `bob` (no `PAID` order for `SKU-LAPTOP-01`) reviews `SKU-LAPTOP-01` | `GET /products/SKU-LAPTOP-01` returns `200 OK` | `GET /orders/verified-purchase?userId={bobId}&sku=SKU-LAPTOP-01` returns `{"verified":false}` | `403 Forbidden` (`errorCode="REVIEW_4006"`) | `ReviewManagerServiceTest#submitReview_unverifiedPurchaser_throwsReview4006`, `ReviewControllerTest#postReview_unverifiedPurchaser_returns403Review4006`, `OrderManagerServiceTest`, `run_e2e_live.sh` |
| **REV-VP-03** | Customer `alice` (has `PAID` order containing `SKU-LAPTOP-01`) reviews `SKU-LAPTOP-01` | `GET /products/SKU-LAPTOP-01` returns `200 OK` | `GET /orders/verified-purchase?userId={aliceId}&sku=SKU-LAPTOP-01` returns `{"verified":true}` | `201 Created` (`ReviewDTO` saved and aggregate rating updated to `5.00`) | `ReviewManagerServiceTest#publicConstructor_checksEnabledByFlags_callsClients`, `OrderServiceIntegrationTest`, `run_e2e_live.sh` |
| **REV-VP-04** | Customer `alice` submits a second review for `SKU-LAPTOP-01` | `200 OK` | `{"verified":true}` | `409 Conflict` (`errorCode="REVIEW_4002"`) | `ReviewControllerTest#postReview_duplicate_returns409Review4002`, `run_e2e_live.sh` |

### 4.2 Gateway Password Update & Ownership Enforcement Matrix (`PUT /users/{id}/password`)

`api-gateway` routes `PUT /users/{id}/password` (`ROLE_CUSTOMER`) to `iam-service` with verified `X-User-Id` and `X-User-Roles` headers (after stripping any client-supplied `X-User-*` headers). `iam-service` enforces `UserService.requireSelfOrAdmin`:

| Test ID | Caller Identity | Target Endpoint | Ownership / Role Check | Expected Outcome | Automated Test Coverage |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **PWD-01** | Unauthenticated request | `PUT /users/{aliceId}/password` | Missing Bearer token at `api-gateway` | `401 Unauthorized` (`errorCode="IAM_4002"`) | `GatewayControllerTest#passwordUpdate_withoutToken_returns401Iam4002` |
| **PWD-02** | Customer `alice` (`X-User-Id = aliceId`, `ROLE_CUSTOMER`) | `PUT /users/{bobId}/password` | `callerId != targetUserId` and lacks `ROLE_ADMIN` | `403 Forbidden` (`errorCode="IAM_4008"`); `bob`'s old password remains active | `UserServiceTest#requireSelfOrAdmin_otherUserWithoutAdmin_throwsIam4008`, `IamControllerIntegrationTest#updatePassword_otherCustomerForbiddenWithIam4008`, `run_e2e_live.sh` |
| **PWD-03** | Customer `alice` (`X-User-Id = aliceId`, `ROLE_CUSTOMER`) | `PUT /users/{aliceId}/password` | `callerId == targetUserId` (self) | `200 OK` (`Password updated`), old tokens blacklisted, `POST /auth/login` with new password returns `200 OK` and fresh JWT | `IamControllerIntegrationTest#updatePassword_ownerAllowed_andCanLoginWithNewPassword`, `GatewayControllerTest#passwordUpdate_authenticatedCustomer_forwardsVerifiedUserId`, `run_e2e_live.sh` |
| **PWD-04** | Admin `admin` (`ROLE_ADMIN`) | `PUT /users/{bobId}/password` | `callerRoles` contains `ROLE_ADMIN` | `200 OK` (`Password updated`) | `UserServiceTest#requireSelfOrAdmin_adminUpdatingOtherUser_succeeds`, `IamControllerIntegrationTest#updatePassword_adminAllowedForOtherUser` |
