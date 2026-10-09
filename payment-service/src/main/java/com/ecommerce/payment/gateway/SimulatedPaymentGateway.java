package com.ecommerce.payment.gateway;

import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.payment.service.RazorpayEmulatorService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Card gateway adapter backed by the fake Razorpay emulator so the platform runs without third
 * party dependencies.
 *
 * <p>Each charge creates a Razorpay order for the amount in paise with the platform order ID as
 * receipt and then attempts a payment on it. The outcome is chosen by the emulator from markers in
 * the user ID, which lets tests and the live E2E script exercise every catalog path:
 *
 * <ul>
 *   <li>decline yields CARD_DECLINED with a failed pay_ payment
 *   <li>invalidcard yields INVALID_PAYMENT_METHOD
 *   <li>gatewaydown yields GATEWAY_UNAVAILABLE
 *   <li>unmapped yields an unknown gateway code
 *   <li>timeout makes the call itself fail with GatewayCommunicationException
 *   <li>anything else is captured with a pay_ payment
 * </ul>
 */
@Component
public class SimulatedPaymentGateway {

  public static final String CODE_APPROVED = "APPROVED";
  public static final String CODE_CARD_DECLINED = "CARD_DECLINED";
  public static final String CODE_INVALID_PAYMENT_METHOD = "INVALID_PAYMENT_METHOD";
  public static final String CODE_GATEWAY_UNAVAILABLE = "GATEWAY_UNAVAILABLE";
  public static final String CODE_FRAUD_SUSPECTED = "FRAUD_SUSPECTED";
  public static final String CODE_UNMAPPED = "GW_ERR_999";

  static final String CURRENCY = "INR";
  static final String METHOD = "card";
  static final String DESCRIPTION = "E-commerce order payment";

  private static final Map<String, String> EMULATOR_ERROR_TO_GATEWAY_CODE =
      Map.of(
          "PAY_4002", CODE_INVALID_PAYMENT_METHOD,
          "PAY_4006", CODE_FRAUD_SUSPECTED,
          "PAY_5001", CODE_GATEWAY_UNAVAILABLE,
          "PAY_5004", CODE_UNMAPPED);

  private final RazorpayClient razorpayClient;

  public SimulatedPaymentGateway(RazorpayClient razorpayClient) {
    this.razorpayClient = razorpayClient;
  }

  /** Charges the given amount through Razorpay and returns the gateway verdict. */
  public PaymentGatewayResponse charge(
      String transactionId, String orderId, String userId, BigDecimal amount) {
    long amountPaise = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    String razorpayOrderId = null;
    try {
      Map<String, Object> order =
          razorpayClient.createOrder(
              amountPaise, CURRENCY, orderId, Map.of("transactionId", transactionId));
      razorpayOrderId = (String) order.get("id");
      Map<String, Object> payment =
          razorpayClient.createPayment(
              razorpayOrderId, amountPaise, CURRENCY, METHOD, userId, DESCRIPTION);
      String paymentId = (String) payment.get("id");
      if (RazorpayEmulatorService.PAYMENT_STATUS_CAPTURED.equals(payment.get("status"))) {
        return new PaymentGatewayResponse(true, CODE_APPROVED, paymentId, razorpayOrderId);
      }
      return new PaymentGatewayResponse(false, CODE_CARD_DECLINED, paymentId, razorpayOrderId);
    } catch (ApplicationBaseException ex) {
      if ("PAY_5002".equals(ex.getErrorCode())) {
        throw new GatewayCommunicationException(
            "Gateway did not answer in time for transaction " + transactionId);
      }
      String code = EMULATOR_ERROR_TO_GATEWAY_CODE.getOrDefault(ex.getErrorCode(), ex.getErrorCode());
      return new PaymentGatewayResponse(false, code, null, razorpayOrderId);
    }
  }

  /** Raised when the gateway cannot be reached or does not respond. */
  public static class GatewayCommunicationException extends RuntimeException {
    public GatewayCommunicationException(String message) {
      super(message);
    }
  }
}
