package com.ecommerce.order.service;

import com.ecommerce.common.dto.InventoryReservedEvent;
import com.ecommerce.common.dto.OrderDetailsDTO;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.common.util.KafkaTopicNames;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Applies inventory and payment outcomes to orders.
 *
 * <p>Kafka payloads are consumed as raw JSON strings and parsed here, so producers in other
 * services do not need to share Java type headers. The handle methods are also invoked by the
 * webhook fallback controller and are idempotent: outcomes that arrive late or twice never move
 * an order backwards.
 */
@Service
public class KafkaListenerService {

  private static final Logger log = LoggerFactory.getLogger(KafkaListenerService.class);

  private final OrderManagerService orderManagerService;
  private final ObjectMapper objectMapper;

  public KafkaListenerService(OrderManagerService orderManagerService, ObjectMapper objectMapper) {
    this.orderManagerService = orderManagerService;
    this.objectMapper = objectMapper;
  }

  /** Kafka entry point for inventory-reserved-topic and inventory-failed-topic. */
  @KafkaListener(
      id = "order-inventory-listener",
      topics = {KafkaTopicNames.INVENTORY_RESERVED_TOPIC, KafkaTopicNames.INVENTORY_FAILED_TOPIC},
      autoStartup = "${order.kafka.listeners.auto-startup:true}")
  public void onInventoryMessage(
      @Payload String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
    InventoryReservedEvent event = parse(payload, InventoryReservedEvent.class, topic);
    if (event == null) {
      return;
    }
    if (KafkaTopicNames.INVENTORY_FAILED_TOPIC.equals(topic)) {
      event.setSuccess(false);
    }
    try {
      handleInventoryUpdate(event);
    } catch (ApplicationBaseException e) {
      log.warn("Ignoring inventory event for order {}: {}", event.getOrderId(), e.getMessage());
    }
  }

  /** Kafka entry point for payment-successful-topic and payment-failed-topic. */
  @KafkaListener(
      id = "order-payment-listener",
      topics = {KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC, KafkaTopicNames.PAYMENT_FAILED_TOPIC},
      autoStartup = "${order.kafka.listeners.auto-startup:true}")
  public void onPaymentMessage(
      @Payload String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
    PaymentResultEvent event = parse(payload, PaymentResultEvent.class, topic);
    if (event == null) {
      return;
    }
    if (KafkaTopicNames.PAYMENT_FAILED_TOPIC.equals(topic)) {
      event.setSuccess(false);
    }
    try {
      handlePaymentResult(event);
    } catch (ApplicationBaseException e) {
      log.warn("Ignoring payment event for order {}: {}", event.getOrderId(), e.getMessage());
    }
  }

  /**
   * Applies an inventory outcome.
   *
   * <p>Failure marks the order FAILED unless it is already terminal. Success marks a PENDING
   * order INVENTORY_RESERVED and leaves orders that already moved on, such as PAID, untouched.
   * Throws ORDER_4002 when the order does not exist.
   */
  public OrderDetailsDTO handleInventoryUpdate(InventoryReservedEvent event) {
    String orderId = event.getOrderId();
    OrderStatus current = orderManagerService.getOrderStatus(orderId);
    if (!event.isSuccess()) {
      if (current.isTerminal()) {
        return skip(orderId, current, "inventory failure");
      }
      return orderManagerService.updateOrderStatus(
          orderId, OrderStatus.FAILED, Order.REASON_INVENTORY_FAILED);
    }
    if (current != OrderStatus.PENDING) {
      return skip(orderId, current, "inventory reservation");
    }
    return orderManagerService.updateOrderStatus(orderId, OrderStatus.INVENTORY_RESERVED);
  }

  /**
   * Applies a payment outcome.
   *
   * <p>Success marks a PENDING or INVENTORY_RESERVED order PAID. Failure marks the order FAILED
   * unless it is already terminal. Throws ORDER_4002 when the order does not exist.
   */
  public OrderDetailsDTO handlePaymentResult(PaymentResultEvent event) {
    String orderId = event.getOrderId();
    OrderStatus current = orderManagerService.getOrderStatus(orderId);
    if (!event.isSuccess()) {
      if (current.isTerminal()) {
        return skip(orderId, current, "payment failure");
      }
      return orderManagerService.updateOrderStatus(
          orderId, OrderStatus.FAILED, Order.REASON_PAYMENT_FAILED);
    }
    if (current != OrderStatus.PENDING && current != OrderStatus.INVENTORY_RESERVED) {
      return skip(orderId, current, "payment success");
    }
    return orderManagerService.updateOrderStatus(orderId, OrderStatus.PAID);
  }

  private OrderDetailsDTO skip(String orderId, OrderStatus current, String outcome) {
    log.info("Order {} is {}; ignoring {}", orderId, current, outcome);
    return orderManagerService.getOrderDetails(orderId);
  }

  private <T> T parse(String payload, Class<T> type, String topic) {
    try {
      return objectMapper.readValue(payload, type);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      log.warn("Dropping unparseable message on {}: {}", topic, e.getMessage());
      return null;
    }
  }
}
