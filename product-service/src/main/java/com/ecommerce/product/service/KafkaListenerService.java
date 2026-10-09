package com.ecommerce.product.service;

import com.ecommerce.common.dto.InventoryReservedEvent;
import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.common.util.KafkaTopicNames;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes order-placed events, reserves stock and publishes the reservation outcome, then settles
 * the reservation when the payment result arrives.
 *
 * <p>The same handlers back the POST /events/order-placed and POST /events/payment-result
 * webhooks. Results are cached per order id so a redelivered or duplicated event never reserves
 * stock twice, and settlement is idempotent per order id inside InventoryManagerService.
 */
@Service
public class KafkaListenerService {

  /** Payment listeners read raw strings so payloads with or without type headers both parse. */
  private static final String STRING_VALUE_DESERIALIZER =
      "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer";

  private static final Logger log = LoggerFactory.getLogger(KafkaListenerService.class);

  private final InventoryManagerService inventoryManagerService;
  private final KafkaProducerService kafkaProducerService;
  private final ObjectMapper objectMapper;
  private final Map<String, InventoryReservedEvent> processedOrders = new ConcurrentHashMap<>();

  public KafkaListenerService(
      InventoryManagerService inventoryManagerService,
      KafkaProducerService kafkaProducerService,
      ObjectMapper objectMapper) {
    this.inventoryManagerService = inventoryManagerService;
    this.kafkaProducerService = kafkaProducerService;
    this.objectMapper = objectMapper;
  }

  /** Kafka entry point. Publishing errors are logged so the record is not redelivered forever. */
  @KafkaListener(
      topics = KafkaTopicNames.ORDER_PLACED_TOPIC,
      groupId = "${spring.kafka.consumer.group-id:product-service}",
      autoStartup = "${product.kafka.listener-auto-startup:true}")
  public void onOrderPlaced(OrderPlacedEvent event) {
    try {
      handleOrderPlaced(event);
    } catch (ApplicationBaseException e) {
      log.error("Could not publish inventory result for order {}: {}",
          event == null ? null : event.getOrderId(), e.getMessage());
    }
  }

  /**
   * Reserves stock for the order and publishes the outcome once.
   *
   * <p>Returns a failure event without publishing when the event has no order id.
   */
  public InventoryReservedEvent handleOrderPlaced(OrderPlacedEvent event) {
    if (event == null || event.getOrderId() == null || event.getOrderId().isBlank()) {
      log.warn("Ignoring order-placed event without orderId: {}", event);
      return new InventoryReservedEvent(null, false, "Order event is missing an orderId");
    }
    AtomicBoolean firstDelivery = new AtomicBoolean(false);
    InventoryReservedEvent result =
        processedOrders.computeIfAbsent(
            event.getOrderId(),
            orderId -> {
              firstDelivery.set(true);
              return inventoryManagerService.reserveStockForOrder(orderId, event.getItems());
            });
    if (!firstDelivery.get()) {
      log.info("Order {} already processed, skipping duplicate event", event.getOrderId());
      return result;
    }
    kafkaProducerService.sendInventoryResult(result);
    return result;
  }

  /** Kafka entry point for payment-successful-topic. A success=false payload releases instead. */
  @KafkaListener(
      topics = KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC,
      groupId = "${spring.kafka.consumer.group-id:product-service}",
      autoStartup = "${product.kafka.listener-auto-startup:true}",
      properties = STRING_VALUE_DESERIALIZER)
  public void onPaymentSuccessful(String payload) {
    PaymentResultEvent event = parsePaymentResult(payload);
    if (event != null) {
      settleLoggingErrors(event);
    }
  }

  /** Kafka entry point for payment-failed-topic. Every record here releases the reservation. */
  @KafkaListener(
      topics = KafkaTopicNames.PAYMENT_FAILED_TOPIC,
      groupId = "${spring.kafka.consumer.group-id:product-service}",
      autoStartup = "${product.kafka.listener-auto-startup:true}",
      properties = STRING_VALUE_DESERIALIZER)
  public void onPaymentFailed(String payload) {
    PaymentResultEvent event = parsePaymentResult(payload);
    if (event != null) {
      event.setSuccess(false);
      settleLoggingErrors(event);
    }
  }

  /**
   * Settles the stock reserved for the order: decrements it on success, releases it on failure.
   *
   * <p>Returns true only when stock changed. Events without an order id and replays of an already
   * settled order return false.
   */
  public boolean handlePaymentResult(PaymentResultEvent event) {
    if (event == null || event.getOrderId() == null || event.getOrderId().isBlank()) {
      log.warn("Ignoring payment result without orderId: {}", event);
      return false;
    }
    return event.isSuccess()
        ? inventoryManagerService.decrementStockForOrder(event.getOrderId())
        : inventoryManagerService.releaseReservedStockForOrder(event.getOrderId());
  }

  /** Catalog errors are logged because redelivering the same record would fail the same way. */
  private void settleLoggingErrors(PaymentResultEvent event) {
    try {
      handlePaymentResult(event);
    } catch (ApplicationBaseException e) {
      log.error("Could not settle stock for order {}: {} {}",
          event.getOrderId(), e.getErrorCode(), e.getMessage());
    }
  }

  /**
   * Parses a payment result from a JSON object or from a JSON string that wraps one.
   *
   * <p>Returns null for blank or malformed payloads so a poison record is skipped.
   */
  private PaymentResultEvent parsePaymentResult(String payload) {
    if (payload == null || payload.isBlank()) {
      log.warn("Ignoring empty payment result payload");
      return null;
    }
    try {
      JsonNode node = objectMapper.readTree(payload);
      if (node.isTextual()) {
        node = objectMapper.readTree(node.asText());
      }
      return objectMapper.treeToValue(node, PaymentResultEvent.class);
    } catch (JsonProcessingException e) {
      log.warn("Ignoring malformed payment result payload: {}", e.getOriginalMessage());
      return null;
    }
  }
}
