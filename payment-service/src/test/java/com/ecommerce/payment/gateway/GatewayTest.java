package com.ecommerce.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.common.error.MappedError;
import com.ecommerce.payment.gateway.SimulatedPaymentGateway.GatewayCommunicationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GatewayTest {

  private final SimulatedPaymentGateway gateway = new SimulatedPaymentGateway();
  private final GatewayErrorMapper mapper = new GatewayErrorMapper();

  @ParameterizedTest
  @CsvSource(
      value = {
        "alice, true, APPROVED",
        "NULL, true, APPROVED",
        "DECLINE-me, false, CARD_DECLINED",
        "invalidcard-1, false, INVALID_PAYMENT_METHOD",
        "gatewaydown-1, false, GATEWAY_UNAVAILABLE",
        "unmapped-1, false, GW_ERR_999"
      },
      nullValues = "NULL")
  void charge_userMarkers_selectOutcome(String userId, boolean approved, String code) {
    PaymentGatewayResponse response = gateway.charge("TXN-1", userId, BigDecimal.TEN);

    assertThat(response.approved()).isEqualTo(approved);
    assertThat(response.gatewayCode()).isEqualTo(code);
    assertThat(response.gatewayRef()).startsWith("GW-");
  }

  @Test
  void charge_timeoutMarker_throwsCommunicationException() {
    assertThatThrownBy(() -> gateway.charge("TXN-9", "timeout-user", BigDecimal.TEN))
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
