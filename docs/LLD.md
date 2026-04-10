Command exited with code 0.

Command output:
Tab ID          Title              Index  Nesting
--------------  -----------------  -----  -------
t.0             High Level Design  0      0      
t.mb353xh36moe  Low Level Design   1      0      
t.kr696awic34n  Error Catalog      2      0      
Low-Level Design (LLD) for E-commerce Microservices

This document provides a low-level design (LLD) derived exclusively from the High-Level Design (HLD) for the E-commerce Microservices Application. It details the required components within the shared `commons-jar` and specifies the internal structure (Models, Services, Exceptions, and APIs) for each core microservice, excluding the defined future enhancements section.


---

# 1. Commons Library (`commons-jar`) Design

The `commons-jar` is a shared dependency for all microservices, ensuring architectural consistency in handling data contracts, exceptions, and error reporting.

### 1.1 Base Data Transfer Objects (DTOs) and Models

These structures form the foundation for synchronous request/response handling and Kafka messages.


| Class Name | Package | Description | Key Attributes |
| --- | --- | --- | --- |
| **BaseResponseDTO** | com.ecommerce.common.dto | Standard container for all API responses. | `boolean success`, `int statusCode`, `String message` |
| **ErrorResponseDTO** | com.ecommerce.common.dto | Standard structure for detailed error reporting. | `int httpStatusCode`, `String errorCode`, `String errorDetails`, `String timestamp` |
| **PaginationDTO** | com.ecommerce.common.dto | Metadata for paginated results. | `int page`, `int size`, `long totalElements`, `int totalPages` |

### 1.2 Base Exception Hierarchy

All service-level exceptions must extend a base application exception, which in turn extends `RuntimeException`.


| Class Name | Package | Extends | Description |
| --- | --- | --- | --- |
| **ApplicationBaseException** | com.ecommerce.common.exception | `RuntimeException` | Abstract base class for all checked application exceptions, handling core error mapping logic. |
| **ApiExternalException** | com.ecommerce.common.exception | `ApplicationBaseException` | Exceptions to be thrown by the services will contain the error code. |
| **ApiInternalException** | com.ecommerce.common.exception | `ApplicationBaseException` | Internal exceptions |

### 1.3 Coded Error and Mapping Components

These components standardize how internal errors are translated into external HTTP responses.


| Class Name | Package | Description | Key Methods |
| --- | --- | --- | --- |
| **MappedError** | com.ecommerce.common.error | Enum/Class defining internal, coded error configurations. | `String code`, `String messageTemplate`, `int httpStatus` |
| **CodedErrorFactory** | com.ecommerce.common.error | Creates service exceptions from a `MappedError`. | `create(MappedError error, Object... args)`: returns `ApplicationBaseException` |
| **ErrorMapper** | com.ecommerce.common.error | Abstract component to map `MappedError` codes to localized messages (simulating reading from a YAML or properties file). | `getErrorMessage(String code, Locale locale)` |

### 1.4 Extended Common DTOs (Kafka Schemas)

These define the data contracts for asynchronous communication using Kafka.


| Class Name | Purpose/Topic | Key Attributes (Example) |
| --- | --- | --- |
| **OrderPlacedEvent** | `order-placed-topic` | `String orderId`, `String userId`, `List<ItemDetails>`, `BigDecimal totalAmount` |
| **InventoryReservationRequest** | Internal/Sync | `String orderId`, `List<ItemDetails>` |
| **InventoryReservedEvent** | `inventory-reserved-topic` | `String orderId`, `boolean success`, `String message` |
| **PaymentResultEvent** | `payment-successful-topic` or `payment-failed-topic` | `String orderId`, `String transactionId`, `boolean success`, `String failureReason` |

### 1.5 Service-Level External Data Transfer Objects (DTOs)

These DTOs are used for synchronous API requests and responses, forming the explicit data contract for each microservice's external endpoints. Response DTOs will extend the base `BaseResponseDTO` (excluding the generic `Object data` attribute).

#### IAM Service DTOs

| Class Name | Type | Description | Key Attributes (Example) |
| --- | --- | --- | --- |
| **LoginRequestDTO** | Request | User credentials for authentication. | String username, String password |
| **RegistrationDTO** | Request | Details required for new user registration. | String username, String password, String email, String firstName |
| **AuthResponseDTO** | Response | Contains the generated JWT token after successful login. | String jwtToken, Long expirationTimestamp |
| **UserDetailsDTO** | Response | User identity and permissions returned after token validation. | String userId, Set<String> roles, String email |

#### User Service DTOs

| Class Name | Type | Description | Key Attributes (Example) |
| --- | --- | --- | --- |
| **ProfileUpdateDTO** | Request | Data for updating non-sensitive user profile information. | String firstName, String lastName, String email |
| **AddressDTO** | Request/Response | Structure for adding/viewing a user's address. | String street, String city, String zipCode, boolean isDefault |
| **UserProfileDTO** | Response | Complete user profile and addresses. | String userId, String firstName, String lastName, List<AddressDTO> addresses |

#### Product Service DTOs

| Class Name | Type | Description | Key Attributes (Example) |
| --- | --- | --- | --- |
| **ProductCreationDTO** | Request | Data needed to create a new catalog item. | String name, String description, BigDecimal price, String categoryId, Integer initialStock |
| **ProductDetailsDTO** | Response | Comprehensive product information retrieved via GET requests. | String sku, String name, String description, BigDecimal price, Integer currentStock, BigDecimal averageRating |
| **ProductSummaryDTO** | Response | Lightweight details for use in search results or lists. | String sku, String name, BigDecimal price, String imageUrl |

#### Order Service DTOs

| Class Name | Type | Description | Key Attributes (Example) |
| --- | --- | --- | --- |
| **CartItemDTO** | Request | Details for adding an item to the shopping cart. | String sku, Integer quantity |
| **CartDTO** | Response | The current state of the user's shopping cart. | String userId, List<CartItemDTO>, BigDecimal subtotal |
| **OrderDetailsDTO** | Response | Full details of a finalized or pending order. | String orderId, String userId, String status, BigDecimal totalAmount, List<OrderItemDTO> items |

#### Review Service DTOs

| Class Name | Type | Description | Key Attributes (Example) |
| --- | --- | --- | --- |
| **ReviewDTO** | Request/Response | Data structure for submitting or viewing a review. | String productId, Integer rating, String comment, String userId |
| **RatingSummaryDTO** | Response | Aggregated rating statistics for a product. | String productId, BigDecimal averageRating, Long reviewCount |

#### Search Service DTOs

| Class Name | Type | Description | Key Attributes (Example) |
| --- | --- | --- | --- |
| **SearchRequestDTO** | Request | Parameters for executing a product search. | String queryText, List<String> categoryFilters, String sortField, String sortOrder |
| **SearchResponseDTO** | Response | Container for search results, including pagination metadata. | PaginationDTO pagination, List<ProductSummaryDTO> results |

## 2. Service-Specific Low-Level Design

This section outlines the internal models, service logic, and custom exceptions for each core microservice, based on the responsibilities defined in the HLD.

### 2.1 Identity and Access Management (IAM) Service

**Responsibility:** Authentication (OAuth2/JWT), Token generation/validation, Role management.**Database:** Relational (User credentials, Roles, Permissions).

#### 2.1.1 Internal Model Classes

| Class Name | Description | Key Attributes |
| --- | --- | --- |
| **User** | Represents a platform user with login credentials. | `Long id`, `String username`, `String hashedPassword`, `Set<Role> roles`, `boolean enabled` |
| **Role** | User roles for permission checks. | `Long id`, `String name` (e.g., ROLE\_CUSTOMER, ROLE\_ADMIN) |
| **JWTToken** | Stored or blacklisted tokens. | `String token`, `<span type="placeholder" placeholder-type="date"></span> expirationDate`, `String userId` |

#### 2.1.2 Service Classes and Signatures

| Class Name | Description | Key Methods |
| --- | --- | --- |
| **AuthService** | Handles core authentication and token logic. | `login(String username, String password)`: returns `String jwt` |
|  |  | `validateToken(String jwt)`: returns `UserDetailsDTO` |
|  |  | `generateToken(User user)`: returns `String jwt` |
| **UserService** | Manages user account credentials. | `registerNewUser(RegistrationDTO details)` |
|  |  | `updatePassword(String userId, String newPassword)` |

#### 2.1.5 API Endpoint Definitions

| Endpoint | Method | Description | Permissions Required |
| --- | --- | --- | --- |
| `/auth/login` | POST | Authenticates user and generates JWT. | N/A |
| `/auth/validate` | POST | **Internal/Gateway Only:** Validates JWT. | N/A |
| `/users/register` | POST | Registers a new IAM user account. | N/A |

### 2.2 User Service

**Responsibility:** User registration and profile management (address, details).**Database:** Relational (User details).

#### 2.2.1 Internal Model Classes

| Class Name | Description | Key Attributes |
| --- | --- | --- |
| **UserProfile** | User's non-sensitive profile data. | `String userId` (PK/Link to IAM), `String firstName`, `String lastName`, `String email` |
| **Address** | User shipping or billing address. | `Long id`, `String userId`, `String street`, `String city`, `String zipCode` |

#### 2.2.2 Service Classes and Signatures

| Class Name | Description | Key Methods |
| --- | --- | --- |
| **UserManagerService** | Manages profile creation and updates. | `fetchUserProfile(String userId)`: returns `UserProfile` |
|  |  | `updateProfile(String userId, ProfileUpdateDTO)` |
|  |  | `addAddress(String userId, AddressDTO)` |

#### 2.2.5 API Endpoint Definitions

| Endpoint | Method | Description | Permissions Required |
| --- | --- | --- | --- |
| `/profiles/{id}` | GET | Retrieves user profile details. | ROLE\_CUSTOMER |
| `/profiles/{id}` | PUT | Updates user profile details or address. | ROLE\_CUSTOMER |

### 2.3 Product Service

**Responsibility:** Catalog management and inventory tracking.**Database:** Relational (Product catalog, Inventory).

#### 2.3.1 Internal Model Classes

| Class Name | Description | Key Attributes |
| --- | --- | --- |
| **Product** | Represents a catalog item. | `String sku`, `String name`, `String description`, `BigDecimal price`, `String categoryId` |
| **Inventory** | Tracks stock level. | `String sku`, `Integer currentStock`, `Integer reservedStock` |
| **Category** | Product categorization. | `String id`, `String name` |

#### 2.3.2 Service Classes and Signatures

| Class Name | Description | Key Methods |
| --- | --- | --- |
| **ProductCatalogService** | Manages CRUD operations on products. | `getProductDetails(String sku)`: returns `Product` |
|  |  | `createProduct(ProductCreationDTO)` |
| **InventoryManagerService** | Handles stock reservations and updates. | `reserveStock(List<InventoryReservationRequest>)` |
|  |  | `decrementStock(List<InventoryReservationRequest>)` (Called post-payment) |
| **KafkaProducerService** | Sends async updates. | `sendInventoryResult(InventoryReservedEvent)` |

#### 2.3.5 API Endpoint Definitions

| Endpoint | Method | Description | Permissions Required |
| --- | --- | --- | --- |
| `/products` | POST | Creates a new product. | ROLE\_ADMIN |
| `/products/{id}` | GET | Retrieves product details. | N/A |
| `/products/{id}` | PUT | Updates product details. | ROLE\_ADMIN |
| `/inventory/reserve` | POST | **Internal Only:** Synchronously reserves inventory. | ROLE\_SERVICE |

### 2.4 Order Service

**Responsibility:** Shopping carts, order creation, order state transitions.**Database:** Relational (Orders, Order items, Cart).

#### 2.4.1 Internal Model Classes

| Class Name | Description | Key Attributes |
| --- | --- | --- |
| **Cart** | Represents a user's current cart. | `String userId`, `Set<CartItem>`, `<span type="placeholder" placeholder-type="date"></span> lastUpdated` |
| **Order** | The core order record. | `String id`, `String userId`, `BigDecimal totalAmount`, `String status` (e.g., PENDING, PAID, FAILED), `<span type="placeholder" placeholder-type="date"></span> creationDate` |
| **OrderItem** | Line items within an order. | `Long id`, `String orderId`, `String sku`, `Integer quantity`, `BigDecimal unitPrice` |

#### 2.4.2 Service Classes and Signatures

| Class Name | Description | Key Methods |
| --- | --- | --- |
| **CartService** | Manages cart CRUD operations. | `addToCart(String userId, CartItemDTO)` |
|  |  | `getCart(String userId)`: returns `Cart` |
| **OrderManagerService** | Handles the core order lifecycle. | `createOrder(String userId, Cart)`: returns `Order` |
|  |  | `updateOrderStatus(String orderId, OrderStatus status)` |
| **KafkaProducerService** | Publishes order events. | `sendOrderPlaced(OrderPlacedEvent)` |
| **KafkaListenerService** | Consumes inventory/payment results. | `handleInventoryUpdate(InventoryReservedEvent)` |
|  |  | `handlePaymentResult(PaymentResultEvent)` |

#### 2.4.5 API Endpoint Definitions

| Endpoint | Method | Description | Permissions Required |
| --- | --- | --- | --- |
| `/orders` | POST | Creates a new order from the cart. | ROLE\_CUSTOMER |
| `/orders/{orderId}` | GET | Retrieves order status and details. | ROLE\_CUSTOMER |
| `/carts` | GET | Retrieves the user's current shopping cart. | ROLE\_CUSTOMER |
| `/carts/items` | POST | Adds an item to the shopping cart. | ROLE\_CUSTOMER |

### 2.5 Payment Service

**Responsibility:** Integrates with external payment gateways, processes payments.**Database:** Relational (Payment transactions).

#### 2.5.1 Internal Model Classes

| Class Name | Description | Key Attributes |
| --- | --- | --- |
| **PaymentTransaction** | Record of a payment attempt. | `String id`, `String orderId`, `BigDecimal amount`, `String gatewayRef`, `String status` (e.g., INITIATED, SUCCESS, FAILED) |

#### 2.5.2 Service Classes and Signatures

| Class Name | Description | Key Methods |
| --- | --- | --- |
| **PaymentProcessorService** | Orchestrates payment gateway interaction. | `initiatePayment(OrderPlacedEvent)`: returns `PaymentTransaction` |
|  |  | `handleGatewayCallback(String transactionId, PaymentGatewayResponse)` |
| **KafkaListenerService** | Consumes order placement events. | `handleOrderPlaced(OrderPlacedEvent)` |
| **KafkaProducerService** | Publishes payment results. | `sendPaymentResult(PaymentResultEvent)` |

#### 2.5.5 API Endpoint Definitions

| Endpoint | Method | Description | Permissions Required |
| --- | --- | --- | --- |
| N/A | N/A | Primarily asynchronous via Kafka. No external REST API. | N/A |

### 2.6 Invoice Service

**Responsibility:** Generates billing documents.**Database:** Relational (Billing records, Invoice metadata).

#### 2.6.1 Internal Model Classes

| Class Name | Description | Key Attributes |
| --- | --- | --- |
| **Invoice** | Metadata about the generated bill. | `String id`, `String orderId`, `<span type="placeholder" placeholder-type="file"></span> pdfStorageLink`, `BigDecimal taxAmount`, `<span type="placeholder" placeholder-type="date"></span> issueDate` |
| **BillingRecord** | Data used for calculation. | `String orderId`, `BigDecimal subtotal`, `BigDecimal discount`, `BigDecimal finalTotal` |

#### 2.6.2 Service Classes and Signatures

| Class Name | Description | Key Methods |
| --- | --- | --- |
| **InvoiceGeneratorService** | Performs calculation and document generation. | `generateInvoice(Order orderDetails)`: returns `Invoice` |
|  |  | `calculateTaxAndDiscounts(Order orderDetails)` |
|  |  | `storeInvoiceDocument(Invoice invoice, byte[] pdfContent)` |
| **KafkaListenerService** | Consumes successful payment events. | `handlePaymentSuccessful(PaymentResultEvent)` |

#### 2.6.5 API Endpoint Definitions

| Endpoint | Method | Description | Permissions Required |
| --- | --- | --- | --- |
| `/invoices/{orderId}` | GET | Retrieves the finalized invoice document (or link). | ROLE\_CUSTOMER |

### 2.7 Notification Service

**Responsibility:** Handles all user notifications (email/SMS).**Database:** NoSQL (Notification logs/templates).

#### 2.7.1 Internal Model Classes

| Class Name | Description | Key Attributes |
| --- | --- | --- |
| **NotificationLog** | History of sent notifications. | `String id`, `String userId`, `String type` (e.g., EMAIL, SMS), `String templateId`, `<span type="placeholder" placeholder-type="date"></span> sentDate`, `String status` |
| **NotificationTemplate** | Predefined message templates. | `String templateId`, `String channel`, `String bodyTemplate` |

#### 2.7.2 Service Classes and Signatures

| Class Name | Description | Key Methods |
| --- | --- | --- |
| **NotificationSenderService** | Abstraction for external providers. | `sendEmail(String recipient, String templateId, Map<String, Object> data)` |
|  |  | `sendSms(String phoneNumber, String message)` |
| **KafkaListenerService** | Consumes events for triggers. | `handleOrderPlaced(OrderPlacedEvent)` |
|  |  | `handlePaymentResult(PaymentResultEvent)` |

#### 2.7.5 API Endpoint Definitions

| Endpoint | Method | Description | Permissions Required |
| --- | --- | --- | --- |
| N/A | N/A | Primarily asynchronous via Kafka. No external REST API. | N/A |

### 2.8 Review Service

**Responsibility:** Manages customer product reviews and ratings.**Database:** Relational (Reviews, Ratings).

#### 2.8.1 Internal Model Classes

| Class Name | Description | Key Attributes |
| --- | --- | --- |
| **Review** | A single customer review. | `Long id`, `String userId`, `String productId`, `Integer rating` (1-5), `String comment`, `<span type="placeholder" placeholder-type="date"></span> submissionDate` |
| **AggregateRating** | Pre-calculated average rating per product. | `String productId`, `BigDecimal averageRating`, `Long reviewCount` |

#### 2.8.2 Service Classes and Signatures

| Class Name | Description | Key Methods |
| --- | --- | --- |
| **ReviewManagerService** | Handles submission, moderation, and rating calculation. | `submitReview(ReviewDTO)` |
|  |  | `getReviewsByProductId(String productId)`: returns `List<Review>` |
|  |  | `calculateAggregateRating(String productId)` |

#### 2.8.5 API Endpoint Definitions

| Endpoint | Method | Description | Permissions Required |
| --- | --- | --- | --- |
| `/reviews` | POST | Submits a new review. | ROLE\_CUSTOMER |
| `/reviews/products/{productId}` | GET | Retrieves all reviews for a product. | N/A |
| `/reviews/products/{productId}/rating` | GET | Retrieves aggregated rating score. | N/A |

### 2.9 Search Service

**Responsibility:** Provides fast, relevant product search functionality.**Database:** NoSQL (Elasticsearch Search Index).

#### 2.9.1 Internal Model Classes

| Class Name | Description | Key Attributes |
| --- | --- | --- |
| **ProductSearchIndex** | Flattened, denormalized product data for search. | `String productId`, `String name`, `String description`, `BigDecimal price`, `List<String> keywords` |
| **SearchQuery** | Internal representation of a complex user search. | `String textQuery`, `Map<String, List<String>> filters`, `String sortField`, `String sortOrder` |

#### 2.9.2 Service Classes and Signatures

| Class Name | Description | Key Methods |
| --- | --- | --- |
| **SearchQueryService** | Executes searches against the index. | `executeSearch(SearchQuery query)`: returns `List<ProductSummaryDTO>` |
|  |  | `filterAndSortResults(List<ProductSummaryDTO>)` |
| **ProductDataIndexer** | Listens for product changes and updates the index. | `updateIndex(ProductDTO)` (Triggered by Kafka/Internal Sync) |

#### 2.9.5 API Endpoint Definitions

| Endpoint | Method | Description | Permissions Required |
| --- | --- | --- | --- |
| `/search/products` | GET | Executes a product search based on query parameters. | N/A |

## 3. External API Gateway Definition

The following table summarizes the public-facing APIs that must be exposed via the API Gateway. The Gateway will handle routing, load balancing, and authentication enforcement (by calling the IAM Service) before forwarding the request.


| Microservice | Endpoint (Path) | Method | Permissions Required | Description |
| --- | --- | --- | --- | --- |
| **IAM** | `/auth/login` | POST | N/A | Authenticates user and generates JWT. |
| **IAM** | `/users/register` | POST | N/A | Registers a new user. |
| **Order** | `/orders` | POST | ROLE\_CUSTOMER | Creates an order from the cart. |
| **Order** | `/orders/{orderId}` | GET | ROLE\_CUSTOMER | Retrieves order details. |
| **Order** | `/carts` | GET | ROLE\_CUSTOMER | Retrieves the shopping cart. |
| **Order** | `/carts/items` | POST | ROLE\_CUSTOMER | Adds item to the cart. |
| **Product** | `/products/{id}` | GET | N/A | Retrieves product details. |
| **Product** | `/products` | POST/PUT | ROLE\_ADMIN | Catalog management (Create/Update). |
| **Invoice** | `/invoices/{orderId}` | GET | ROLE\_CUSTOMER | Retrieves the invoice document. |
| **Search** | `/search/products` | GET | N/A | Executes a product search with filters/sorting. |
| **User** | `/profiles/{id}` | GET/PUT | ROLE\_CUSTOMER | View or update user profile/address. |
| **Review** | `/reviews` | POST | ROLE\_CUSTOMER | Submits a new product review. |
| **Review** | `/reviews/products/{productId}` | GET | N/A | Retrieves product reviews. |
| **Review** | `/reviews/products/{productId}/rating` | GET | N/A | Retrieves aggregated rating. |


