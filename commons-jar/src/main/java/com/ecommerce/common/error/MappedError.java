package com.ecommerce.common.error;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Internal coded error catalog shared by all services.
 *
 * <p>Every constant mirrors one row of docs/ERROR_CATALOG.md: the enum name equals the error
 * code, and each entry carries the HTTP status and the MessageFormat style message template.
 * The first digit of the numeric part is 4 for client errors and 5 for server errors.
 */
public enum MappedError {
  // IAM service
  IAM_4001(401, "Invalid username or password provided."),
  IAM_4002(401, "Access Token is missing, invalid, or expired."),
  IAM_4003(403, "User account {0} is currently disabled or locked."),
  IAM_4004(400, "Username {0} is already taken."),
  IAM_4005(400, "Password complexity requirements not met."),
  IAM_4006(400, "Missing required registration field: {0}."),
  IAM_4007(401, "Token type is not supported for this endpoint."),
  IAM_4008(403, "User {0} does not possess the required role: {1}."),
  IAM_4009(400, "Provided user ID {0} is syntactically invalid."),
  IAM_5001(503, "External service (e.g., LDAP/OAuth provider) is unreachable."),
  IAM_5002(500, "Error generating the JWT token for user {0}."),

  // USER service
  USER_4001(404, "User profile with ID {0} was not found."),
  USER_4002(409, "User with email {0} already exists in the system."),
  USER_4003(400, "Address limit exceeded for user {0}. Maximum allowed is {1}."),
  USER_4004(400, "Email address {0} is not a valid format."),
  USER_4005(400, "Address field {0} must not be empty."),
  USER_4006(400, "Cannot delete default address {0} without setting a new default."),
  USER_4007(403, "Cannot modify profile {0}: access denied."),
  USER_4008(404, "Address with ID {0} for user {1} not found."),
  USER_4009(400, "Provided zip code {0} is not valid for region."),
  USER_5001(500, "Failed to save profile data for user {0}."),
  USER_5002(503, "Dependent IAM Service is currently unreachable."),

  // PROD service
  PROD_4001(404, "Product with SKU {0} not found in the catalog."),
  PROD_4002(400, "Insufficient stock for product {0}. Only {1} available."),
  PROD_4003(400, "Invalid category ID {0} provided for product."),
  PROD_4004(400, "Product SKU {0} already exists."),
  PROD_4005(400, "Product price must be a positive value."),
  PROD_4006(400, "Quantity {0} for product {1} is not valid (must be > 0)."),
  PROD_4007(400, "Cannot reserve negative stock."),
  PROD_4008(409, "Inventory update failed due to concurrent modification lock."),
  PROD_4009(404, "Category with ID {0} does not exist."),
  PROD_4010(400, "Product name or description exceeds maximum length of {0}."),
  PROD_5001(503, "Inventory database connection failed."),
  PROD_5002(500, "Failed to publish inventory change event for SKU {0}."),

  // ORDER service
  ORDER_4001(400, "Cannot place order: cart is empty for user {0}."),
  ORDER_4002(404, "Order with ID {0} was not found."),
  ORDER_4003(400, "Order {0} is in status {1} and cannot be modified/cancelled."),
  ORDER_4004(400, "Item {0} failed inventory check. Out of stock."),
  ORDER_4005(404, "Cart for user {0} does not exist or has expired."),
  ORDER_4006(400, "Cannot add item {0} to cart: quantity {1} is invalid."),
  ORDER_4007(400, "User must specify a shipping address before creating order."),
  ORDER_4008(400, "The order total {0} does not match the calculated amount {1}."),
  ORDER_4009(400, "Maximum number of items ({0}) exceeded in cart."),
  ORDER_4010(400, "Order {0} cannot be processed due to payment failure."),
  ORDER_5001(500, "Failed to publish OrderPlaced event for Order {0}."),
  ORDER_5002(503, "Cannot retrieve pricing details from Product Service."),

  // PAY service
  PAY_4001(400, "Payment failed for Order {0}: Card declined by gateway."),
  PAY_4002(400, "Invalid payment method or details provided."),
  PAY_4003(409, "Transaction {0} already processed. Status is {1}."),
  PAY_4004(404, "Transaction record for Order {0} not found."),
  PAY_4005(400, "Amount {0} does not match the expected order total."),
  PAY_4006(400, "Payment initiation blocked: user {0} is suspected fraud risk."),
  PAY_5001(503, "External payment gateway is currently unavailable."),
  PAY_5002(500, "Gateway communication failed for transaction {0}. Retry later."),
  PAY_5003(500, "Failed to publish PaymentResult event for Order {0}."),
  PAY_5004(500, "Failed to map external gateway error code {0} to internal response."),

  // INV service
  INV_4001(404, "Invoice for Order {0} has not yet been generated."),
  INV_4002(404, "Order details needed for invoice calculation not found for {0}."),
  INV_4003(400, "Invoice generation requires a PAID status for Order {0}."),
  INV_5001(500, "Failed to generate PDF document for Order {0}."),
  INV_5002(503, "Failed to store the generated invoice in external file storage."),
  INV_5003(500, "Tax calculation service returned an unexpected error for order {0}."),

  // NOTIF service
  NOTIF_4001(400, "Missing recipient address for notification type {0}."),
  NOTIF_4002(404, "Notification template with ID {0} not found."),
  NOTIF_4003(400, "Required data parameter {0} missing for template {1}."),
  NOTIF_5001(503, "External email provider connection failed."),
  NOTIF_5002(503, "SMS gateway connection timed out."),
  NOTIF_5003(500, "Failed to save notification log for user {0}."),

  // REVIEW service
  REVIEW_4001(400, "Review contains prohibited language or content."),
  REVIEW_4002(409, "User {0} has already submitted a review for product {1}."),
  REVIEW_4003(400, "Rating value {0} is outside the allowed range (1 to 5)."),
  REVIEW_4004(404, "Product {0} does not exist, cannot submit review."),
  REVIEW_4005(400, "Review comment exceeds maximum length of {0} characters."),
  REVIEW_4006(403, "User {0} cannot submit a review for product {1}: must be a verified purchaser."),
  REVIEW_4007(404, "Review with ID {0} not found."),
  REVIEW_5001(500, "Error calculating aggregate rating for product {0}."),

  // SEARCH service
  SEARCH_4001(400, "Search query text must be at least {0} characters long."),
  SEARCH_4002(400, "Invalid sort field {0} provided in search request."),
  SEARCH_4003(400, "Invalid category filter value: {0}."),
  SEARCH_4004(400, "Pagination offset or size is invalid."),
  SEARCH_5001(503, "Search index is currently unavailable or unreachable."),
  SEARCH_5002(500, "Indexing failed for product SKU {0}. Data integrity compromised."),
  SEARCH_5003(500, "Unexpected error during query execution: {0}.");

  private static final Map<String, MappedError> BY_CODE =
      Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(MappedError::getCode, Function.identity()));

  private final int httpStatus;
  private final String messageTemplate;

  MappedError(int httpStatus, String messageTemplate) {
    this.httpStatus = httpStatus;
    this.messageTemplate = messageTemplate;
  }

  /** Returns the catalog code, for example IAM_4001. */
  public String getCode() {
    return name();
  }

  public int getHttpStatus() {
    return httpStatus;
  }

  public String getMessageTemplate() {
    return messageTemplate;
  }

  /** Returns true for 4xx client errors that are safe to expose to API consumers. */
  public boolean isClientError() {
    return httpStatus >= 400 && httpStatus < 500;
  }

  /** Returns true for 5xx server side errors. */
  public boolean isServerError() {
    return httpStatus >= 500;
  }

  /**
   * Formats the message template with the given arguments.
   *
   * <p>Arguments are rendered with String.valueOf so numbers are never locale grouped.
   */
  public String format(Object... args) {
    return formatTemplate(messageTemplate, args);
  }

  /** Looks up a catalog entry by its code, returning empty for unknown or null codes. */
  public static Optional<MappedError> fromCode(String code) {
    return code == null ? Optional.empty() : Optional.ofNullable(BY_CODE.get(code));
  }

  static String formatTemplate(String template, Object... args) {
    if (args == null || args.length == 0) {
      return template;
    }
    Object[] rendered = Arrays.stream(args).map(String::valueOf).toArray();
    return new MessageFormat(template).format(rendered);
  }
}
