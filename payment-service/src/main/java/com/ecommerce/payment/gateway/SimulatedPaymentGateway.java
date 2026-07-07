package com.ecommerce.payment.gateway;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic stand in for an external card gateway so the platform runs without third party
 * dependencies.
 *
 * <p>The outcome is chosen from markers in the user ID, which lets tests and the live E2E script
 * exercise every catalog path:
 *
 * <ul>
 *   <li>decline yields CARD_DECLINED
 *   <li>invalidcard yields INVALID_PAYMENT_METHOD
 *   <li>gatewaydown yields GATEWAY_UNAVAILABLE
 *   <li>unmapped yields an unknown gateway code
 *   <li>timeout makes the call itself fail with GatewayCommunicationException
 *   <li>anything else is approved
 * </ul>
 */
@Component
public class SimulatedPaymentGateway {

  public static final String CODE_APPROVED = "APPROVED";
  public static final String CODE_CARD_DECLINED = "CARD_DECLINED";
  public static final String CODE_INVALID_PAYMENT_METHOD = "INVALID_PAYMENT_METHOD";
  public static final String CODE_GATEWAY_UNAVAILABLE = "GATEWAY_UNAVAILABLE";
  public static final String CODE_UNMAPPED = "GW_ERR_999";

  /** Charges the given amount and returns the gateway verdict. */
  public PaymentGatewayResponse charge(String transactionId, String userId, BigDecimal amount) {
    String marker = userId == null ? "" : userId.toLowerCase(Locale.ROOT);
    if (marker.contains("timeout")) {
      throw new GatewayCommunicationException(
          "Gateway did not answer in time for transaction " + transactionId);
    }
    String gatewayRef = "GW-" + UUID.randomUUID();
    if (marker.contains("decline")) {
      return new PaymentGatewayResponse(false, CODE_CARD_DECLINED, gatewayRef);
    }
    if (marker.contains("invalidcard")) {
      return new PaymentGatewayResponse(false, CODE_INVALID_PAYMENT_METHOD, gatewayRef);
    }
    if (marker.contains("gatewaydown")) {
      return new PaymentGatewayResponse(false, CODE_GATEWAY_UNAVAILABLE, gatewayRef);
    }
    if (marker.contains("unmapped")) {
      return new PaymentGatewayResponse(false, CODE_UNMAPPED, gatewayRef);
    }
    return new PaymentGatewayResponse(true, CODE_APPROVED, gatewayRef);
  }

  /** Raised when the gateway cannot be reached or does not respond. */
  public static class GatewayCommunicationException extends RuntimeException {
    public GatewayCommunicationException(String message) {
      super(message);
    }
  }
}
