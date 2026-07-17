package com.ecommerce.invoice.controller;

import com.ecommerce.common.dto.InvoiceDTO;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.invoice.service.InvoiceGeneratorService;
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

  private final InvoiceGeneratorService invoiceGeneratorService;

  public EventWebhookController(InvoiceGeneratorService invoiceGeneratorService) {
    this.invoiceGeneratorService = invoiceGeneratorService;
  }

  /** Accepts a successful PaymentResult event over HTTP and generates the invoice. */
  @PostMapping("/payment-successful")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public InvoiceDTO paymentSuccessful(@RequestBody PaymentResultEvent event) {
    return invoiceGeneratorService.generateInvoiceForPayment(event);
  }
}
