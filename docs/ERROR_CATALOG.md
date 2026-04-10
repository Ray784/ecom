E-commerce Microservices Application: Detailed Error Catalog

This document defines a standardized, internal error catalog for the core microservices of the e-commerce application. These standardized error codes and messages (templates) are critical for consistent logging, monitoring, and providing clear feedback to both internal service clients and external API consumers.


Each service has a unique prefix (e.g., IAM, ORDER, PROD) followed by a four-digit number, where the first digit indicates the HTTP status category (e.g., 4xx or 5xx).

# 1. Identity and Access Management (IAM) Service Error Codes (IAM)

The IAM Service handles authentication, token management, and user registration credentials.


| Code | HTTP Status | Message Template | Description |
| --- | --- | --- | --- |
| **IAM\_4001** | 401 UNAUTHORIZED | Invalid username or password provided. | Generic login failure. |
| **IAM\_4002** | 401 UNAUTHORIZED | Access Token is missing, invalid, or expired. | Token validation failed during API Gateway check. |
| **IAM\_4003** | 403 FORBIDDEN | User account {0} is currently disabled or locked. | Authentication successful, but access is denied. |
| **IAM\_4004** | 400 BAD REQUEST | Username {0} is already taken. | Attempted registration with an existing username. |
| **IAM\_4005** | 400 BAD REQUEST | Password complexity requirements not met. | User password failed validation rules. |
| **IAM\_4006** | 400 BAD REQUEST | Missing required registration field: {0}. | Incomplete data submitted for registration. |
| **IAM\_4007** | 401 UNAUTHORIZED | Token type is not supported for this endpoint. | E.g., using a refresh token where an access token is expected. |
| **IAM\_4008** | 403 FORBIDDEN | User {0} does not possess the required role: {1}. | Authorization failure based on defined roles. |
| **IAM\_4009** | 400 BAD REQUEST | Provided user ID {0} is syntactically invalid. | Format violation for user identifiers. |
| **IAM\_5001** | 503 UNAVAILABLE | External service (e.g., LDAP/OAuth provider) is unreachable. | Failure integrating with external identity systems. |
| **IAM\_5002** | 500 INTERNAL SERVER ERROR | Error generating the JWT token for user {0}. | Internal cryptographic or signing failure. |

## 2. User Service Error Codes (USER)

The User Service manages user profiles and physical addresses.


| Code | HTTP Status | Message Template | Description |
| --- | --- | --- | --- |
| **USER\_4001** | 404 NOT FOUND | User profile with ID {0} was not found. | Profile lookup failure. |
| **USER\_4002** | 409 CONFLICT | User with email {0} already exists in the system. | Duplication during profile creation. |
| **USER\_4003** | 400 BAD REQUEST | Address limit exceeded for user {0}. Maximum allowed is {1}. | Violation of a business rule for saved addresses. |
| **USER\_4004** | 400 BAD REQUEST | Email address {0} is not a valid format. | Input validation failed for the email field. |
| **USER\_4005** | 400 BAD REQUEST | Address field {0} must not be empty. | Missing required address component. |
| **USER\_4006** | 400 BAD REQUEST | Cannot delete default address {0} without setting a new default. | Attempting to remove the primary address prematurely. |
| **USER\_4007** | 403 FORBIDDEN | Cannot modify profile {0}: access denied. | Attempted modification by a user who is not the profile owner. |
| **USER\_4008** | 404 NOT FOUND | Address with ID {0} for user {1} not found. | Specific address lookup failure. |
| **USER\_4009** | 400 BAD REQUEST | Provided zip code {0} is not valid for region. | Geographical validation failure. |
| **USER\_5001** | 500 INTERNAL SERVER ERROR | Failed to save profile data for user {0}. | Database persistence error. |
| **USER\_5002** | 503 UNAVAILABLE | Dependent IAM Service is currently unreachable. | Synchronous dependency failure. |

## 3. Product Service Error Codes (PROD)

The Product Service handles catalog data and inventory tracking.


| Code | HTTP Status | Message Template | Description |
| --- | --- | --- | --- |
| **PROD\_4001** | 404 NOT FOUND | Product with SKU {0} not found in the catalog. | Product lookup failure. |
| **PROD\_4002** | 400 BAD REQUEST | Insufficient stock for product {0}. Only {1} available. | Reservation failure due to low inventory. |
| **PROD\_4003** | 400 BAD REQUEST | Invalid category ID {0} provided for product. | Category existence check failed during product creation. |
| **PROD\_4004** | 400 BAD REQUEST | Product SKU {0} already exists. | Attempted creation of a duplicate SKU. |
| **PROD\_4005** | 400 BAD REQUEST | Product price must be a positive value. | Price validation failure. |
| **PROD\_4006** | 400 BAD REQUEST | Quantity {0} for product {1} is not valid (must be > 0). | Quantity check during reservation request. |
| **PROD\_4007** | 400 BAD REQUEST | Cannot reserve negative stock. | Invalid reservation request. |
| **PROD\_4008** | 409 CONFLICT | Inventory update failed due to concurrent modification lock. | Optimistic locking failure during stock update. |
| **PROD\_4009** | 404 NOT FOUND | Category with ID {0} does not exist. | Failure to retrieve category details. |
| **PROD\_4010** | 400 BAD REQUEST | Product name or description exceeds maximum length of {0}. | Data length validation. |
| **PROD\_5001** | 503 UNAVAILABLE | Inventory database connection failed. | Database access error for inventory tracking. |
| **PROD\_5002** | 500 INTERNAL SERVER ERROR | Failed to publish inventory change event for SKU {0}. | Kafka producer failure. |

## 4. Order Service Error Codes (ORDER)

The Order Service manages shopping carts, order creation, and status transitions.


| Code | HTTP Status | Message Template | Description |
| --- | --- | --- | --- |
| **ORDER\_4001** | 400 BAD REQUEST | Cannot place order: cart is empty for user {0}. | Order attempt without items. |
| **ORDER\_4002** | 404 NOT FOUND | Order with ID {0} was not found. | Order lookup failure. |
| **ORDER\_4003** | 400 BAD REQUEST | Order {0} is in status {1} and cannot be modified/cancelled. | Invalid state transition attempt. |
| **ORDER\_4004** | 400 BAD REQUEST | Item {0} failed inventory check. Out of stock. | Synchronous check before order finalization. |
| **ORDER\_4005** | 404 NOT FOUND | Cart for user {0} does not exist or has expired. | Cart retrieval failure. |
| **ORDER\_4006** | 400 BAD REQUEST | Cannot add item {0} to cart: quantity {1} is invalid. | Input validation for item quantity. |
| **ORDER\_4007** | 400 BAD REQUEST | User must specify a shipping address before creating order. | Missing required data for order submission. |
| **ORDER\_4008** | 400 BAD REQUEST | The order total {0} does not match the calculated amount {1}. | Integrity check failure. |
| **ORDER\_4009** | 400 BAD REQUEST | Maximum number of items ({0}) exceeded in cart. | Cart size business limit. |
| **ORDER\_4010** | 400 BAD REQUEST | Order {0} cannot be processed due to payment failure. | Order finalization blocked by asynchronous payment result. |
| **ORDER\_5001** | 500 INTERNAL SERVER ERROR | Failed to publish OrderPlaced event for Order {0}. | Critical Kafka producer failure for core workflow. |
| **ORDER\_5002** | 503 UNAVAILABLE | Cannot retrieve pricing details from Product Service. | Synchronous dependency failure (Product Service). |

## 5. Payment Service Error Codes (PAY)

The Payment Service handles external payment gateway integration and transactions.


| Code | HTTP Status | Message Template | Description |
| --- | --- | --- | --- |
| **PAY\_4001** | 400 BAD REQUEST | Payment failed for Order {0}: Card declined by gateway. | Definitive transaction failure. |
| **PAY\_4002** | 400 BAD REQUEST | Invalid payment method or details provided. | Input validation specific to payment method. |
| **PAY\_4003** | 409 CONFLICT | Transaction {0} already processed. Status is {1}. | Attempt to re-process an already completed transaction. |
| **PAY\_4004** | 404 NOT FOUND | Transaction record for Order {0} not found. | Internal transaction record lookup failure. |
| **PAY\_4005** | 400 BAD REQUEST | Amount {0} does not match the expected order total. | Security/integrity check failure. |
| **PAY\_4006** | 400 BAD REQUEST | Payment initiation blocked: user {0} is suspected fraud risk. | Internal fraud detection trigger. |
| **PAY\_5001** | 503 UNAVAILABLE | External payment gateway is currently unavailable. | Failure to communicate with third-party vendor. |
| **PAY\_5002** | 500 INTERNAL SERVER ERROR | Gateway communication failed for transaction {0}. Retry later. | Communication timeout or unexpected error. |
| **PAY\_5003** | 500 INTERNAL SERVER ERROR | Failed to publish PaymentResult event for Order {0}. | Critical Kafka producer failure after processing. |
| **PAY\_5004** | 500 INTERNAL SERVER ERROR | Failed to map external gateway error code {0} to internal response. | Unhandled third-party response. |

## 6. Invoice Service Error Codes (INV)

The Invoice Service generates and stores billing documents.


| Code | HTTP Status | Message Template | Description |
| --- | --- | --- | --- |
| **INV\_4001** | 404 NOT FOUND | Invoice for Order {0} has not yet been generated. | Attempt to retrieve an invoice before processing. |
| **INV\_4002** | 404 NOT FOUND | Order details needed for invoice calculation not found for {0}. | Dependency data missing. |
| **INV\_4003** | 400 BAD REQUEST | Invoice generation requires a PAID status for Order {0}. | Business logic validation. |
| **INV\_5001** | 500 INTERNAL SERVER ERROR | Failed to generate PDF document for Order {0}. | Document creation library failure. |
| **INV\_5002** | 503 UNAVAILABLE | Failed to store the generated invoice in external file storage. | S3/Cloud Storage integration failure. |
| **INV\_5003** | 500 INTERNAL SERVER ERROR | Tax calculation service returned an unexpected error for order {0}. | Dependency failure during billing computation. |

## 7. Notification Service Error Codes (NOTIF)

The Notification Service handles asynchronous communication (email/SMS/push) to users.


| Code | HTTP Status | Message Template | Description |
| --- | --- | --- | --- |
| **NOTIF\_4001** | 400 BAD REQUEST | Missing recipient address for notification type {0}. | Required contact information missing (e.g., no email address). |
| **NOTIF\_4002** | 404 NOT FOUND | Notification template with ID {0} not found. | Template retrieval failure. |
| **NOTIF\_4003** | 400 BAD REQUEST | Required data parameter {0} missing for template {1}. | Data mapping error during message assembly. |
| **NOTIF\_5001** | 503 UNAVAILABLE | External email provider connection failed. | Email vendor integration failure. |
| **NOTIF\_5002** | 503 UNAVAILABLE | SMS gateway connection timed out. | SMS vendor integration failure. |
| **NOTIF\_5003** | 500 INTERNAL SERVER ERROR | Failed to save notification log for user {0}. | NoSQL database persistence error for logs. |

## 8. Review Service Error Codes (REVIEW)

The Review Service manages customer product reviews and ratings.


| Code | HTTP Status | Message Template | Description |
| --- | --- | --- | --- |
| **REVIEW\_4001** | 400 BAD REQUEST | Review contains prohibited language or content. | Moderation failure during submission. |
| **REVIEW\_4002** | 409 CONFLICT | User {0} has already submitted a review for product {1}. | Duplicate submission violation. |
| **REVIEW\_4003** | 400 BAD REQUEST | Rating value {0} is outside the allowed range (1 to 5). | Input validation failure for rating score. |
| **REVIEW\_4004** | 404 NOT FOUND | Product {0} does not exist, cannot submit review. | Dependency check failure (Product Service). |
| **REVIEW\_4005** | 400 BAD REQUEST | Review comment exceeds maximum length of {0} characters. | Data length validation. |
| **REVIEW\_4006** | 403 FORBIDDEN | User {0} cannot submit a review for product {1}: must be a verified purchaser. | Business rule restriction. |
| **REVIEW\_4007** | 404 NOT FOUND | Review with ID {0} not found. | Specific review record lookup failure. |
| **REVIEW\_5001** | 500 INTERNAL SERVER ERROR | Error calculating aggregate rating for product {0}. | Computation failure. |

## 9. Search Service Error Codes (SEARCH)

The Search Service provides fast, relevant product search functionality.


| Code | HTTP Status | Message Template | Description |
| --- | --- | --- | --- |
| **SEARCH\_4001** | 400 BAD REQUEST | Search query text must be at least {0} characters long. | Input validation for query string. |
| **SEARCH\_4002** | 400 BAD REQUEST | Invalid sort field {0} provided in search request. | Parameter validation failure. |
| **SEARCH\_4003** | 400 BAD REQUEST | Invalid category filter value: {0}. | Filter validation failure. |
| **SEARCH\_4004** | 400 BAD REQUEST | Pagination offset or size is invalid. | Input validation for pagination parameters. |
| **SEARCH\_5001** | 503 UNAVAILABLE | Search index is currently unavailable or unreachable. | Elasticsearch connection failure. |
| **SEARCH\_5002** | 500 INTERNAL SERVER ERROR | Indexing failed for product SKU {0}. Data integrity compromised. | Failure during the indexing process. |
| **SEARCH\_5003** | 500 INTERNAL SERVER ERROR | Unexpected error during query execution: {0}. | Low-level search engine error. |


