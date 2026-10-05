package com.ecommerce.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.payment.model.PaymentTransaction;
import com.ecommerce.payment.service.PaymentProcessorService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {PaymentController.class, EventWebhookController.class})
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

  private static final String ORDER_JSON =
      "{\"orderId\":\"o-1\",\"userId\":\"alice\",\"items\":[],\"totalAmount\":49.99}";

  @Autowired private MockMvc mockMvc;

  @MockBean private PaymentProcessorService processor;

  private static PaymentTransaction successTx() {
    PaymentTransaction tx =
        new PaymentTransaction("TXN-1", "o-1", "alice", new BigDecimal("49.99"), "GW-1",
            PaymentTransaction.STATUS_SUCCESS);
    return tx;
  }

  @Test
  void getByOrderId_existing_returnsTransaction() throws Exception {
    when(processor.getByOrderId("o-1")).thenReturn(successTx());

    mockMvc
        .perform(get("/payments/order/o-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionId").value("TXN-1"))
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.amount").value(49.99))
        .andExpect(jsonPath("$.createdAt").isNotEmpty());
  }

  @Test
  void getByOrderId_missing_returns404WithPay4004() throws Exception {
    when(processor.getByOrderId("nope"))
        .thenThrow(CodedErrorFactory.create(MappedError.PAY_4004, "nope"));

    mockMvc
        .perform(get("/payments/order/nope"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("PAY_4004"))
        .andExpect(jsonPath("$.errorDetails").value("Transaction record for Order nope not found."));
  }

  @Test
  void process_validOrder_returns201() throws Exception {
    when(processor.initiatePayment(any())).thenReturn(successTx());

    mockMvc
        .perform(post("/payments/process").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.orderId").value("o-1"))
        .andExpect(jsonPath("$.status").value("SUCCESS"));
  }

  @Test
  void process_declined_returns400WithPay4001() throws Exception {
    when(processor.initiatePayment(any()))
        .thenThrow(CodedErrorFactory.create(MappedError.PAY_4001, "o-1"));

    mockMvc
        .perform(post("/payments/process").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("PAY_4001"));
  }

  @Test
  void process_gatewayDown_returns503WithPay5001() throws Exception {
    when(processor.initiatePayment(any())).thenThrow(CodedErrorFactory.create(MappedError.PAY_5001));

    mockMvc
        .perform(post("/payments/process").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("PAY_5001"));
  }

  @Test
  void process_malformedBody_returns400() throws Exception {
    mockMvc
        .perform(post("/payments/process").contentType(MediaType.APPLICATION_JSON).content("{oops"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value(GlobalExceptionHandler.BAD_REQUEST_CODE));
  }

  @Test
  void orderPlacedWebhook_validOrder_returns202() throws Exception {
    when(processor.initiatePayment(any())).thenReturn(successTx());

    mockMvc
        .perform(post("/events/order-placed").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.transactionId").value("TXN-1"));
  }

  @Test
  void orderPlacedWebhook_fraud_returns400WithPay4006() throws Exception {
    when(processor.initiatePayment(any()))
        .thenThrow(CodedErrorFactory.create(MappedError.PAY_4006, "fraud-1"));

    mockMvc
        .perform(post("/events/order-placed").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("PAY_4006"));
  }
}
