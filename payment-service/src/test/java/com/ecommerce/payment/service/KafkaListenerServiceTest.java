package com.ecommerce.payment.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class KafkaListenerServiceTest {

  private final PaymentProcessorService processor = mock(PaymentProcessorService.class);
  private final KafkaListenerService listener = new KafkaListenerService(processor);

  @Test
  void handleOrderPlaced_delegatesToProcessor() {
    OrderPlacedEvent event = new OrderPlacedEvent("o-1", "alice", List.of(), BigDecimal.TEN);

    listener.handleOrderPlaced(event);

    verify(processor).initiatePayment(event);
  }

  @Test
  void handleOrderPlaced_catalogError_isLoggedNotRethrown() {
    OrderPlacedEvent event = new OrderPlacedEvent("o-2", "decline", List.of(), BigDecimal.TEN);
    when(processor.initiatePayment(event))
        .thenThrow(CodedErrorFactory.create(MappedError.PAY_4001, "o-2"));

    assertThatCode(() -> listener.handleOrderPlaced(event)).doesNotThrowAnyException();
  }
}
