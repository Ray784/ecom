package com.ecommerce.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.payment.gateway.GatewayErrorMapper;
import com.ecommerce.payment.gateway.RazorpayClient;
import com.ecommerce.payment.gateway.SimulatedPaymentGateway;
import com.ecommerce.payment.model.PaymentTransaction;
import com.ecommerce.payment.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class RazorpayWebhookServiceTest {

  private static final String SECRET = "rzp_test_webhook_secret";

  private final Map<String, PaymentTransaction> store = new HashMap<>();
  private PaymentTransactionRepository repository;
  private KafkaProducerService producer;
  private RazorpayWebhookService service;

  @BeforeEach
  void setUp() {
    repository = mock(PaymentTransactionRepository.class);
    when(repository.save(any(PaymentTransaction.class)))
        .thenAnswer(
            invocation -> {
              PaymentTransaction tx = invocation.getArgument(0);
              store.put(tx.getId(), tx);
              return tx;
            });
    when(repository.findById(anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.getArgument(0))));
    when(repository.findFirstByOrderIdOrderByCreatedAtDesc(anyString()))
        .thenAnswer(invocation -> latest(PaymentTransaction::getOrderId, invocation.getArgument(0)));
    when(repository.findFirstByGatewayRefOrderByCreatedAtDesc(anyString()))
        .thenAnswer(
            invocation -> latest(PaymentTransaction::getGatewayRef, invocation.getArgument(0)));
    when(repository.findFirstByRazorpayOrderIdOrderByCreatedAtDesc(anyString()))
        .thenAnswer(
            invocation -> latest(PaymentTransaction::getRazorpayOrderId, invocation.getArgument(0)));
    producer = mock(KafkaProducerService.class);
    RazorpayEmulatorService emulator =
        new RazorpayEmulatorService("rzp_test_key_id", "rzp_test_key_secret");
    SimulatedPaymentGateway gateway =
        new SimulatedPaymentGateway(
            new RazorpayClient(emulator, "rzp_test_key_id", "rzp_test_key_secret"));
    PaymentProcessorService processor =
        new PaymentProcessorService(repository, gateway, new GatewayErrorMapper(), producer);
    service = new RazorpayWebhookService(repository, processor, SECRET);
  }

  private Optional<PaymentTransaction> latest(
      Function<PaymentTransaction, String> field, String value) {
    return store.values().stream()
        .filter(tx -> value.equals(field.apply(tx)))
        .max(Comparator.comparing(PaymentTransaction::getCreatedAt));
  }

  private PaymentTransaction seed(String id, String status) {
    PaymentTransaction tx =
        new PaymentTransaction(id, "o-" + id, "alice", new BigDecimal("50.00"), "pay_" + id, status);
    tx.setRazorpayOrderId("order_" + id);
    store.put(id, tx);
    return tx;
  }

  private Map<String, Object> deliver(String body) {
    return service.processWebhook(body, RazorpayWebhookService.sign(body, SECRET));
  }

  private static String nested(String event, String entityFields) {
    return "{\"event\":\"" + event + "\",\"payload\":{\"payment\":{\"entity\":{"
        + entityFields + "}}}}";
  }

  private PaymentResultEvent publishedEvent() {
    ArgumentCaptor<PaymentResultEvent> captor = ArgumentCaptor.forClass(PaymentResultEvent.class);
    verify(producer).sendPaymentResult(captor.capture());
    return captor.getValue();
  }

  @Test
  void sign_matchesKnownHmacSha256Vector() {
    assertThat(RazorpayWebhookService.sign("{}", SECRET))
        .isEqualTo("73e599c241e718f854468dbbbd3f6cfcfdea8463fe17570422e3b4fbb74a8b18");
  }

  @Test
  void processWebhook_missingSignature_throwsPay4002() {
    assertThatThrownBy(() -> service.processWebhook("{}", null))
        .extracting("errorCode")
        .isEqualTo("PAY_4002");
    assertThatThrownBy(() -> service.processWebhook("{}", " "))
        .extracting("errorCode")
        .isEqualTo("PAY_4002");
  }

  @Test
  void processWebhook_nullBody_throwsPay4002() {
    assertThatThrownBy(() -> service.processWebhook(null, "abc"))
        .extracting("errorCode")
        .isEqualTo("PAY_4002");
  }

  @Test
  void processWebhook_signatureFromOtherSecretOrTamperedBody_throwsPay4002() {
    seed("T1", PaymentTransaction.STATUS_INITIATED);
    String body = nested("payment.captured", "\"id\":\"pay_T1\"");

    assertThatThrownBy(
            () -> service.processWebhook(body, RazorpayWebhookService.sign(body, "other")))
        .extracting("errorCode")
        .isEqualTo("PAY_4002");
    assertThatThrownBy(
            () -> service.processWebhook(body + " ", RazorpayWebhookService.sign(body, SECRET)))
        .extracting("errorCode")
        .isEqualTo("PAY_4002");
    verify(producer, never()).sendPaymentResult(any());
  }

  @Test
  void processWebhook_uppercaseSignature_isAccepted() {
    seed("T1", PaymentTransaction.STATUS_INITIATED);
    String body = nested("payment.captured", "\"id\":\"pay_T1\"");

    Map<String, Object> response =
        service.processWebhook(body, RazorpayWebhookService.sign(body, SECRET).toUpperCase());

    assertThat(response).containsEntry("status", "processed");
  }

  @Test
  void processWebhook_nestedCapturedByPaymentId_marksSuccessAndPublishesSuccess() {
    seed("T1", PaymentTransaction.STATUS_INITIATED);

    Map<String, Object> response =
        deliver(nested("payment.captured", "\"id\":\"pay_T1\",\"order_id\":\"order_zzz\""));

    assertThat(response)
        .containsEntry("status", "processed")
        .containsEntry("event", "payment.captured")
        .containsEntry("transactionId", "T1")
        .containsEntry("orderId", "o-T1")
        .containsEntry("paymentStatus", "SUCCESS")
        .containsEntry("gatewayRef", "pay_T1");
    assertThat(store.get("T1").getStatus()).isEqualTo(PaymentTransaction.STATUS_SUCCESS);
    PaymentResultEvent event = publishedEvent();
    assertThat(event.isSuccess()).isTrue();
    assertThat(event.getOrderId()).isEqualTo("o-T1");
  }

  @Test
  void processWebhook_nestedFailedByRazorpayOrderId_marksFailedWithDescription() {
    seed("T2", PaymentTransaction.STATUS_INITIATED);

    Map<String, Object> response =
        deliver(
            nested(
                "payment.failed",
                "\"id\":\"pay_other\",\"order_id\":\"order_T2\","
                    + "\"error_description\":\"Card declined by gateway\""));

    assertThat(response)
        .containsEntry("event", "payment.failed")
        .containsEntry("paymentStatus", "FAILED");
    assertThat(store.get("T2").getFailureReason()).isEqualTo("Card declined by gateway");
    PaymentResultEvent event = publishedEvent();
    assertThat(event.isSuccess()).isFalse();
    assertThat(event.getFailureReason()).isEqualTo("Card declined by gateway");
  }

  @Test
  void processWebhook_failedWithoutDescription_usesDefaultReason() {
    seed("T3", PaymentTransaction.STATUS_INITIATED);

    deliver(nested("payment.failed", "\"receipt\":\"o-T3\""));

    assertThat(store.get("T3").getFailureReason())
        .isEqualTo(RazorpayWebhookService.DEFAULT_FAILURE_REASON);
  }

  @Test
  void processWebhook_flatTransactionIdWithApprovedFalse_appliesReason() {
    seed("T4", PaymentTransaction.STATUS_INITIATED);

    Map<String, Object> response =
        deliver("{\"transactionId\":\"T4\",\"approved\":false,\"reason\":\"insufficient funds\"}");

    assertThat(response).containsEntry("event", "payment.failed");
    assertThat(store.get("T4").getFailureReason()).isEqualTo("insufficient funds");
  }

  @Test
  void processWebhook_flatOrderIdWithOrderPaidEvent_marksSuccess() {
    seed("T5", PaymentTransaction.STATUS_INITIATED);

    Map<String, Object> response = deliver("{\"event\":\"order.paid\",\"orderId\":\"o-T5\"}");

    assertThat(response).containsEntry("paymentStatus", "SUCCESS");
  }

  @Test
  void processWebhook_flatApprovedTrueWithoutEvent_reportsCapturedEvent() {
    seed("T6", PaymentTransaction.STATUS_INITIATED);

    Map<String, Object> response = deliver("{\"paymentId\":\"pay_T6\",\"approved\":true}");

    assertThat(response).containsEntry("event", "payment.captured");
  }

  @Test
  void processWebhook_unknownTransactionId_throwsPay4004() {
    assertThatThrownBy(() -> deliver("{\"transactionId\":\"TXN-x\",\"approved\":true}"))
        .extracting("errorCode")
        .isEqualTo("PAY_4004");
  }

  @Test
  void processWebhook_unknownOrderFromNestedPayload_createsTransactionAndPublishes() {
    Map<String, Object> response =
        deliver(
            nested(
                "payment.captured",
                "\"id\":\"pay_new\",\"order_id\":\"order_new\",\"receipt\":\"o-new\","
                    + "\"amount\":4999,\"notes\":{\"userId\":\"carol\"}"));

    String transactionId = (String) response.get("transactionId");
    PaymentTransaction created = store.get(transactionId);
    assertThat(transactionId).startsWith("TXN-");
    assertThat(created.getOrderId()).isEqualTo("o-new");
    assertThat(created.getUserId()).isEqualTo("carol");
    assertThat(created.getAmount()).isEqualByComparingTo("49.99");
    assertThat(created.getGatewayRef()).isEqualTo("pay_new");
    assertThat(created.getRazorpayOrderId()).isEqualTo("order_new");
    assertThat(created.getStatus()).isEqualTo(PaymentTransaction.STATUS_SUCCESS);
    assertThat(publishedEvent().getOrderId()).isEqualTo("o-new");
  }

  @Test
  void processWebhook_unknownFlatOrder_createsTransactionWithFlatAmountAndUser() {
    Map<String, Object> response =
        deliver(
            "{\"event\":\"payment.captured\",\"orderId\":\"o-flat\",\"userId\":\"dave\","
                + "\"amount\":12.50}");

    PaymentTransaction created = store.get((String) response.get("transactionId"));
    assertThat(created.getUserId()).isEqualTo("dave");
    assertThat(created.getAmount()).isEqualByComparingTo("12.50");
    assertThat(created.getGatewayRef()).isNull();
  }

  @Test
  void processWebhook_unknownOrderWithoutAmount_createsTransactionWithNullAmount() {
    Map<String, Object> response =
        deliver(nested("payment.captured", "\"receipt\":\"o-noamt\",\"payer_user_id\":\"erin\""));

    PaymentTransaction created = store.get((String) response.get("transactionId"));
    assertThat(created.getAmount()).isNull();
    assertThat(created.getUserId()).isEqualTo("erin");
  }

  @Test
  void processWebhook_terminalTransactionWithSameOutcome_isIdempotent() {
    seed("T7", PaymentTransaction.STATUS_SUCCESS);

    Map<String, Object> response = deliver(nested("payment.captured", "\"id\":\"pay_T7\""));

    assertThat(response)
        .containsEntry("status", "already_processed")
        .containsEntry("paymentStatus", "SUCCESS");
    verify(producer, never()).sendPaymentResult(any());
  }

  @Test
  void processWebhook_terminalTransactionWithConflictingOutcome_throwsPay4003() {
    seed("T8", PaymentTransaction.STATUS_SUCCESS);

    assertThatThrownBy(() -> deliver(nested("payment.failed", "\"id\":\"pay_T8\"")))
        .extracting("errorCode")
        .isEqualTo("PAY_4003");
    verify(producer, never()).sendPaymentResult(any());
  }

  @Test
  void processWebhook_unknownPaymentWithoutOrder_throwsPay4004() {
    assertThatThrownBy(() -> deliver(nested("payment.captured", "\"id\":\"pay_ghost\"")))
        .extracting("errorCode")
        .isEqualTo("PAY_4004");
  }

  @Test
  void processWebhook_noIdentifiers_throwsPay4002() {
    assertThatThrownBy(() -> deliver(nested("payment.captured", "\"id\":\"\"")))
        .extracting("errorCode")
        .isEqualTo("PAY_4002");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not json",
        "[1,2]",
        "{\"event\":\"refund.created\",\"orderId\":\"o-1\"}",
        "{\"orderId\":\"o-1\"}",
        "{\"approved\":\"yes\",\"orderId\":\"o-1\"}"
      })
  void processWebhook_malformedOrUnsupportedPayload_throwsPay4002(String body) {
    assertThatThrownBy(() -> deliver(body)).extracting("errorCode").isEqualTo("PAY_4002");
  }
}
