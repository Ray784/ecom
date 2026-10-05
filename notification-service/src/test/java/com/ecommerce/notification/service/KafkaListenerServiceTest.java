package com.ecommerce.notification.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class KafkaListenerServiceTest {

  private final NotificationSenderService sender = mock(NotificationSenderService.class);
  private final KafkaListenerService listener = new KafkaListenerService(sender);

  @Test
  void handleOrderPlaced_delegatesToSender() {
    OrderPlacedEvent event = new OrderPlacedEvent("ORD-1", "alice", List.of(), BigDecimal.ONE);

    listener.handleOrderPlaced(event);

    verify(sender).notifyOrderPlaced(event);
  }

  @Test
  void handleOrderPlaced_catalogError_isLoggedNotRethrown() {
    OrderPlacedEvent event = new OrderPlacedEvent("ORD-2", "", List.of(), BigDecimal.ONE);
    when(sender.notifyOrderPlaced(event)).thenThrow(CodedErrorFactory.create(MappedError.NOTIF_4001, "EMAIL"));

    assertThatCode(() -> listener.handleOrderPlaced(event)).doesNotThrowAnyException();
  }

  @Test
  void handlePaymentResult_delegatesToSender() {
    PaymentResultEvent event = new PaymentResultEvent("ORD-3", "TXN-3", false, "declined");

    listener.handlePaymentResult(event);

    verify(sender).notifyPaymentResult(event);
  }

  @Test
  void handlePaymentResult_catalogError_isLoggedNotRethrown() {
    PaymentResultEvent event = new PaymentResultEvent("ORD-4", "TXN-4", true, null);
    when(sender.notifyPaymentResult(event)).thenThrow(CodedErrorFactory.create(MappedError.NOTIF_5001));

    assertThatCode(() -> listener.handlePaymentResult(event)).doesNotThrowAnyException();
  }
}
