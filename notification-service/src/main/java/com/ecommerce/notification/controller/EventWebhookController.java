package com.ecommerce.notification.controller;

import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.notification.model.NotificationLog;
import com.ecommerce.notification.service.NotificationSenderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST fallback for event delivery when Kafka is not available. */
@RestController
@RequestMapping("/events")
public class EventWebhookController {

  private final NotificationSenderService senderService;

  public EventWebhookController(NotificationSenderService senderService) {
    this.senderService = senderService;
  }

  @PostMapping("/order-placed")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public NotificationLog orderPlaced(@RequestBody OrderPlacedEvent event) {
    return senderService.notifyOrderPlaced(event);
  }

  @PostMapping("/payment-result")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public NotificationLog paymentResult(@RequestBody PaymentResultEvent event) {
    return senderService.notifyPaymentResult(event);
  }
}
