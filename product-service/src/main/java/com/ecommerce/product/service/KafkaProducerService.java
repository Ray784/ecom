package com.ecommerce.product.service;

import com.ecommerce.common.dto.InventoryReservedEvent;
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

/**
 * Publishes inventory reservation outcomes.
 *
 * <p>Successful reservations go to inventory-reserved-topic and failures go to
 * inventory-failed-topic. When an order-service webhook is configured the event is also posted
 * there. PROD_5002 is raised only when neither channel accepted the event.
 */
@Service
public class KafkaProducerService {

  private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final OrderWebhookClient orderWebhookClient;
  private final long sendTimeoutMs;

  public KafkaProducerService(
      KafkaTemplate<String, Object> kafkaTemplate,
      OrderWebhookClient orderWebhookClient,
      @Value("${product.kafka.send-timeout-ms:5000}") long sendTimeoutMs) {
    this.kafkaTemplate = kafkaTemplate;
    this.orderWebhookClient = orderWebhookClient;
    this.sendTimeoutMs = sendTimeoutMs;
  }

  /** Returns the topic that an event with the given outcome is published to. */
  public static String topicFor(InventoryReservedEvent event) {
    return event.isSuccess()
        ? KafkaTopicNames.INVENTORY_RESERVED_TOPIC
        : KafkaTopicNames.INVENTORY_FAILED_TOPIC;
  }

  /** Publishes the event and returns the Kafka topic it was routed to. */
  public String sendInventoryResult(InventoryReservedEvent event) {
    String topic = topicFor(event);
    boolean published = publishToKafka(topic, event);
    boolean notified = orderWebhookClient.notifyInventoryResult(event);
    if (!published && !notified) {
      throw CodedErrorFactory.create(MappedError.PROD_5002, event.getOrderId());
    }
    return topic;
  }

  private boolean publishToKafka(String topic, InventoryReservedEvent event) {
    try {
      kafkaTemplate.send(topic, event.getOrderId(), event).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
      log.info("Published {} for order {}", topic, event.getOrderId());
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while publishing {} for order {}", topic, event.getOrderId());
      return false;
    } catch (ExecutionException | TimeoutException | RuntimeException e) {
      log.warn("Kafka publish to {} failed for order {}: {}", topic, event.getOrderId(), e.getMessage());
      return false;
    }
  }
}
