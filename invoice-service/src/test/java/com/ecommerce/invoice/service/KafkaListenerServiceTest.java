package com.ecommerce.invoice.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import org.junit.jupiter.api.Test;

class KafkaListenerServiceTest {

  private final InvoiceGeneratorService generator = mock(InvoiceGeneratorService.class);
  private final KafkaListenerService listener = new KafkaListenerService(generator);

  @Test
  void handlePaymentSuccessful_delegatesToGenerator() {
    PaymentResultEvent event = new PaymentResultEvent("ORD-1", "TXN-1", true, null);

    listener.handlePaymentSuccessful(event);

    verify(generator).generateInvoiceForPayment(event);
  }

  @Test
  void handlePaymentSuccessful_catalogError_isLoggedNotRethrown() {
    PaymentResultEvent event = new PaymentResultEvent("ORD-2", "TXN-2", true, null);
    when(generator.generateInvoiceForPayment(event))
        .thenThrow(CodedErrorFactory.create(MappedError.INV_5002));

    assertThatCode(() -> listener.handlePaymentSuccessful(event)).doesNotThrowAnyException();
  }
}
