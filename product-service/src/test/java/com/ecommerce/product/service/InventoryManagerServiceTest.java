package com.ecommerce.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.InventoryReservationRequest;
import com.ecommerce.common.dto.InventoryReservedEvent;
import com.ecommerce.common.dto.ItemDetails;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.product.model.Inventory;
import com.ecommerce.product.repository.InventoryRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class InventoryManagerServiceTest {

  private InventoryRepository inventoryRepository;
  private InventoryManagerService service;

  @BeforeEach
  void setUp() {
    inventoryRepository = mock(InventoryRepository.class);
    service =
        new InventoryManagerService(
            inventoryRepository, new TransactionTemplate(mock(PlatformTransactionManager.class)));
  }

  private static List<InventoryReservationRequest> req(ItemDetails... items) {
    return List.of(new InventoryReservationRequest("O1", Arrays.asList(items)));
  }

  private static ItemDetails item(String sku, Integer quantity) {
    return new ItemDetails(sku, quantity, null);
  }

  private static void assertCode(Runnable call, String code) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ApplicationBaseException.class)
        .extracting("errorCode")
        .isEqualTo(code);
  }

  @Test
  void reserveStock_enoughStock_increasesReserved() {
    Inventory inventory = new Inventory("A", 10, 2);
    when(inventoryRepository.findById("A")).thenReturn(Optional.of(inventory));

    service.reserveStock(req(item("A", 3)));

    assertThat(inventory.getReservedStock()).isEqualTo(5);
    assertThat(inventory.getCurrentStock()).isEqualTo(10);
    verify(inventoryRepository).saveAll(anyList());
  }

  @Test
  void reserveStock_duplicateSkuLines_areSummedBeforeChecking() {
    Inventory inventory = new Inventory("A", 5, 0);
    when(inventoryRepository.findById("A")).thenReturn(Optional.of(inventory));

    assertCode(() -> service.reserveStock(req(item("A", 3), item("A", 3))), "PROD_4002");
    assertThat(inventory.getReservedStock()).isZero();
  }

  @Test
  void reserveStock_insufficientStock_throwsProd4002WithoutSaving() {
    when(inventoryRepository.findById("A")).thenReturn(Optional.of(new Inventory("A", 3, 2)));

    assertThatThrownBy(() -> service.reserveStock(req(item("A", 2))))
        .isInstanceOf(ApplicationBaseException.class)
        .hasMessageContaining("Only 1 available")
        .extracting("errorCode")
        .isEqualTo("PROD_4002");
    verify(inventoryRepository, never()).saveAll(anyList());
  }

  @Test
  void reserveStock_unknownSku_throwsProd4001() {
    when(inventoryRepository.findById("X")).thenReturn(Optional.empty());
    assertCode(() -> service.reserveStock(req(item("X", 1))), "PROD_4001");
  }

  @Test
  void reserveStock_blankOrNullSku_throwsProd4001() {
    assertCode(() -> service.reserveStock(req(item(" ", 1))), "PROD_4001");
    assertCode(() -> service.reserveStock(req((ItemDetails) null)), "PROD_4001");
  }

  @Test
  void reserveStock_zeroOrNullQuantity_throwsProd4006() {
    assertCode(() -> service.reserveStock(req(item("A", 0))), "PROD_4006");
    assertCode(() -> service.reserveStock(req(item("A", null))), "PROD_4006");
  }

  @Test
  void reserveStock_negativeQuantity_throwsProd4007() {
    assertCode(() -> service.reserveStock(req(item("A", -1))), "PROD_4007");
  }

  @Test
  void reserveStock_noItems_throwsProd4006() {
    assertCode(() -> service.reserveStock(null), "PROD_4006");
    assertCode(() -> service.reserveStock(Arrays.asList((InventoryReservationRequest) null)), "PROD_4006");
    assertCode(
        () -> service.reserveStock(List.of(new InventoryReservationRequest("O1", null))),
        "PROD_4006");
  }

  @Test
  void reserveStock_optimisticLockConflict_throwsProd4008() {
    when(inventoryRepository.findById("A")).thenReturn(Optional.of(new Inventory("A", 10, 0)));
    when(inventoryRepository.saveAll(anyList()))
        .thenThrow(new ObjectOptimisticLockingFailureException(Inventory.class, "A"));

    assertCode(() -> service.reserveStock(req(item("A", 1))), "PROD_4008");
  }

  @Test
  void reserveStock_databaseDown_throwsProd5001() {
    when(inventoryRepository.findById("A"))
        .thenThrow(new DataAccessResourceFailureException("down"));

    assertCode(() -> service.reserveStock(req(item("A", 1))), "PROD_5001");
  }

  @Test
  void reserveStockForOrder_success_returnsSuccessEvent() {
    when(inventoryRepository.findById("A")).thenReturn(Optional.of(new Inventory("A", 10, 0)));

    InventoryReservedEvent event = service.reserveStockForOrder("O1", List.of(item("A", 1)));

    assertThat(event.getOrderId()).isEqualTo("O1");
    assertThat(event.isSuccess()).isTrue();
  }

  @Test
  void reserveStockForOrder_failure_returnsFailureEventWithCode() {
    when(inventoryRepository.findById("A")).thenReturn(Optional.of(new Inventory("A", 0, 0)));

    InventoryReservedEvent event = service.reserveStockForOrder("O1", List.of(item("A", 1)));

    assertThat(event.isSuccess()).isFalse();
    assertThat(event.getMessage()).startsWith("PROD_4002");
  }

  @Test
  void decrementStock_releasesReservedAndReducesCurrent() {
    Inventory inventory = new Inventory("A", 10, 4);
    when(inventoryRepository.findById("A")).thenReturn(Optional.of(inventory));

    service.decrementStock(req(item("A", 3)));

    assertThat(inventory.getCurrentStock()).isEqualTo(7);
    assertThat(inventory.getReservedStock()).isEqualTo(1);
  }

  @Test
  void decrementStock_moreThanReserved_floorsReservedAtZero() {
    Inventory inventory = new Inventory("A", 10, 1);
    when(inventoryRepository.findById("A")).thenReturn(Optional.of(inventory));

    service.decrementStock(req(item("A", 3)));

    assertThat(inventory.getCurrentStock()).isEqualTo(7);
    assertThat(inventory.getReservedStock()).isZero();
  }

  @Test
  void decrementStock_moreThanCurrent_throwsProd4002() {
    when(inventoryRepository.findById("A")).thenReturn(Optional.of(new Inventory("A", 2, 0)));
    assertCode(() -> service.decrementStock(req(item("A", 3))), "PROD_4002");
  }
}
