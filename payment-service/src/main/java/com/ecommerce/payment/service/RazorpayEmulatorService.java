package com.ecommerce.payment.service;

import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * In memory stand in for the Razorpay Orders and Payments API so the platform runs without a real
 * gateway account.
 *
 * <p>Every call must carry HTTP Basic credentials built from the configured key id and key secret,
 * otherwise PAY_4002 is raised. Payment outcomes are chosen from markers found in the payer user
 * ID, the description or the method:
 *
 * <ul>
 *   <li>fraud raises PAY_4006
 *   <li>timeout raises PAY_5002
 *   <li>decline stores a payment with status failed
 *   <li>invalidcard raises PAY_4002
 *   <li>gatewaydown raises PAY_5001
 *   <li>unmapped raises PAY_5004
 *   <li>anything else stores a captured payment and marks the order paid
 * </ul>
 */
@Service
public class RazorpayEmulatorService {

  public static final String ORDER_PREFIX = "order_";
  public static final String PAYMENT_PREFIX = "pay_";
  public static final String ORDER_STATUS_CREATED = "created";
  public static final String ORDER_STATUS_ATTEMPTED = "attempted";
  public static final String ORDER_STATUS_PAID = "paid";
  public static final String PAYMENT_STATUS_CAPTURED = "captured";
  public static final String PAYMENT_STATUS_FAILED = "failed";
  public static final String DECLINE_ERROR_CODE = "BAD_REQUEST_ERROR";
  public static final String DECLINE_ERROR_DESCRIPTION = "Card declined by gateway";
  public static final String UNMAPPED_GATEWAY_CODE = "GW_ERR_999";

  private static final String DEFAULT_CURRENCY = "INR";
  private static final String DEFAULT_METHOD = "card";
  private static final Set<String> CURRENCIES = Set.of("INR", "USD");
  private static final Set<String> METHODS = Set.of("card", "upi");
  private static final String ID_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  private static final int ID_LENGTH = 14;
  private static final String BASIC_PREFIX = "basic ";

  private final SecureRandom random = new SecureRandom();
  private final Map<String, Map<String, Object>> orders = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Object>> payments = new ConcurrentHashMap<>();
  private final byte[] expectedCredentials;

  public RazorpayEmulatorService(
      @Value("${razorpay.key-id:rzp_test_key_id}") String keyId,
      @Value("${razorpay.key-secret:rzp_test_key_secret}") String keySecret) {
    this.expectedCredentials = (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8);
  }

  /** Creates a Razorpay order from the snake case request body. */
  public Map<String, Object> createOrder(String authorization, Map<String, Object> request) {
    authenticate(authorization);
    Map<String, Object> body = request == null ? Map.of() : request;
    long amount = positiveAmount(body.get("amount"));
    String currency = currency(body.get("currency"));
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", newId(ORDER_PREFIX));
    order.put("entity", "order");
    order.put("amount", amount);
    order.put("amount_paid", 0L);
    order.put("amount_due", amount);
    order.put("currency", currency);
    order.put("receipt", asString(body.get("receipt")));
    order.put("notes", body.get("notes") instanceof Map<?, ?> notes ? notes : Map.of());
    order.put("status", ORDER_STATUS_CREATED);
    order.put("attempts", 0);
    order.put("created_at", Instant.now().getEpochSecond());
    orders.put((String) order.get("id"), order);
    return copy(order);
  }

  /** Returns the stored order or raises PAY_4004. */
  public Map<String, Object> getOrder(String authorization, String razorpayOrderId) {
    authenticate(authorization);
    return copy(findOrder(razorpayOrderId));
  }

  /** Simulates a payment attempt against an existing order. */
  public Map<String, Object> createPayment(String authorization, Map<String, Object> request) {
    authenticate(authorization);
    Map<String, Object> body = request == null ? Map.of() : request;
    String orderId = asString(body.get("order_id"));
    Map<String, Object> order = findOrder(orderId);
    long amount =
        body.get("amount") == null ? (Long) order.get("amount") : positiveAmount(body.get("amount"));
    if (amount != (Long) order.get("amount")) {
      throw CodedErrorFactory.create(MappedError.PAY_4005, amount);
    }
    if (ORDER_STATUS_PAID.equals(order.get("status"))) {
      throw CodedErrorFactory.create(MappedError.PAY_4003, orderId, ORDER_STATUS_PAID);
    }
    String payerUserId = asString(body.get("payer_user_id"));
    String description = asString(body.get("description"));
    String method = body.get("method") == null ? DEFAULT_METHOD : asString(body.get("method"));
    String markers =
        String.join("|", nullToEmpty(payerUserId), nullToEmpty(description), method)
            .toLowerCase(Locale.ROOT);

    if (markers.contains("fraud")) {
      throw CodedErrorFactory.create(MappedError.PAY_4006, payerUserId);
    }
    if (markers.contains("timeout")) {
      throw CodedErrorFactory.create(MappedError.PAY_5002, orderId);
    }
    boolean declined = markers.contains("decline");
    if (!declined) {
      if (markers.contains("invalidcard") || !METHODS.contains(method.toLowerCase(Locale.ROOT))) {
        throw CodedErrorFactory.create(MappedError.PAY_4002);
      }
      if (markers.contains("gatewaydown")) {
        throw CodedErrorFactory.create(MappedError.PAY_5001);
      }
      if (markers.contains("unmapped")) {
        throw CodedErrorFactory.create(MappedError.PAY_5004, UNMAPPED_GATEWAY_CODE);
      }
    }

    Map<String, Object> payment = new LinkedHashMap<>();
    payment.put("id", newId(PAYMENT_PREFIX));
    payment.put("entity", "payment");
    payment.put("amount", amount);
    payment.put("currency", order.get("currency"));
    payment.put("order_id", orderId);
    payment.put("receipt", order.get("receipt"));
    payment.put("method", method);
    payment.put("payer_user_id", payerUserId);
    payment.put("description", description);
    payment.put("created_at", Instant.now().getEpochSecond());
    synchronized (order) {
      order.put("attempts", (Integer) order.get("attempts") + 1);
      if (declined) {
        payment.put("status", PAYMENT_STATUS_FAILED);
        payment.put("captured", false);
        payment.put("error_code", DECLINE_ERROR_CODE);
        payment.put("error_description", DECLINE_ERROR_DESCRIPTION);
        order.put("status", ORDER_STATUS_ATTEMPTED);
      } else {
        payment.put("status", PAYMENT_STATUS_CAPTURED);
        payment.put("captured", true);
        payment.put("error_code", null);
        payment.put("error_description", null);
        order.put("status", ORDER_STATUS_PAID);
        order.put("amount_paid", amount);
        order.put("amount_due", 0L);
      }
    }
    payments.put((String) payment.get("id"), payment);
    return copy(payment);
  }

  /** Returns the stored payment or raises PAY_4004. */
  public Map<String, Object> getPayment(String authorization, String razorpayPaymentId) {
    authenticate(authorization);
    Map<String, Object> payment = razorpayPaymentId == null ? null : payments.get(razorpayPaymentId);
    if (payment == null) {
      throw CodedErrorFactory.create(MappedError.PAY_4004, razorpayPaymentId);
    }
    return copy(payment);
  }

  /** Builds the Basic authorization header value for the given credentials. */
  public static String basicAuthorization(String keyId, String keySecret) {
    String token = keyId + ":" + keySecret;
    return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
  }

  private void authenticate(String authorization) {
    if (authorization == null
        || !authorization.toLowerCase(Locale.ROOT).startsWith(BASIC_PREFIX)) {
      throw CodedErrorFactory.create(MappedError.PAY_4002);
    }
    byte[] supplied;
    try {
      supplied = Base64.getDecoder().decode(authorization.substring(BASIC_PREFIX.length()).trim());
    } catch (IllegalArgumentException ex) {
      throw CodedErrorFactory.create(MappedError.PAY_4002, ex);
    }
    if (!MessageDigest.isEqual(expectedCredentials, supplied)) {
      throw CodedErrorFactory.create(MappedError.PAY_4002);
    }
  }

  private Map<String, Object> findOrder(String razorpayOrderId) {
    Map<String, Object> order = razorpayOrderId == null ? null : orders.get(razorpayOrderId);
    if (order == null) {
      throw CodedErrorFactory.create(MappedError.PAY_4004, razorpayOrderId);
    }
    return order;
  }

  private String newId(String prefix) {
    StringBuilder id = new StringBuilder(prefix);
    for (int i = 0; i < ID_LENGTH; i++) {
      id.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
    }
    return id.toString();
  }

  private static long positiveAmount(Object raw) {
    long amount;
    if (raw instanceof Number number) {
      amount = number.longValue();
      if (number.doubleValue() != amount) {
        throw CodedErrorFactory.create(MappedError.PAY_4005, raw);
      }
    } else if (raw instanceof String text) {
      try {
        amount = Long.parseLong(text.trim());
      } catch (NumberFormatException ex) {
        throw CodedErrorFactory.create(MappedError.PAY_4005, ex, raw);
      }
    } else {
      throw CodedErrorFactory.create(MappedError.PAY_4005, raw);
    }
    if (amount <= 0) {
      throw CodedErrorFactory.create(MappedError.PAY_4005, raw);
    }
    return amount;
  }

  private static String currency(Object raw) {
    if (raw == null) {
      return DEFAULT_CURRENCY;
    }
    String currency = raw.toString().toUpperCase(Locale.ROOT);
    if (!CURRENCIES.contains(currency)) {
      throw CodedErrorFactory.create(MappedError.PAY_4002);
    }
    return currency;
  }

  private static String asString(Object value) {
    return value == null ? null : value.toString();
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static Map<String, Object> copy(Map<String, Object> source) {
    synchronized (source) {
      return new LinkedHashMap<>(source);
    }
  }
}
