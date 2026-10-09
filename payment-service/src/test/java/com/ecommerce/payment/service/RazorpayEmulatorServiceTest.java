package com.ecommerce.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RazorpayEmulatorServiceTest {

  private static final String AUTH =
      RazorpayEmulatorService.basicAuthorization("rzp_test_key_id", "rzp_test_key_secret");

  private final RazorpayEmulatorService emulator =
      new RazorpayEmulatorService("rzp_test_key_id", "rzp_test_key_secret");

  private Map<String, Object> order(long amount) {
    return emulator.createOrder(AUTH, Map.of("amount", amount, "receipt", "o-1"));
  }

  private static Map<String, Object> paymentRequest(String orderId, String userId) {
    Map<String, Object> request = new HashMap<>();
    request.put("order_id", orderId);
    request.put("amount", 5000);
    request.put("currency", "INR");
    request.put("method", "card");
    request.put("payer_user_id", userId);
    return request;
  }

  @Test
  void createOrder_validRequest_storesCreatedOrderWithRazorpayShape() {
    long before = Instant.now().getEpochSecond();
    Map<String, Object> order =
        emulator.createOrder(
            AUTH,
            Map.of("amount", 5000, "currency", "usd", "receipt", "o-1", "notes", Map.of("k", "v")));

    assertThat((String) order.get("id")).matches("order_[A-Za-z0-9]{14}");
    assertThat(order)
        .containsEntry("entity", "order")
        .containsEntry("amount", 5000L)
        .containsEntry("amount_paid", 0L)
        .containsEntry("amount_due", 5000L)
        .containsEntry("currency", "USD")
        .containsEntry("receipt", "o-1")
        .containsEntry("notes", Map.of("k", "v"))
        .containsEntry("status", "created")
        .containsEntry("attempts", 0);
    assertThat((Long) order.get("created_at")).isGreaterThanOrEqualTo(before);
    assertThat(emulator.getOrder(AUTH, (String) order.get("id"))).isEqualTo(order);
  }

  @Test
  void createOrder_withoutCurrencyOrNotes_defaultsToInrAndEmptyNotes() {
    Map<String, Object> order = emulator.createOrder(AUTH, Map.of("amount", "250"));

    assertThat(order)
        .containsEntry("currency", "INR")
        .containsEntry("amount", 250L)
        .containsEntry("notes", Map.of());
  }

  @Test
  void createOrder_unsupportedCurrency_throwsPay4002() {
    assertThatThrownBy(() -> emulator.createOrder(AUTH, Map.of("amount", 1, "currency", "EUR")))
        .extracting("errorCode")
        .isEqualTo("PAY_4002");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-5", "12.5", "abc"})
  void createOrder_invalidAmount_throwsPay4005(String amount) {
    Object raw = amount.equals("12.5") ? (Object) 12.5d : amount;
    assertThatThrownBy(() -> emulator.createOrder(AUTH, Map.of("amount", raw)))
        .extracting("errorCode")
        .isEqualTo("PAY_4005");
  }

  @Test
  void createOrder_nullBody_throwsPay4005ForMissingAmount() {
    assertThatThrownBy(() -> emulator.createOrder(AUTH, null))
        .extracting("errorCode")
        .isEqualTo("PAY_4005");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "Bearer abc",
        "Basic !!!not-base64!!!",
        "Basic cnpwX3Rlc3Rfa2V5X2lkOndyb25n",
      })
  void anyCall_missingOrInvalidBasicAuth_throwsPay4002(String authorization) {
    assertThatThrownBy(() -> emulator.createOrder(authorization, Map.of("amount", 100)))
        .extracting("errorCode")
        .isEqualTo("PAY_4002");
    assertThatThrownBy(() -> emulator.createPayment(authorization, Map.of()))
        .extracting("errorCode")
        .isEqualTo("PAY_4002");
    assertThatThrownBy(() -> emulator.getOrder(authorization, "order_x"))
        .extracting("errorCode")
        .isEqualTo("PAY_4002");
    assertThatThrownBy(() -> emulator.getPayment(authorization, "pay_x"))
        .extracting("errorCode")
        .isEqualTo("PAY_4002");
  }

  @Test
  void createPayment_normalPayer_capturesAndMarksOrderPaid() {
    String orderId = (String) order(5000).get("id");

    Map<String, Object> payment = emulator.createPayment(AUTH, paymentRequest(orderId, "alice"));

    assertThat((String) payment.get("id")).matches("pay_[A-Za-z0-9]{14}");
    assertThat(payment)
        .containsEntry("entity", "payment")
        .containsEntry("order_id", orderId)
        .containsEntry("amount", 5000L)
        .containsEntry("status", "captured")
        .containsEntry("captured", true)
        .containsEntry("error_code", null);
    assertThat(emulator.getOrder(AUTH, orderId))
        .containsEntry("status", "paid")
        .containsEntry("amount_paid", 5000L)
        .containsEntry("amount_due", 0L)
        .containsEntry("attempts", 1);
    assertThat(emulator.getPayment(AUTH, (String) payment.get("id"))).isEqualTo(payment);
  }

  @Test
  void createPayment_withoutAmountOrMethod_usesOrderAmountAndCard() {
    String orderId = (String) order(700).get("id");

    Map<String, Object> payment = emulator.createPayment(AUTH, Map.of("order_id", orderId));

    assertThat(payment).containsEntry("amount", 700L).containsEntry("method", "card");
  }

  @Test
  void createPayment_declineMarker_storesFailedPaymentAndLeavesOrderUnpaid() {
    String orderId = (String) order(5000).get("id");

    Map<String, Object> payment =
        emulator.createPayment(AUTH, paymentRequest(orderId, "decline-user"));

    assertThat((String) payment.get("id")).startsWith("pay_");
    assertThat(payment)
        .containsEntry("status", "failed")
        .containsEntry("captured", false)
        .containsEntry("error_code", "BAD_REQUEST_ERROR")
        .containsEntry("error_description", "Card declined by gateway");
    assertThat(emulator.getOrder(AUTH, orderId))
        .containsEntry("status", "attempted")
        .containsEntry("amount_paid", 0L)
        .containsEntry("amount_due", 5000L);
  }

  @Test
  void createPayment_markerInDescription_isHonoured() {
    String orderId = (String) order(5000).get("id");
    Map<String, Object> request = paymentRequest(orderId, "alice");
    request.put("description", "please gatewaydown now");

    assertThatThrownBy(() -> emulator.createPayment(AUTH, request))
        .extracting("errorCode")
        .isEqualTo("PAY_5001");
  }

  @ParameterizedTest
  @CsvSource({
    "fraud-user, PAY_4006",
    "timeout-user, PAY_5002",
    "invalidcard-user, PAY_4002",
    "gatewaydown-user, PAY_5001",
    "unmapped-user, PAY_5004"
  })
  void createPayment_errorMarkers_throwCatalogCodesWithoutStoringPayment(
      String userId, String code) {
    String orderId = (String) order(5000).get("id");

    assertThatThrownBy(() -> emulator.createPayment(AUTH, paymentRequest(orderId, userId)))
        .extracting("errorCode")
        .isEqualTo(code);
    assertThat(emulator.getOrder(AUTH, orderId)).containsEntry("status", "created");
  }

  @Test
  void createPayment_unsupportedMethod_throwsPay4002() {
    String orderId = (String) order(5000).get("id");
    Map<String, Object> request = paymentRequest(orderId, "alice");
    request.put("method", "netbanking");

    assertThatThrownBy(() -> emulator.createPayment(AUTH, request))
        .extracting("errorCode")
        .isEqualTo("PAY_4002");
  }

  @Test
  void createPayment_amountDifferentFromOrder_throwsPay4005() {
    String orderId = (String) order(4000).get("id");

    assertThatThrownBy(() -> emulator.createPayment(AUTH, paymentRequest(orderId, "alice")))
        .extracting("errorCode")
        .isEqualTo("PAY_4005");
  }

  @Test
  void createPayment_orderAlreadyPaid_throwsPay4003() {
    String orderId = (String) order(5000).get("id");
    emulator.createPayment(AUTH, paymentRequest(orderId, "alice"));

    assertThatThrownBy(() -> emulator.createPayment(AUTH, paymentRequest(orderId, "alice")))
        .extracting("errorCode")
        .isEqualTo("PAY_4003");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"order_unknown"})
  void createPayment_unknownOrder_throwsPay4004(String orderId) {
    assertThatThrownBy(() -> emulator.createPayment(AUTH, paymentRequest(orderId, "alice")))
        .extracting("errorCode")
        .isEqualTo("PAY_4004");
  }

  @Test
  void getOrderAndGetPayment_unknownIds_throwPay4004() {
    assertThatThrownBy(() -> emulator.getOrder(AUTH, "order_missing"))
        .extracting("errorCode")
        .isEqualTo("PAY_4004");
    assertThatThrownBy(() -> emulator.getPayment(AUTH, "pay_missing"))
        .extracting("errorCode")
        .isEqualTo("PAY_4004");
    assertThatThrownBy(() -> emulator.getPayment(AUTH, null))
        .extracting("errorCode")
        .isEqualTo("PAY_4004");
  }
}
