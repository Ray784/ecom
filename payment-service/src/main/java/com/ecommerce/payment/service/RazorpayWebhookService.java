package com.ecommerce.payment.service;

import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.payment.model.PaymentTransaction;
import com.ecommerce.payment.repository.PaymentTransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Verifies and applies Razorpay webhooks.
 *
 * <p>The X-Razorpay-Signature header must equal the hex HMAC-SHA256 of the raw body keyed with the
 * webhook secret, otherwise PAY_4002 is raised. Both the nested Razorpay payload shape and flat
 * convenience fields are accepted. The verdict is applied through the payment processor so the
 * PaymentResult event reaches the success or failure topic exactly like a synchronous charge.
 */
@Service
public class RazorpayWebhookService {

  public static final String EVENT_PAYMENT_CAPTURED = "payment.captured";
  public static final String EVENT_PAYMENT_FAILED = "payment.failed";
  public static final String EVENT_ORDER_PAID = "order.paid";
  public static final String STATUS_PROCESSED = "processed";
  public static final String STATUS_ALREADY_PROCESSED = "already_processed";
  public static final String DEFAULT_FAILURE_REASON = "Payment failed at Razorpay";

  private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookService.class);
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final PaymentTransactionRepository repository;
  private final PaymentProcessorService paymentProcessorService;
  private final String webhookSecret;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public RazorpayWebhookService(
      PaymentTransactionRepository repository,
      PaymentProcessorService paymentProcessorService,
      @Value("${razorpay.webhook-secret:rzp_test_webhook_secret}") String webhookSecret) {
    this.repository = repository;
    this.paymentProcessorService = paymentProcessorService;
    this.webhookSecret = webhookSecret;
  }

  /** Verifies the signature, applies the verdict and returns the acknowledgement body. */
  public Map<String, Object> processWebhook(String rawBody, String signature) {
    verifySignature(rawBody, signature);
    JsonNode root = parse(rawBody);
    JsonNode entity = root.path("payload").path("payment").path("entity");
    String event = text(root, "event");
    boolean approved = resolveApproved(event, root.get("approved"));
    String reason =
        approved
            ? null
            : firstNonBlank(
                text(root, "reason"), text(entity, "error_description"), DEFAULT_FAILURE_REASON);

    PaymentTransaction transaction = resolveTransaction(root, entity);
    String outcome;
    if (transaction.isTerminal()) {
      boolean alreadySucceeded = PaymentTransaction.STATUS_SUCCESS.equals(transaction.getStatus());
      if (alreadySucceeded != approved) {
        throw CodedErrorFactory.create(
            MappedError.PAY_4003, transaction.getId(), transaction.getStatus());
      }
      outcome = STATUS_ALREADY_PROCESSED;
    } else {
      transaction =
          paymentProcessorService.handleGatewayCallback(transaction.getId(), approved, reason);
      outcome = STATUS_PROCESSED;
    }
    log.info("Razorpay webhook {} for transaction {} {}", event, transaction.getId(), outcome);

    String effectiveEvent =
        event != null ? event : approved ? EVENT_PAYMENT_CAPTURED : EVENT_PAYMENT_FAILED;
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("status", outcome);
    response.put("event", effectiveEvent);
    response.put("transactionId", transaction.getId());
    response.put("orderId", transaction.getOrderId());
    response.put("paymentStatus", transaction.getStatus());
    response.put("gatewayRef", transaction.getGatewayRef());
    return response;
  }

  /** Returns the lowercase hex HMAC-SHA256 of the body keyed with the secret. */
  public static String sign(String rawBody, String secret) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("HmacSHA256 is not available", ex);
    }
  }

  private void verifySignature(String rawBody, String signature) {
    if (rawBody == null || signature == null || signature.isBlank()) {
      throw CodedErrorFactory.create(MappedError.PAY_4002);
    }
    byte[] expected = sign(rawBody, webhookSecret).getBytes(StandardCharsets.UTF_8);
    byte[] supplied = signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expected, supplied)) {
      throw CodedErrorFactory.create(MappedError.PAY_4002);
    }
  }

  private JsonNode parse(String rawBody) {
    try {
      JsonNode root = objectMapper.readTree(rawBody);
      if (root == null || !root.isObject()) {
        throw CodedErrorFactory.create(MappedError.PAY_4002);
      }
      return root;
    } catch (JsonProcessingException ex) {
      throw CodedErrorFactory.create(MappedError.PAY_4002, ex);
    }
  }

  private static boolean resolveApproved(String event, JsonNode approvedNode) {
    if (EVENT_PAYMENT_CAPTURED.equals(event) || EVENT_ORDER_PAID.equals(event)) {
      return true;
    }
    if (EVENT_PAYMENT_FAILED.equals(event)) {
      return false;
    }
    if (event == null && approvedNode != null && approvedNode.isBoolean()) {
      return approvedNode.booleanValue();
    }
    throw CodedErrorFactory.create(MappedError.PAY_4002);
  }

  /**
   * Finds the transaction by transaction ID, pay_ reference, order_ ID or platform order ID, in
   * that order, and creates one for webhook initiated captures of an unknown order.
   */
  private PaymentTransaction resolveTransaction(JsonNode root, JsonNode entity) {
    String transactionId = text(root, "transactionId");
    if (transactionId != null) {
      return repository
          .findById(transactionId)
          .orElseThrow(() -> CodedErrorFactory.create(MappedError.PAY_4004, transactionId));
    }
    String paymentId = firstNonBlank(text(root, "paymentId"), text(entity, "id"));
    String razorpayOrderId = firstNonBlank(text(root, "razorpayOrderId"), text(entity, "order_id"));
    String orderId =
        firstNonBlank(
            text(root, "orderId"), text(entity, "receipt"), text(entity.path("notes"), "orderId"));

    Optional<PaymentTransaction> found = Optional.empty();
    if (paymentId != null) {
      found = repository.findFirstByGatewayRefOrderByCreatedAtDesc(paymentId);
    }
    if (found.isEmpty() && razorpayOrderId != null) {
      found = repository.findFirstByRazorpayOrderIdOrderByCreatedAtDesc(razorpayOrderId);
    }
    if (found.isEmpty() && orderId != null) {
      found = repository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
    }
    if (found.isPresent()) {
      return found.get();
    }
    if (orderId == null) {
      String reference = firstNonBlank(paymentId, razorpayOrderId);
      if (reference == null) {
        throw CodedErrorFactory.create(MappedError.PAY_4002);
      }
      throw CodedErrorFactory.create(MappedError.PAY_4004, reference);
    }
    PaymentTransaction created =
        new PaymentTransaction(
            "TXN-" + UUID.randomUUID(),
            orderId,
            firstNonBlank(
                text(root, "userId"),
                text(entity.path("notes"), "userId"),
                text(entity, "payer_user_id")),
            amount(root, entity),
            paymentId,
            PaymentTransaction.STATUS_INITIATED);
    created.setRazorpayOrderId(razorpayOrderId);
    repository.save(created);
    log.info("Created transaction {} for webhook initiated payment of order {}",
        created.getId(), orderId);
    return created;
  }

  /** Reads the flat rupee amount or converts the nested paise amount, null when absent. */
  private static BigDecimal amount(JsonNode root, JsonNode entity) {
    JsonNode flat = root.get("amount");
    if (flat != null && flat.isNumber()) {
      return flat.decimalValue();
    }
    JsonNode paise = entity.get("amount");
    if (paise != null && paise.isIntegralNumber()) {
      return BigDecimal.valueOf(paise.longValue()).movePointLeft(2);
    }
    return null;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull() || value.isContainerNode()) {
      return null;
    }
    String text = value.asText();
    return text.isBlank() ? null : text;
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }
}
