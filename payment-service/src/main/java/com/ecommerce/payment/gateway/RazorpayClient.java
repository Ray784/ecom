package com.ecommerce.payment.gateway;

import com.ecommerce.payment.service.RazorpayEmulatorService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Razorpay API client that authenticates with the configured key pair.
 *
 * <p>Calls go in process to the emulator so no network hop or live port is needed, while still
 * exercising the same Basic credential check that external callers of the /v1 endpoints face.
 */
@Component
public class RazorpayClient {

  private final RazorpayEmulatorService emulator;
  private final String authorization;

  public RazorpayClient(
      RazorpayEmulatorService emulator,
      @Value("${razorpay.key-id:rzp_test_key_id}") String keyId,
      @Value("${razorpay.key-secret:rzp_test_key_secret}") String keySecret) {
    this.emulator = emulator;
    this.authorization = RazorpayEmulatorService.basicAuthorization(keyId, keySecret);
  }

  /** Creates a Razorpay order for the amount in paise and returns the order entity. */
  public Map<String, Object> createOrder(
      long amountPaise, String currency, String receipt, Map<String, Object> notes) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("amount", amountPaise);
    request.put("currency", currency);
    request.put("receipt", receipt);
    request.put("notes", notes == null ? Map.of() : notes);
    return emulator.createOrder(authorization, request);
  }

  /** Attempts a payment against the Razorpay order and returns the payment entity. */
  public Map<String, Object> createPayment(
      String razorpayOrderId,
      long amountPaise,
      String currency,
      String method,
      String payerUserId,
      String description) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("order_id", razorpayOrderId);
    request.put("amount", amountPaise);
    request.put("currency", currency);
    request.put("method", method);
    request.put("payer_user_id", payerUserId);
    request.put("description", description);
    return emulator.createPayment(authorization, request);
  }
}
