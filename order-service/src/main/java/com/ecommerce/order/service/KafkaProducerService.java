package com.ecommerce.order.service;

import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.util.KafkaTopicNames;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** Publishes order events to Kafka and waits for the broker acknowledgement. */
@Service
public class KafkaProducerService {

  private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final long publishTimeoutMs;

  public KafkaProducerService(
      KafkaTemplate<String, Object> kafkaTemplate,
      @Value("${order.kafka.publish-timeout-ms:5000}") long publishTimeoutMs) {
    this.kafkaTemplate = kafkaTemplate;
    this.publishTimeoutMs = publishTimeoutMs;
  }

  /**
   * Publishes the event to order-placed-topic keyed by order ID.
   *
   * <p>Throws ORDER_5001 when the broker does not acknowledge within the configured timeout.
   */
  public void sendOrderPlaced(OrderPlacedEvent event) {
    String orderId = event.getOrderId();
    try {
      kafkaTemplate
          .send(KafkaTopicNames.ORDER_PLACED_TOPIC, orderId, event)
          .get(publishTimeoutMs, TimeUnit.MILLISECONDS);
      log.info("Published OrderPlaced event for order {}", orderId);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw CodedErrorFactory.create(MappedError.ORDER_5001, e, orderId);
    } catch (ExecutionException | TimeoutException | RuntimeException e) {
      throw CodedErrorFactory.create(MappedError.ORDER_5001, e, orderId);
    }
  }
}
