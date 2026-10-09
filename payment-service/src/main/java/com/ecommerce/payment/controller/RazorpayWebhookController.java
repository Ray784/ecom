package com.ecommerce.payment.controller;

import com.ecommerce.payment.service.RazorpayWebhookService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Receives Razorpay webhooks, the raw body is kept intact for signature verification. */
@RestController
@RequestMapping("/payments/razorpay")
public class RazorpayWebhookController {

  public static final String SIGNATURE_HEADER = "X-Razorpay-Signature";

  private final RazorpayWebhookService webhookService;

  public RazorpayWebhookController(RazorpayWebhookService webhookService) {
    this.webhookService = webhookService;
  }

  /** Verifies the signature and applies the payment verdict, PAY_4002 on a bad signature. */
  @PostMapping("/webhook")
  public Map<String, Object> webhook(
      @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
      @RequestBody(required = false) String rawBody) {
    return webhookService.processWebhook(rawBody, signature);
  }
}
