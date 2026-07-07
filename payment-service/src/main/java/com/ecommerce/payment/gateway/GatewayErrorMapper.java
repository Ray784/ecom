package com.ecommerce.payment.gateway;

import com.ecommerce.common.error.MappedError;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Translates gateway result codes into internal catalog errors. */
@Component
public class GatewayErrorMapper {

  private static final Map<String, MappedError> KNOWN_CODES =
      Map.of(
          SimulatedPaymentGateway.CODE_CARD_DECLINED, MappedError.PAY_4001,
          SimulatedPaymentGateway.CODE_INVALID_PAYMENT_METHOD, MappedError.PAY_4002,
          "FRAUD_SUSPECTED", MappedError.PAY_4006,
          SimulatedPaymentGateway.CODE_GATEWAY_UNAVAILABLE, MappedError.PAY_5001,
          "GATEWAY_TIMEOUT", MappedError.PAY_5002);

  /** Returns the catalog entry for the code, or PAY_5004 when the code is unknown. */
  public MappedError toMappedError(String gatewayCode) {
    if (gatewayCode == null) {
      return MappedError.PAY_5004;
    }
    return KNOWN_CODES.getOrDefault(gatewayCode, MappedError.PAY_5004);
  }
}
