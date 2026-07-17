package com.ecommerce.invoice.service;

import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.common.util.KafkaTopicNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/** Consumes successful payment events and generates the matching invoice. */
@Service
public class KafkaListenerService {

  private static final Logger log = LoggerFactory.getLogger(KafkaListenerService.class);

  private final InvoiceGeneratorService invoiceGeneratorService;

  public KafkaListenerService(InvoiceGeneratorService invoiceGeneratorService) {
    this.invoiceGeneratorService = invoiceGeneratorService;
  }

  /**
   * Generates the invoice for the paid order.
   *
   * <p>Catalog errors are logged instead of rethrown because redelivering the same record would
   * fail the same way.
   */
  @KafkaListener(
      topics = KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC,
      groupId = "${invoice.kafka.group-id:invoice-service}")
  public void handlePaymentSuccessful(PaymentResultEvent event) {
    log.info("Received PaymentResult event {}", event);
    try {
      invoiceGeneratorService.generateInvoiceForPayment(event);
    } catch (ApplicationBaseException ex) {
      log.warn("Invoice for order {} not generated, {}: {}",
          event == null ? null : event.getOrderId(), ex.getErrorCode(), ex.getMessage());
    }
  }
}
