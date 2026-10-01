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
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
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
    service = new KafkaListenerService(inventoryManagerService, kafkaProducerService);
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
}
