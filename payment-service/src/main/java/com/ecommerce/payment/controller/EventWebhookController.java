package com.ecommerce.payment.controller;

import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.payment.dto.PaymentTransactionResponse;
import com.ecommerce.payment.service.PaymentProcessorService;
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

  private final PaymentProcessorService paymentProcessorService;

  public EventWebhookController(PaymentProcessorService paymentProcessorService) {
    this.paymentProcessorService = paymentProcessorService;
  }

  /** Accepts an OrderPlaced event over HTTP and processes it like the Kafka listener. */
  @PostMapping("/order-placed")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public PaymentTransactionResponse orderPlaced(@RequestBody OrderPlacedEvent event) {
    return PaymentTransactionResponse.from(paymentProcessorService.initiatePayment(event));
  }
}
