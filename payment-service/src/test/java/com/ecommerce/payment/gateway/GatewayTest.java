package com.ecommerce.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.common.error.MappedError;
import com.ecommerce.payment.gateway.SimulatedPaymentGateway.GatewayCommunicationException;
import com.ecommerce.payment.service.RazorpayEmulatorService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GatewayTest {

  private final RazorpayEmulatorService emulator =
      new RazorpayEmulatorService("rzp_test_key_id", "rzp_test_key_secret");
  private final SimulatedPaymentGateway gateway =
      new SimulatedPaymentGateway(
          new RazorpayClient(emulator, "rzp_test_key_id", "rzp_test_key_secret"));
  private final GatewayErrorMapper mapper = new GatewayErrorMapper();
  private final String auth =
      RazorpayEmulatorService.basicAuthorization("rzp_test_key_id", "rzp_test_key_secret");

  @ParameterizedTest
  @CsvSource(
      value = {"alice, true, APPROVED", "NULL, true, APPROVED", "DECLINE-me, false, CARD_DECLINED"},
      nullValues = "NULL")
  void charge_paymentCreatingMarkers_returnRazorpayPaymentRef(
      String userId, boolean approved, String code) {
    PaymentGatewayResponse response = gateway.charge("TXN-1", "o-1", userId, BigDecimal.TEN);

    assertThat(response.approved()).isEqualTo(approved);
    assertThat(response.gatewayCode()).isEqualTo(code);
    assertThat(response.gatewayRef()).matches("pay_[A-Za-z0-9]{14}");
    assertThat(response.razorpayOrderId()).matches("order_[A-Za-z0-9]{14}");
    assertThat(emulator.getPayment(auth, response.gatewayRef()))
        .containsEntry("order_id", response.razorpayOrderId())
        .containsEntry("amount", 1000L);
    assertThat(emulator.getOrder(auth, response.razorpayOrderId()))
        .containsEntry("receipt", "o-1")
        .containsEntry("status", approved ? "paid" : "attempted");
  }

  @ParameterizedTest
  @CsvSource({
    "invalidcard-1, INVALID_PAYMENT_METHOD",
    "gatewaydown-1, GATEWAY_UNAVAILABLE",
    "unmapped-1, GW_ERR_999",
    "fraud-1, FRAUD_SUSPECTED"
  })
  void charge_rejectingMarkers_returnGatewayCodeWithoutPayment(String userId, String code) {
    PaymentGatewayResponse response = gateway.charge("TXN-1", "o-1", userId, BigDecimal.TEN);

    assertThat(response.approved()).isFalse();
    assertThat(response.gatewayCode()).isEqualTo(code);
    assertThat(response.gatewayRef()).isNull();
    assertThat(response.razorpayOrderId()).startsWith("order_");
  }

  @Test
  void charge_fractionalAmount_isSentInPaise() {
    PaymentGatewayResponse response =
        gateway.charge("TXN-2", "o-2", "alice", new BigDecimal("49.99"));

    assertThat(emulator.getOrder(auth, response.razorpayOrderId())).containsEntry("amount", 4999L);
  }

  @Test
  void charge_timeoutMarker_throwsCommunicationException() {
    assertThatThrownBy(() -> gateway.charge("TXN-9", "o-9", "timeout-user", BigDecimal.TEN))
        .isInstanceOf(GatewayCommunicationException.class)
        .hasMessageContaining("TXN-9");
  }

  @ParameterizedTest
  @CsvSource({
    "CARD_DECLINED, PAY_4001",
    "INVALID_PAYMENT_METHOD, PAY_4002",
    "FRAUD_SUSPECTED, PAY_4006",
    "GATEWAY_UNAVAILABLE, PAY_5001",
    "GATEWAY_TIMEOUT, PAY_5002",
    "GW_ERR_999, PAY_5004"
  })
  void toMappedError_knownAndUnknownCodes(String gatewayCode, MappedError expected) {
    assertThat(mapper.toMappedError(gatewayCode)).isEqualTo(expected);
  }

  @Test
  void toMappedError_nullCode_isPay5004() {
    assertThat(mapper.toMappedError(null)).isEqualTo(MappedError.PAY_5004);
  }
}
