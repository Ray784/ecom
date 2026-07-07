package com.ecommerce.payment.controller;

import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.payment.dto.PaymentTransactionResponse;
import com.ecommerce.payment.service.PaymentProcessorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Inspection and internal trigger endpoints for payments. */
@RestController
@RequestMapping("/payments")
public class PaymentController {

  private final PaymentProcessorService paymentProcessorService;

  public PaymentController(PaymentProcessorService paymentProcessorService) {
    this.paymentProcessorService = paymentProcessorService;
  }

  /** Returns the latest transaction of the order, PAY_4004 when none exists. */
  @GetMapping("/order/{orderId}")
  public PaymentTransactionResponse getByOrderId(@PathVariable String orderId) {
    return PaymentTransactionResponse.from(paymentProcessorService.getByOrderId(orderId));
  }

  /** Internal or test trigger that runs the full payment flow synchronously. */
  @PostMapping("/process")
  @ResponseStatus(HttpStatus.CREATED)
  public PaymentTransactionResponse process(@RequestBody OrderPlacedEvent event) {
    return PaymentTransactionResponse.from(paymentProcessorService.initiatePayment(event));
  }
}
