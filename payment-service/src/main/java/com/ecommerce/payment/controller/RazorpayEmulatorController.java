package com.ecommerce.payment.controller;

import com.ecommerce.payment.service.RazorpayEmulatorService;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fake Razorpay REST surface protected by HTTP Basic credentials. */
@RestController
@RequestMapping("/v1")
public class RazorpayEmulatorController {

  private final RazorpayEmulatorService emulator;

  public RazorpayEmulatorController(RazorpayEmulatorService emulator) {
    this.emulator = emulator;
  }

  /** Creates a Razorpay order, amount is given in paise. */
  @PostMapping("/orders")
  public Map<String, Object> createOrder(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @RequestBody Map<String, Object> request) {
    return emulator.createOrder(authorization, request);
  }

  /** Returns a Razorpay order by its order_ ID. */
  @GetMapping("/orders/{razorpayOrderId}")
  public Map<String, Object> getOrder(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable String razorpayOrderId) {
    return emulator.getOrder(authorization, razorpayOrderId);
  }

  /** Simulates a payment capture for an existing Razorpay order. */
  @PostMapping("/payments")
  public Map<String, Object> createPayment(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @RequestBody Map<String, Object> request) {
    return emulator.createPayment(authorization, request);
  }

  /** Returns a Razorpay payment by its pay_ ID. */
  @GetMapping("/payments/{razorpayPaymentId}")
  public Map<String, Object> getPayment(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable String razorpayPaymentId) {
    return emulator.getPayment(authorization, razorpayPaymentId);
  }
}
