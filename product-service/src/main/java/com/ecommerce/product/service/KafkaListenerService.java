package com.ecommerce.product.service;

import com.ecommerce.common.dto.InventoryReservedEvent;
import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.common.util.KafkaTopicNames;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes order-placed events, reserves stock and publishes the reservation outcome.
 *
 * <p>The same handler backs the POST /events/order-placed webhook. Results are cached per order
 * id so a redelivered or duplicated event never reserves stock twice.
 */
@Service
public class KafkaListenerService {

  private static final Logger log = LoggerFactory.getLogger(KafkaListenerService.class);

  private final InventoryManagerService inventoryManagerService;
  private final KafkaProducerService kafkaProducerService;
  private final Map<String, InventoryReservedEvent> processedOrders = new ConcurrentHashMap<>();

  public KafkaListenerService(
      InventoryManagerService inventoryManagerService, KafkaProducerService kafkaProducerService) {
    this.inventoryManagerService = inventoryManagerService;
    this.kafkaProducerService = kafkaProducerService;
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
}
