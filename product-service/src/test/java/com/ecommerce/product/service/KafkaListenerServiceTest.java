package com.ecommerce.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.InventoryReservedEvent;
import com.ecommerce.common.dto.ItemDetails;
import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KafkaListenerServiceTest {

  private InventoryManagerService inventoryManagerService;
  private KafkaProducerService kafkaProducerService;
  private KafkaListenerService service;

  private final List<ItemDetails> items = List.of(new ItemDetails("A", 1, BigDecimal.ONE));
  private final OrderPlacedEvent event = new OrderPlacedEvent("O1", "U1", items, BigDecimal.ONE);

  @BeforeEach
  void setUp() {
    inventoryManagerService = mock(InventoryManagerService.class);
    kafkaProducerService = mock(KafkaProducerService.class);
    service =
        new KafkaListenerService(inventoryManagerService, kafkaProducerService, new ObjectMapper());
  }

  @Test
  void handleOrderPlaced_reservesAndPublishesResult() {
    InventoryReservedEvent result = new InventoryReservedEvent("O1", true, "ok");
    when(inventoryManagerService.reserveStockForOrder("O1", items)).thenReturn(result);

    assertThat(service.handleOrderPlaced(event)).isSameAs(result);
    verify(kafkaProducerService).sendInventoryResult(result);
  }

  @Test
  void handleOrderPlaced_duplicateOrder_isReservedAndPublishedOnce() {
    InventoryReservedEvent result = new InventoryReservedEvent("O1", true, "ok");
    when(inventoryManagerService.reserveStockForOrder("O1", items)).thenReturn(result);

    service.handleOrderPlaced(event);
    InventoryReservedEvent second = service.handleOrderPlaced(event);

    assertThat(second).isSameAs(result);
    verify(inventoryManagerService, times(1)).reserveStockForOrder("O1", items);
    verify(kafkaProducerService, times(1)).sendInventoryResult(result);
  }

  @Test
  void handleOrderPlaced_missingOrderId_returnsFailureWithoutSideEffects() {
    assertThat(service.handleOrderPlaced(null).isSuccess()).isFalse();
    InventoryReservedEvent result =
        service.handleOrderPlaced(new OrderPlacedEvent(" ", "U1", items, BigDecimal.ONE));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getOrderId()).isNull();
    verify(inventoryManagerService, never()).reserveStockForOrder(any(), any());
    verify(kafkaProducerService, never()).sendInventoryResult(any());
  }

  @Test
  void onOrderPlaced_publishFailure_isSwallowedToAvoidRedelivery() {
    when(inventoryManagerService.reserveStockForOrder("O1", items))
        .thenReturn(new InventoryReservedEvent("O1", true, "ok"));
    when(kafkaProducerService.sendInventoryResult(any()))
        .thenThrow(CodedErrorFactory.create(MappedError.PROD_5002, "O1"));

    assertThatCode(() -> service.onOrderPlaced(event)).doesNotThrowAnyException();
  }

  @Test
  void onOrderPlaced_success_delegatesToHandler() {
    when(inventoryManagerService.reserveStockForOrder("O1", items))
        .thenReturn(new InventoryReservedEvent("O1", true, "ok"));

    service.onOrderPlaced(event);

    verify(kafkaProducerService).sendInventoryResult(any());
  }

  @Test
  void handlePaymentResult_success_decrementsStockForOrder() {
    when(inventoryManagerService.decrementStockForOrder("O1")).thenReturn(true);

    assertThat(service.handlePaymentResult(new PaymentResultEvent("O1", "T1", true, null)))
        .isTrue();
    verify(inventoryManagerService, never()).releaseReservedStockForOrder(any());
  }

  @Test
  void handlePaymentResult_failure_releasesReservedStockForOrder() {
    when(inventoryManagerService.releaseReservedStockForOrder("O1")).thenReturn(true);

    assertThat(service.handlePaymentResult(new PaymentResultEvent("O1", null, false, "declined")))
        .isTrue();
    verify(inventoryManagerService, never()).decrementStockForOrder(any());
  }

  @Test
  void handlePaymentResult_missingOrderId_returnsFalseWithoutSideEffects() {
    assertThat(service.handlePaymentResult(null)).isFalse();
    assertThat(service.handlePaymentResult(new PaymentResultEvent(" ", "T1", true, null)))
        .isFalse();

    verify(inventoryManagerService, never()).decrementStockForOrder(any());
    verify(inventoryManagerService, never()).releaseReservedStockForOrder(any());
  }

  @Test
  void onPaymentSuccessful_jsonObject_decrements() {
    service.onPaymentSuccessful("{\"orderId\":\"O1\",\"success\":true,\"extra\":1}");

    verify(inventoryManagerService).decrementStockForOrder("O1");
  }

  @Test
  void onPaymentSuccessful_jsonStringWrappingObject_decrements() {
    service.onPaymentSuccessful("\"{\\\"orderId\\\":\\\"O1\\\",\\\"success\\\":true}\"");

    verify(inventoryManagerService).decrementStockForOrder("O1");
  }

  @Test
  void onPaymentSuccessful_successFalse_releases() {
    service.onPaymentSuccessful("{\"orderId\":\"O1\",\"success\":false}");

    verify(inventoryManagerService).releaseReservedStockForOrder("O1");
    verify(inventoryManagerService, never()).decrementStockForOrder(any());
  }

  @Test
  void onPaymentFailed_alwaysReleasesEvenIfSuccessFlagSet() {
    service.onPaymentFailed("{\"orderId\":\"O1\",\"success\":true}");

    verify(inventoryManagerService).releaseReservedStockForOrder("O1");
    verify(inventoryManagerService, never()).decrementStockForOrder(any());
  }

  @Test
  void onPaymentListeners_blankOrMalformedPayload_isSkipped() {
    assertThatCode(
            () -> {
              service.onPaymentSuccessful(null);
              service.onPaymentSuccessful(" ");
              service.onPaymentSuccessful("not-json");
              service.onPaymentFailed("null");
              service.onPaymentFailed("42");
            })
        .doesNotThrowAnyException();

    verify(inventoryManagerService, never()).decrementStockForOrder(any());
    verify(inventoryManagerService, never()).releaseReservedStockForOrder(any());
  }

  @Test
  void onPaymentSuccessful_inventoryError_isSwallowedToAvoidRedelivery() {
    when(inventoryManagerService.decrementStockForOrder("O1"))
        .thenThrow(CodedErrorFactory.create(MappedError.PROD_4002, "A", 0));

    assertThatCode(() -> service.onPaymentSuccessful("{\"orderId\":\"O1\",\"success\":true}"))
        .doesNotThrowAnyException();
  }
}
