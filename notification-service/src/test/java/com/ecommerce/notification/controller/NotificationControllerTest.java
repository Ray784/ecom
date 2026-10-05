package com.ecommerce.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.notification.model.NotificationLog;
import com.ecommerce.notification.service.NotificationSenderService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {NotificationController.class, EventWebhookController.class})
@Import(GlobalExceptionHandler.class)
class NotificationControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private NotificationSenderService sender;

  private static NotificationLog entry(String type) {
    return new NotificationLog("NTF-1", "alice", "ORD-1", type, "ORDER_PLACED_TEMPLATE", "Hello",
        Instant.parse("2026-10-10T00:00:00Z"), NotificationLog.STATUS_SENT);
  }

  @Test
  void byUser_returnsLogs() throws Exception {
    when(sender.getNotificationsByUserId("alice")).thenReturn(List.of(entry("EMAIL")));

    mockMvc
        .perform(get("/notifications/user/alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("NTF-1"))
        .andExpect(jsonPath("$[0].sentDate").value("2026-10-10T00:00:00Z"));
  }

  @Test
  void byOrder_noLogs_returnsEmptyArray() throws Exception {
    when(sender.getNotificationsByOrderId("ORD-0")).thenReturn(List.of());

    mockMvc
        .perform(get("/notifications/order/ORD-0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void send_emailMergesUserAndOrderIntoTemplateData() throws Exception {
    when(sender.sendEmail(eq("a@example.com"), eq("ORDER_PLACED_TEMPLATE"), anyMap()))
        .thenReturn(entry("EMAIL"));

    mockMvc
        .perform(
            post("/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"recipient\":\"a@example.com\",\"templateId\":\"ORDER_PLACED_TEMPLATE\","
                        + "\"userId\":\"alice\",\"orderId\":\"ORD-1\",\"data\":{\"totalAmount\":5}}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("EMAIL"));
    verify(sender)
        .sendEmail(
            eq("a@example.com"),
            eq("ORDER_PLACED_TEMPLATE"),
            argThat(data -> "alice".equals(data.get("userId"))
                && "ORD-1".equals(data.get("orderId"))
                && data.get("totalAmount") != null));
  }

  @Test
  void send_smsDelegatesToSmsSender() throws Exception {
    when(sender.sendSms("+1555", "hi", "alice", null)).thenReturn(entry("SMS"));

    mockMvc
        .perform(
            post("/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"sms\",\"recipient\":\"+1555\",\"message\":\"hi\",\"userId\":\"alice\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("SMS"));
  }

  @Test
  void send_unsupportedType_returns400WithNotif4001() throws Exception {
    mockMvc
        .perform(
            post("/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"PIGEON\",\"recipient\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("NOTIF_4001"));
  }

  @Test
  void send_unknownTemplate_returns404WithNotif4002() throws Exception {
    when(sender.sendEmail(any(), eq("NOPE"), anyMap()))
        .thenThrow(CodedErrorFactory.create(MappedError.NOTIF_4002, "NOPE"));

    mockMvc
        .perform(
            post("/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipient\":\"a@example.com\",\"templateId\":\"NOPE\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("NOTIF_4002"))
        .andExpect(jsonPath("$.errorDetails").value("Notification template with ID NOPE not found."));
  }

  @Test
  void send_smsGatewayTimeout_returns503WithNotif5002() throws Exception {
    when(sender.sendSms(any(), any(), any(), any())).thenThrow(CodedErrorFactory.create(MappedError.NOTIF_5002));

    mockMvc
        .perform(
            post("/notifications/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"SMS\",\"recipient\":\"+000\",\"message\":\"hi\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("NOTIF_5002"));
  }

  @Test
  void orderPlacedWebhook_returns202() throws Exception {
    when(sender.notifyOrderPlaced(any())).thenReturn(entry("EMAIL"));

    mockMvc
        .perform(
            post("/events/order-placed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"ORD-1\",\"userId\":\"alice\",\"totalAmount\":5}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.orderId").value("ORD-1"));
  }

  @Test
  void paymentResultWebhook_missingOrder_returns400WithNotif4003() throws Exception {
    when(sender.notifyPaymentResult(any()))
        .thenThrow(CodedErrorFactory.create(MappedError.NOTIF_4003, "orderId", "PAYMENT_SUCCESS_TEMPLATE"));

    mockMvc
        .perform(
            post("/events/payment-result")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"success\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("NOTIF_4003"));
  }
}
