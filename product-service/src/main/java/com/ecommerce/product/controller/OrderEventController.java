package com.ecommerce.product.controller;

import com.ecommerce.common.dto.InventoryReservedEvent;
import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.product.service.KafkaListenerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal webhook fallback for order-placed events when Kafka is unavailable.
 *
 * <p>Delegates to the same handler as the Kafka listener and returns the reservation outcome.
 */
@RestController
@RequestMapping("/events")
public class OrderEventController {

  private final KafkaListenerService kafkaListenerService;

  public OrderEventController(KafkaListenerService kafkaListenerService) {
    this.kafkaListenerService = kafkaListenerService;
  }

  @PostMapping("/order-placed")
  public ResponseEntity<InventoryReservedEvent> orderPlaced(@RequestBody OrderPlacedEvent event) {
    InventoryReservedEvent result = kafkaListenerService.handleOrderPlaced(event);
    HttpStatus status = result.getOrderId() == null ? HttpStatus.BAD_REQUEST : HttpStatus.ACCEPTED;
    return ResponseEntity.status(status).body(result);
  }
}
