package com.ecommerce.invoice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.common.dto.InvoiceDTO;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.invoice.service.InvoiceGeneratorService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {InvoiceController.class, EventWebhookController.class})
@Import(GlobalExceptionHandler.class)
class InvoiceControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private InvoiceGeneratorService generator;

  private static InvoiceDTO invoice() {
    InvoiceDTO dto =
        new InvoiceDTO("INV-1", "ORD-1", "/tmp/invoices/invoice-ORD-1.pdf", new BigDecimal("20.00"),
            new BigDecimal("0.00"), new BigDecimal("1.60"), new BigDecimal("21.60"),
            "2026-10-10T00:00:00Z");
    dto.setSuccess(true);
    dto.setStatusCode(200);
    return dto;
  }

  @Test
  void getInvoice_existing_returns200() throws Exception {
    when(generator.getInvoice("ORD-1")).thenReturn(invoice());

    mockMvc
        .perform(get("/invoices/ORD-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("INV-1"))
        .andExpect(jsonPath("$.pdfStorageLink").value("/tmp/invoices/invoice-ORD-1.pdf"))
        .andExpect(jsonPath("$.finalTotal").value(21.60));
  }

  @Test
  void getInvoice_notGenerated_returns404WithInv4001() throws Exception {
    when(generator.getInvoice("ORD-X")).thenThrow(CodedErrorFactory.create(MappedError.INV_4001, "ORD-X"));

    mockMvc
        .perform(get("/invoices/ORD-X"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("INV_4001"))
        .andExpect(jsonPath("$.errorDetails").value("Invoice for Order ORD-X has not yet been generated."));
  }

  @Test
  void generate_withAmount_billsDirectly() throws Exception {
    when(generator.generateInvoice(eq("ORD-1"), any())).thenReturn(invoice());

    mockMvc
        .perform(post("/invoices/generate/ORD-1").param("amount", "20.00"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.orderId").value("ORD-1"));
    verify(generator).generateInvoice("ORD-1", new BigDecimal("20.00"));
    verify(generator, never()).generateInvoiceForOrderId(any());
  }

  @Test
  void generate_withoutAmount_looksUpOrder() throws Exception {
    when(generator.generateInvoiceForOrderId("ORD-1")).thenReturn(invoice());

    mockMvc.perform(post("/invoices/generate/ORD-1")).andExpect(status().isCreated());
    verify(generator).generateInvoiceForOrderId("ORD-1");
  }

  @Test
  void generate_unpaidOrder_returns400WithInv4003() throws Exception {
    when(generator.generateInvoiceForOrderId("ORD-2"))
        .thenThrow(CodedErrorFactory.create(MappedError.INV_4003, "ORD-2"));

    mockMvc
        .perform(post("/invoices/generate/ORD-2"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("INV_4003"));
  }

  @Test
  void generate_storageDown_returns503WithInv5002() throws Exception {
    when(generator.generateInvoice(eq("ORD-3"), any())).thenThrow(CodedErrorFactory.create(MappedError.INV_5002));

    mockMvc
        .perform(post("/invoices/generate/ORD-3").param("amount", "5"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("INV_5002"));
  }

  @Test
  void generate_nonNumericAmount_returns400() throws Exception {
    mockMvc
        .perform(post("/invoices/generate/ORD-1").param("amount", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value(GlobalExceptionHandler.BAD_REQUEST_CODE));
  }

  @Test
  void paymentSuccessfulWebhook_returns202() throws Exception {
    when(generator.generateInvoiceForPayment(any())).thenReturn(invoice());

    mockMvc
        .perform(
            post("/events/payment-successful")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"ORD-1\",\"transactionId\":\"TXN-1\",\"success\":true}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.id").value("INV-1"));
  }
}
