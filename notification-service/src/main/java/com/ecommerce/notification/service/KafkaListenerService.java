package com.ecommerce.notification.service;

import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.common.util.KafkaTopicNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes order and payment events and notifies the customer.
 *
 * <p>Catalog errors are logged instead of rethrown because the failed attempt is already recorded
 * in the notification log and redelivery would fail the same way.
 */
@Service
public class KafkaListenerService {

  private static final Logger log = LoggerFactory.getLogger(KafkaListenerService.class);

  private final NotificationSenderService senderService;

  public KafkaListenerService(NotificationSenderService senderService) {
    this.senderService = senderService;
  }

  @KafkaListener(
      topics = KafkaTopicNames.ORDER_PLACED_TOPIC,
      groupId = "${notification.kafka.group-id:notification-service}")
  public void handleOrderPlaced(OrderPlacedEvent event) {
    log.info("Received OrderPlaced event {}", event);
    try {
      senderService.notifyOrderPlaced(event);
    } catch (ApplicationBaseException ex) {
      log.warn("Order placed notification failed with {}: {}", ex.getErrorCode(), ex.getMessage());
    }
  }

  @KafkaListener(
      topics = {KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC, KafkaTopicNames.PAYMENT_FAILED_TOPIC},
      groupId = "${notification.kafka.group-id:notification-service}")
  public void handlePaymentResult(PaymentResultEvent event) {
    log.info("Received PaymentResult event {}", event);
    try {
      senderService.notifyPaymentResult(event);
    } catch (ApplicationBaseException ex) {
      log.warn("Payment notification failed with {}: {}", ex.getErrorCode(), ex.getMessage());
    }
  }
}
