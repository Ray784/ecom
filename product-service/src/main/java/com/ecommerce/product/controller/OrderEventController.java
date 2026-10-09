package com.ecommerce.product.controller;

import com.ecommerce.common.dto.InventoryReservedEvent;
import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.product.service.KafkaListenerService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal webhook fallback for order-placed and payment result events when Kafka is unavailable.
 *
 * <p>Delegates to the same handlers as the Kafka listeners.
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

  /**
   * Settles the order's reserved stock from a payment result.
   *
   * <p>Returns 202 with applied=false for replays of an already settled order, and 400 when the
   * order id is missing.
   */
  @PostMapping("/payment-result")
  public ResponseEntity<Map<String, Object>> paymentResult(@RequestBody PaymentResultEvent event) {
    if (event.getOrderId() == null || event.getOrderId().isBlank()) {
      return ResponseEntity.badRequest()
          .body(Map.of("applied", false, "message", "Payment result is missing an orderId"));
    }
    boolean applied = kafkaListenerService.handlePaymentResult(event);
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            Map.of(
                "orderId", event.getOrderId(), "success", event.isSuccess(), "applied", applied));
  }
}
