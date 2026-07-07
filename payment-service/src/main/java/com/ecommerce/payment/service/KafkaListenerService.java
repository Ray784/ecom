package com.ecommerce.payment.service;

import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.common.util.KafkaTopicNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/** Consumes OrderPlaced events and starts the payment for each order. */
@Service
public class KafkaListenerService {

  private static final Logger log = LoggerFactory.getLogger(KafkaListenerService.class);

  private final PaymentProcessorService paymentProcessorService;

  public KafkaListenerService(PaymentProcessorService paymentProcessorService) {
    this.paymentProcessorService = paymentProcessorService;
  }

  /**
   * Starts the payment for the placed order.
   *
   * <p>Catalog errors are business outcomes that were already recorded and published, so they are
   * logged instead of rethrown to avoid pointless redelivery of the record.
   */
  @KafkaListener(
      topics = KafkaTopicNames.ORDER_PLACED_TOPIC,
      groupId = "${payment.kafka.group-id:payment-service}")
  public void handleOrderPlaced(OrderPlacedEvent event) {
    log.info("Received OrderPlaced event {}", event);
    try {
      paymentProcessorService.initiatePayment(event);
    } catch (ApplicationBaseException ex) {
      log.warn("Payment for order {} ended with {}: {}",
          event == null ? null : event.getOrderId(), ex.getErrorCode(), ex.getMessage());
    }
  }
}
