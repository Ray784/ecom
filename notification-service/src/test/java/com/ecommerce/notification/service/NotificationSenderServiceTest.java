package com.ecommerce.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.exception.ApiInternalException;
import com.ecommerce.notification.model.NotificationLog;
import com.ecommerce.notification.model.NotificationTemplate;
import com.ecommerce.notification.repository.NotificationLogRepository;
import com.ecommerce.notification.repository.NotificationTemplateRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NotificationSenderServiceTest {

  private NotificationLogRepository logRepository;
  private NotificationTemplateRepository templateRepository;
  private NotificationSenderService service;

  @BeforeEach
  void setUp() {
    logRepository = new NotificationLogRepository();
    templateRepository = new NotificationTemplateRepository();
    service = new NotificationSenderService(logRepository, templateRepository);
  }

  private static Map<String, Object> orderData(String userId, String orderId, Object total) {
    Map<String, Object> data = new HashMap<>();
    data.put("userId", userId);
    data.put("orderId", orderId);
    data.put("totalAmount", total);
    return data;
  }

  @Test
  void templates_defaultsAreSeeded() {
    assertThat(templateRepository.findById(NotificationTemplate.ORDER_PLACED_TEMPLATE)).isPresent();
    assertThat(templateRepository.findById(NotificationTemplate.PAYMENT_SUCCESS_TEMPLATE)).isPresent();
    assertThat(templateRepository.findById(NotificationTemplate.PAYMENT_FAILED_TEMPLATE)).isPresent();
    assertThat(templateRepository.findById(null)).isEmpty();
  }

  @Test
  void sendEmail_rendersTemplateAndLogsSentEntry() {
    NotificationLog entry =
        service.sendEmail("alice@example.com", NotificationTemplate.ORDER_PLACED_TEMPLATE,
            orderData("alice", "ORD-1", new BigDecimal("49.99")));

    assertThat(entry.getStatus()).isEqualTo(NotificationLog.STATUS_SENT);
    assertThat(entry.getType()).isEqualTo(NotificationLog.TYPE_EMAIL);
    assertThat(entry.getUserId()).isEqualTo("alice");
    assertThat(entry.getOrderId()).isEqualTo("ORD-1");
    assertThat(entry.getMessage())
        .isEqualTo("Hello alice, your order ORD-1 for 49.99 has been placed successfully.");
    assertThat(entry.getSentDate()).isNotNull();
    assertThat(service.getNotificationsByUserId("alice")).containsExactly(entry);
  }

  @Test
  void sendEmail_withoutUserIdInData_keysLogByRecipient() {
    Map<String, Object> data = Map.of("orderId", "ORD-2", "transactionId", "TXN-2");

    NotificationLog entry =
        service.sendEmail("bob@example.com", NotificationTemplate.PAYMENT_SUCCESS_TEMPLATE, data);

    assertThat(entry.getUserId()).isEqualTo("bob@example.com");
  }

  @Test
  void sendEmail_valueWithRegexCharacters_isInsertedLiterally() {
    NotificationLog entry =
        service.sendEmail("c@example.com", NotificationTemplate.PAYMENT_FAILED_TEMPLATE,
            Map.of("orderId", "ORD-3", "failureReason", "cost $5 \\ retry"));

    assertThat(entry.getMessage()).endsWith("failed: cost $5 \\ retry");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void sendEmail_missingRecipient_throwsNotif4001(String recipient) {
    assertThatThrownBy(() -> service.sendEmail(recipient, NotificationTemplate.ORDER_PLACED_TEMPLATE, Map.of()))
        .hasMessageContaining("EMAIL")
        .extracting("errorCode")
        .isEqualTo("NOTIF_4001");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"NO_SUCH_TEMPLATE"})
  void sendEmail_unknownTemplate_throwsNotif4002(String templateId) {
    assertThatThrownBy(() -> service.sendEmail("a@example.com", templateId, Map.of()))
        .extracting("errorCode")
        .isEqualTo("NOTIF_4002");
  }

  @Test
  void sendEmail_missingTemplateParameter_throwsNotif4003WithoutLogging() {
    assertThatThrownBy(
            () -> service.sendEmail("a@example.com", NotificationTemplate.ORDER_PLACED_TEMPLATE,
                orderData("alice", "ORD-4", null)))
        .hasMessage("Required data parameter totalAmount missing for template ORDER_PLACED_TEMPLATE.")
        .extracting("errorCode")
        .isEqualTo("NOTIF_4003");
    assertThat(logRepository.count()).isZero();
  }

  @Test
  void sendEmail_nullData_throwsNotif4003() {
    assertThatThrownBy(() -> service.sendEmail("a@example.com", NotificationTemplate.PAYMENT_SUCCESS_TEMPLATE, null))
        .extracting("errorCode")
        .isEqualTo("NOTIF_4003");
  }

  @Test
  void sendEmail_providerDown_logsFailedEntryAndThrowsNotif5001() {
    assertThatThrownBy(
            () -> service.sendEmail("smtp-down@example.com", NotificationTemplate.ORDER_PLACED_TEMPLATE,
                orderData("dave", "ORD-5", BigDecimal.ONE)))
        .extracting("errorCode")
        .isEqualTo("NOTIF_5001");

    List<NotificationLog> logs = service.getNotificationsByOrderId("ORD-5");
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getStatus()).isEqualTo(NotificationLog.STATUS_FAILED);
  }

  @Test
  void sendEmail_storeFailure_throwsNotif5003() {
    NotificationLogRepository broken = mock(NotificationLogRepository.class);
    when(broken.save(any())).thenThrow(new IllegalStateException("disk full"));
    NotificationSenderService brokenService = new NotificationSenderService(broken, templateRepository);

    assertThatThrownBy(
            () -> brokenService.sendEmail("e@example.com", NotificationTemplate.ORDER_PLACED_TEMPLATE,
                orderData("erin", "ORD-6", BigDecimal.ONE)))
        .isInstanceOf(ApiInternalException.class)
        .hasMessageContaining("erin")
        .extracting("errorCode")
        .isEqualTo("NOTIF_5003");
  }

  @Test
  void sendSms_logsSentEntryKeyedByPhone() {
    NotificationLog entry = service.sendSms("+15550001111", "Your parcel shipped");

    assertThat(entry.getType()).isEqualTo(NotificationLog.TYPE_SMS);
    assertThat(entry.getUserId()).isEqualTo("+15550001111");
    assertThat(entry.getTemplateId()).isEqualTo(NotificationSenderService.DIRECT_SMS_TEMPLATE);
    assertThat(entry.getStatus()).isEqualTo(NotificationLog.STATUS_SENT);
  }

  @Test
  void sendSms_withUserAndOrder_linksLogEntry() {
    NotificationLog entry = service.sendSms("+15550001111", "hi", "frank", "ORD-7");

    assertThat(service.getNotificationsByUserId("frank")).containsExactly(entry);
    assertThat(service.getNotificationsByOrderId("ORD-7")).containsExactly(entry);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void sendSms_missingPhone_throwsNotif4001(String phone) {
    assertThatThrownBy(() -> service.sendSms(phone, "hi"))
        .hasMessageContaining("SMS")
        .extracting("errorCode")
        .isEqualTo("NOTIF_4001");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void sendSms_missingMessage_throwsNotif4003(String message) {
    assertThatThrownBy(() -> service.sendSms("+15550001111", message))
        .extracting("errorCode")
        .isEqualTo("NOTIF_4003");
  }

  @Test
  void sendSms_gatewayTimeout_logsFailedEntryAndThrowsNotif5002() {
    assertThatThrownBy(() -> service.sendSms("+0001234", "hi", "gina", "ORD-8"))
        .extracting("errorCode")
        .isEqualTo("NOTIF_5002");

    assertThat(service.getNotificationsByOrderId("ORD-8").get(0).getStatus())
        .isEqualTo(NotificationLog.STATUS_FAILED);
  }

  @Test
  void notifyOrderPlaced_emailsCustomerAddressDerivedFromUserId() {
    NotificationLog entry =
        service.notifyOrderPlaced(new OrderPlacedEvent("ORD-9", "henry", List.of(), new BigDecimal("12.00")));

    assertThat(entry.getTemplateId()).isEqualTo(NotificationTemplate.ORDER_PLACED_TEMPLATE);
    assertThat(entry.getUserId()).isEqualTo("henry");
    assertThat(entry.getMessage()).contains("ORD-9").contains("12.00");
  }

  @Test
  void emailFor_keepsAddressesAndDerivesOthers() {
    assertThat(NotificationSenderService.emailFor("x@y.z")).isEqualTo("x@y.z");
    assertThat(NotificationSenderService.emailFor("ivy")).isEqualTo("ivy@customers.ecommerce.local");
  }

  @Test
  void notifyOrderPlaced_missingOrderId_throwsNotif4003() {
    assertThatThrownBy(() -> service.notifyOrderPlaced(new OrderPlacedEvent(null, "u", List.of(), BigDecimal.ONE)))
        .extracting("errorCode")
        .isEqualTo("NOTIF_4003");
    assertThatThrownBy(() -> service.notifyOrderPlaced(null))
        .extracting("errorCode")
        .isEqualTo("NOTIF_4003");
  }

  @Test
  void notifyOrderPlaced_missingUserId_throwsNotif4001() {
    assertThatThrownBy(() -> service.notifyOrderPlaced(new OrderPlacedEvent("ORD-10", " ", List.of(), BigDecimal.ONE)))
        .extracting("errorCode")
        .isEqualTo("NOTIF_4001");
  }

  @Test
  void notifyOrderPlaced_missingTotal_throwsNotif4003() {
    assertThatThrownBy(() -> service.notifyOrderPlaced(new OrderPlacedEvent("ORD-11", "u", List.of(), null)))
        .extracting("errorCode")
        .isEqualTo("NOTIF_4003");
  }

  @Test
  void notifyPaymentResult_successAfterOrderPlaced_resolvesUserAndUsesSuccessTemplate() {
    service.notifyOrderPlaced(new OrderPlacedEvent("ORD-12", "jack", List.of(), BigDecimal.TEN));

    NotificationLog entry = service.notifyPaymentResult(new PaymentResultEvent("ORD-12", "TXN-12", true, null));

    assertThat(entry.getTemplateId()).isEqualTo(NotificationTemplate.PAYMENT_SUCCESS_TEMPLATE);
    assertThat(entry.getUserId()).isEqualTo("jack");
    assertThat(entry.getMessage()).contains("TXN-12");
    assertThat(service.getNotificationsByUserId("jack")).hasSize(2);
    assertThat(service.getNotificationsByOrderId("ORD-12"))
        .extracting(NotificationLog::getTemplateId)
        .containsExactly(NotificationTemplate.ORDER_PLACED_TEMPLATE, NotificationTemplate.PAYMENT_SUCCESS_TEMPLATE);
  }

  @Test
  void notifyPaymentResult_failure_usesFailedTemplateWithReason() {
    NotificationLog entry =
        service.notifyPaymentResult(new PaymentResultEvent("ORD-13", "TXN-13", false, "PAY_4001: declined"));

    assertThat(entry.getTemplateId()).isEqualTo(NotificationTemplate.PAYMENT_FAILED_TEMPLATE);
    assertThat(entry.getMessage()).isEqualTo("Payment for order ORD-13 failed: PAY_4001: declined");
    assertThat(entry.getUserId()).isEqualTo("order-ORD-13@customers.ecommerce.local");
  }

  @Test
  void notifyPaymentResult_missingReasonAndTransaction_usesPlaceholders() {
    assertThat(service.notifyPaymentResult(new PaymentResultEvent("ORD-14", null, false, null)).getMessage())
        .endsWith("failed: unknown reason");
    assertThat(service.notifyPaymentResult(new PaymentResultEvent("ORD-15", null, true, null)).getMessage())
        .contains("Transaction n/a");
  }

  @Test
  void notifyPaymentResult_missingOrderId_throwsNotif4003() {
    assertThatThrownBy(() -> service.notifyPaymentResult(null))
        .extracting("errorCode")
        .isEqualTo("NOTIF_4003");
    assertThatThrownBy(() -> service.notifyPaymentResult(new PaymentResultEvent("", "T", true, null)))
        .hasMessageContaining(NotificationTemplate.PAYMENT_SUCCESS_TEMPLATE)
        .extracting("errorCode")
        .isEqualTo("NOTIF_4003");
  }

  @Test
  void getNotifications_unknownKeys_returnEmptyLists() {
    assertThat(service.getNotificationsByUserId("nobody")).isEmpty();
    assertThat(service.getNotificationsByOrderId("none")).isEmpty();
  }
}
