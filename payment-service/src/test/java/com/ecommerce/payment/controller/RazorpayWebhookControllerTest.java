package com.ecommerce.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.payment.model.PaymentTransaction;
import com.ecommerce.payment.repository.PaymentTransactionRepository;
import com.ecommerce.payment.service.PaymentProcessorService;
import com.ecommerce.payment.service.RazorpayWebhookService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RazorpayWebhookController.class)
@Import({RazorpayWebhookService.class, GlobalExceptionHandler.class})
class RazorpayWebhookControllerTest {

  private static final String SECRET = "rzp_test_webhook_secret";

  @Autowired private MockMvc mockMvc;

  @MockBean private PaymentTransactionRepository repository;
  @MockBean private PaymentProcessorService processor;

  private PaymentTransaction tx;

  @BeforeEach
  void setUp() {
    tx =
        new PaymentTransaction("TXN-1", "o-1", "alice", new BigDecimal("50.00"), "pay_abc",
            PaymentTransaction.STATUS_INITIATED);
    when(repository.findFirstByGatewayRefOrderByCreatedAtDesc("pay_abc"))
        .thenReturn(Optional.of(tx));
  }

  private static String body(String event) {
    return "{\"event\":\"" + event + "\",\"payload\":{\"payment\":{\"entity\":"
        + "{\"id\":\"pay_abc\",\"order_id\":\"order_abc\",\"receipt\":\"o-1\","
        + "\"error_description\":\"Card declined by gateway\"}}}}";
  }

  private static PaymentTransaction withStatus(PaymentTransaction source, String status) {
    PaymentTransaction copy =
        new PaymentTransaction(source.getId(), source.getOrderId(), source.getUserId(),
            source.getAmount(), source.getGatewayRef(), status);
    return copy;
  }

  @Test
  void webhook_validSignatureCaptured_returns200Processed() throws Exception {
    String body = body("payment.captured");
    when(processor.handleGatewayCallback("TXN-1", true, null))
        .thenReturn(withStatus(tx, PaymentTransaction.STATUS_SUCCESS));

    mockMvc
        .perform(
            post("/payments/razorpay/webhook")
                .header("X-Razorpay-Signature", RazorpayWebhookService.sign(body, SECRET))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("processed"))
        .andExpect(jsonPath("$.event").value("payment.captured"))
        .andExpect(jsonPath("$.transactionId").value("TXN-1"))
        .andExpect(jsonPath("$.orderId").value("o-1"))
        .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"));
  }

  @Test
  void webhook_validSignatureFailed_returns200AndAppliesDecline() throws Exception {
    String body = body("payment.failed");
    when(processor.handleGatewayCallback("TXN-1", false, "Card declined by gateway"))
        .thenReturn(withStatus(tx, PaymentTransaction.STATUS_FAILED));

    mockMvc
        .perform(
            post("/payments/razorpay/webhook")
                .header("X-Razorpay-Signature", RazorpayWebhookService.sign(body, SECRET))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event").value("payment.failed"))
        .andExpect(jsonPath("$.paymentStatus").value("FAILED"));
    verify(processor).handleGatewayCallback("TXN-1", false, "Card declined by gateway");
  }

  @Test
  void webhook_invalidSignature_returns400WithPay4002() throws Exception {
    mockMvc
        .perform(
            post("/payments/razorpay/webhook")
                .header("X-Razorpay-Signature", "deadbeef")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("payment.captured")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("PAY_4002"));
    verify(processor, never()).handleGatewayCallback(any(), anyBoolean(), any());
  }

  @Test
  void webhook_missingSignature_returns400WithPay4002() throws Exception {
    mockMvc
        .perform(
            post("/payments/razorpay/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("payment.captured")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("PAY_4002"));
    verify(processor, never()).handleGatewayCallback(eq("TXN-1"), anyBoolean(), any());
  }

  @Test
  void webhook_emptyBody_returns400WithPay4002() throws Exception {
    mockMvc
        .perform(
            post("/payments/razorpay/webhook")
                .header("X-Razorpay-Signature", "deadbeef")
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("PAY_4002"));
  }
}
