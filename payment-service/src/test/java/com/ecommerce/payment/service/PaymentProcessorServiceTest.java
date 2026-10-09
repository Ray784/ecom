package com.ecommerce.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.ItemDetails;
import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.ApiInternalException;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.payment.gateway.GatewayErrorMapper;
import com.ecommerce.payment.gateway.RazorpayClient;
import com.ecommerce.payment.gateway.SimulatedPaymentGateway;
import com.ecommerce.payment.model.PaymentTransaction;
import com.ecommerce.payment.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class PaymentProcessorServiceTest {

  private final Map<String, PaymentTransaction> store = new HashMap<>();
  private PaymentTransactionRepository repository;
  private SimulatedPaymentGateway gateway;
  private KafkaProducerService producer;
  private PaymentProcessorService service;

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
        .thenAnswer(
            invocation ->
                store.values().stream()
                    .filter(tx -> tx.getOrderId().equals(invocation.getArgument(0)))
                    .max(Comparator.comparing(PaymentTransaction::getCreatedAt)));
    RazorpayEmulatorService emulator =
        new RazorpayEmulatorService("rzp_test_key_id", "rzp_test_key_secret");
    gateway =
        spy(new SimulatedPaymentGateway(
            new RazorpayClient(emulator, "rzp_test_key_id", "rzp_test_key_secret")));
    producer = mock(KafkaProducerService.class);
    service = new PaymentProcessorService(repository, gateway, new GatewayErrorMapper(), producer);
  }

  private static OrderPlacedEvent order(String orderId, String userId, String total) {
    return new OrderPlacedEvent(orderId, userId, List.of(), total == null ? null : new BigDecimal(total));
  }

  private PaymentResultEvent publishedEvent() {
    ArgumentCaptor<PaymentResultEvent> captor = ArgumentCaptor.forClass(PaymentResultEvent.class);
    verify(producer).sendPaymentResult(captor.capture());
    return captor.getValue();
  }

  @Test
  void initiatePayment_normalOrder_completesAsSuccessAndPublishesSuccessEvent() {
    PaymentTransaction tx = service.initiatePayment(order("o-1", "alice", "49.99"));

    assertThat(tx.getStatus()).isEqualTo(PaymentTransaction.STATUS_SUCCESS);
    assertThat(tx.getGatewayRef()).startsWith("pay_").hasSize(18);
    assertThat(tx.getRazorpayOrderId()).startsWith("order_").hasSize(20);
    assertThat(tx.getFailureReason()).isNull();
    assertThat(store.get(tx.getId()).getStatus()).isEqualTo(PaymentTransaction.STATUS_SUCCESS);
    PaymentResultEvent event = publishedEvent();
    assertThat(event.isSuccess()).isTrue();
    assertThat(event.getOrderId()).isEqualTo("o-1");
    assertThat(event.getTransactionId()).isEqualTo(tx.getId());
  }

  @Test
  void initiatePayment_itemsMatchingTotal_succeeds() {
    OrderPlacedEvent event =
        new OrderPlacedEvent(
            "o-2",
            "bob",
            List.of(new ItemDetails("SKU-1", 2, new BigDecimal("10.00")),
                new ItemDetails("SKU-2", 1, new BigDecimal("5.50"))),
            new BigDecimal("25.50"));

    assertThat(service.initiatePayment(event).getStatus()).isEqualTo(PaymentTransaction.STATUS_SUCCESS);
  }

  @Test
  void initiatePayment_itemsWithoutPrices_skipsTotalCheck() {
    OrderPlacedEvent event =
        new OrderPlacedEvent("o-3", "bob", List.of(new ItemDetails("SKU-1", 2, null)), BigDecimal.TEN);

    assertThat(service.initiatePayment(event).getStatus()).isEqualTo(PaymentTransaction.STATUS_SUCCESS);
  }

  @Test
  void initiatePayment_declinedUser_recordsFailurePublishesFailedEventAndThrowsPay4001() {
    assertThatThrownBy(() -> service.initiatePayment(order("o-4", "decline-user", "20.00")))
        .isInstanceOf(ApplicationBaseException.class)
        .extracting("errorCode")
        .isEqualTo("PAY_4001");

    PaymentTransaction stored = service.getByOrderId("o-4");
    assertThat(stored.getStatus()).isEqualTo(PaymentTransaction.STATUS_FAILED);
    assertThat(stored.getGatewayRef()).startsWith("pay_");
    assertThat(stored.getRazorpayOrderId()).startsWith("order_");
    assertThat(stored.getFailureReason()).startsWith("PAY_4001: ").contains("o-4");
    PaymentResultEvent event = publishedEvent();
    assertThat(event.isSuccess()).isFalse();
    assertThat(event.getFailureReason()).startsWith("PAY_4001");
  }

  @Test
  void initiatePayment_fraudUser_blocksBeforeGatewayAndThrowsPay4006() {
    assertThatThrownBy(() -> service.initiatePayment(order("o-5", "Fraudster", "20.00")))
        .extracting("errorCode")
        .isEqualTo("PAY_4006");

    verify(gateway, never()).charge(anyString(), anyString(), anyString(), any());
    assertThat(service.getByOrderId("o-5").getStatus()).isEqualTo(PaymentTransaction.STATUS_FAILED);
    assertThat(publishedEvent().getFailureReason()).startsWith("PAY_4006");
  }

  @ParameterizedTest
  @CsvSource({
    "invalidcard-user, PAY_4002",
    "gatewaydown-user, PAY_5001",
    "timeout-user, PAY_5002",
    "unmapped-user, PAY_5004"
  })
  void initiatePayment_gatewayFailures_mapToCatalogCodes(String userId, String expectedCode) {
    assertThatThrownBy(() -> service.initiatePayment(order("o-" + userId, userId, "10.00")))
        .extracting("errorCode")
        .isEqualTo(expectedCode);

    assertThat(service.getByOrderId("o-" + userId).getStatus())
        .isEqualTo(PaymentTransaction.STATUS_FAILED);
    assertThat(publishedEvent().getFailureReason()).startsWith(expectedCode);
  }

  @Test
  void initiatePayment_nullEvent_throwsPay4002() {
    assertThatThrownBy(() -> service.initiatePayment(null)).extracting("errorCode").isEqualTo("PAY_4002");
  }

  @ParameterizedTest
  @CsvSource(value = {"NULL, alice", "' ', alice", "o-6, NULL", "o-6, ''"}, nullValues = "NULL")
  void initiatePayment_missingIdentifiers_throwsPay4002(String orderId, String userId) {
    assertThatThrownBy(() -> service.initiatePayment(order(orderId, userId, "10.00")))
        .extracting("errorCode")
        .isEqualTo("PAY_4002");
    verify(repository, never()).save(any());
  }

  @ParameterizedTest
  @CsvSource(value = {"NULL", "0", "-5.00"}, nullValues = "NULL")
  void initiatePayment_nonPositiveAmount_throwsPay4005(String total) {
    assertThatThrownBy(() -> service.initiatePayment(order("o-7", "alice", total)))
        .extracting("errorCode")
        .isEqualTo("PAY_4005");
    verify(repository, never()).save(any());
  }

  @Test
  void initiatePayment_itemsNotMatchingTotal_throwsPay4005() {
    OrderPlacedEvent event =
        new OrderPlacedEvent(
            "o-8", "alice", List.of(new ItemDetails("SKU-1", 1, new BigDecimal("10.00"))),
            new BigDecimal("99.00"));

    assertThatThrownBy(() -> service.initiatePayment(event))
        .extracting("errorCode")
        .isEqualTo("PAY_4005");
  }

  @Test
  void initiatePayment_orderAlreadyPaid_throwsPay4003() {
    service.initiatePayment(order("o-9", "alice", "10.00"));

    assertThatThrownBy(() -> service.initiatePayment(order("o-9", "alice", "10.00")))
        .extracting("errorCode")
        .isEqualTo("PAY_4003");
  }

  @Test
  void initiatePayment_publishFailure_propagatesPay5003() {
    when(producer.sendPaymentResult(any()))
        .thenThrow(CodedErrorFactory.create(MappedError.PAY_5003, "o-10"));

    assertThatThrownBy(() -> service.initiatePayment(order("o-10", "alice", "10.00")))
        .isInstanceOf(ApiInternalException.class)
        .extracting("errorCode")
        .isEqualTo("PAY_5003");
  }

  @Test
  void handleGatewayCallback_unknownTransaction_throwsPay4004() {
    assertThatThrownBy(() -> service.handleGatewayCallback("TXN-missing", true, null))
        .extracting("errorCode")
        .isEqualTo("PAY_4004");
  }

  @Test
  void handleGatewayCallback_initiatedTransactionDeclined_marksFailedWithReason() {
    PaymentTransaction tx =
        new PaymentTransaction("TXN-1", "o-11", "alice", BigDecimal.ONE, "GW-1",
            PaymentTransaction.STATUS_INITIATED);
    store.put(tx.getId(), tx);

    PaymentTransaction updated = service.handleGatewayCallback("TXN-1", false, "insufficient funds");

    assertThat(updated.getStatus()).isEqualTo(PaymentTransaction.STATUS_FAILED);
    assertThat(updated.getFailureReason()).isEqualTo("insufficient funds");
    assertThat(publishedEvent().isSuccess()).isFalse();
  }

  @Test
  void handleGatewayCallback_terminalTransaction_throwsPay4003() {
    PaymentTransaction tx =
        new PaymentTransaction("TXN-2", "o-12", "alice", BigDecimal.ONE, "GW-2",
            PaymentTransaction.STATUS_SUCCESS);
    store.put(tx.getId(), tx);

    assertThatThrownBy(() -> service.handleGatewayCallback("TXN-2", true, null))
        .extracting("errorCode")
        .isEqualTo("PAY_4003");
    verify(producer, never()).sendPaymentResult(any());
  }

  @Test
  void getByOrderId_unknownOrder_throwsPay4004() {
    assertThatThrownBy(() -> service.getByOrderId("nope"))
        .extracting("errorCode")
        .isEqualTo("PAY_4004");
  }
}
