package com.ecommerce.order.controller;

import com.ecommerce.common.dto.InventoryReservedEvent;
import com.ecommerce.common.dto.OrderDetailsDTO;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.order.service.KafkaListenerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal webhook fallback that applies saga outcomes over HTTP when Kafka is unavailable.
 *
 * <p>Unlike the Kafka path, an unknown order surfaces as ORDER_4002 to the caller.
 */
@RestController
@RequestMapping("/events")
public class EventWebhookController {

  private final KafkaListenerService kafkaListenerService;

  public EventWebhookController(KafkaListenerService kafkaListenerService) {
    this.kafkaListenerService = kafkaListenerService;
  }

  @PostMapping("/inventory-result")
  public OrderDetailsDTO inventoryResult(@RequestBody InventoryReservedEvent event) {
    return kafkaListenerService.handleInventoryUpdate(event);
  }

  @PostMapping("/payment-result")
  public OrderDetailsDTO paymentResult(@RequestBody PaymentResultEvent event) {
    return kafkaListenerService.handlePaymentResult(event);
  }
}
