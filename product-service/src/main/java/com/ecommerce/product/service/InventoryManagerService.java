package com.ecommerce.product.service;

import com.ecommerce.common.dto.InventoryReservationRequest;
import com.ecommerce.common.dto.InventoryReservedEvent;
import com.ecommerce.common.dto.ItemDetails;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.product.model.Inventory;
import com.ecommerce.product.repository.InventoryRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reserves and decrements stock atomically.
 *
 * <p>Each operation validates every line first and then updates all rows in one transaction, so a
 * multi item order is either fully reserved or not reserved at all.
 */
@Service
public class InventoryManagerService {

  private static final Logger log = LoggerFactory.getLogger(InventoryManagerService.class);

  private final InventoryRepository inventoryRepository;
  private final TransactionTemplate transactionTemplate;

  public InventoryManagerService(
      InventoryRepository inventoryRepository, TransactionTemplate transactionTemplate) {
    this.inventoryRepository = inventoryRepository;
    this.transactionTemplate = transactionTemplate;
  }

  /** Moves the requested quantities from available into reserved stock. */
  public void reserveStock(List<InventoryReservationRequest> requests) {
    Map<String, Integer> quantities = aggregate(requests);
    runAtomically(
        quantities,
        inventory -> {
          int requested = quantities.get(inventory.getSku());
          if (inventory.getAvailableStock() < requested) {
            throw CodedErrorFactory.create(
                MappedError.PROD_4002, inventory.getSku(), inventory.getAvailableStock());
          }
          inventory.setReservedStock(inventory.getReservedStock() + requested);
        });
  }

  /**
   * Reserves stock for an order and reports the outcome as an event instead of throwing.
   *
   * <p>The failure message is the formatted catalog message, for example PROD_4002.
   */
  public InventoryReservedEvent reserveStockForOrder(String orderId, List<ItemDetails> items) {
    try {
      reserveStock(List.of(new InventoryReservationRequest(orderId, items)));
      return new InventoryReservedEvent(orderId, true, "Stock reserved for order " + orderId);
    } catch (ApplicationBaseException e) {
      log.info("Reservation failed for order {}: {} {}", orderId, e.getErrorCode(), e.getMessage());
      return new InventoryReservedEvent(orderId, false, e.getErrorCode() + ": " + e.getMessage());
    }
  }

  /**
   * Permanently removes shipped units from stock.
   *
   * <p>Previously reserved units are released first, so decrementing a reserved order keeps the
   * available count unchanged.
   */
  public void decrementStock(List<InventoryReservationRequest> requests) {
    Map<String, Integer> quantities = aggregate(requests);
    runAtomically(
        quantities,
        inventory -> {
          int requested = quantities.get(inventory.getSku());
          if (inventory.getCurrentStock() < requested) {
            throw CodedErrorFactory.create(
                MappedError.PROD_4002, inventory.getSku(), inventory.getCurrentStock());
          }
          inventory.setCurrentStock(inventory.getCurrentStock() - requested);
          inventory.setReservedStock(
              Math.max(0, inventory.getReservedStock() - requested));
        });
  }

  private void runAtomically(Map<String, Integer> quantities, Consumer<Inventory> mutation) {
    try {
      transactionTemplate.executeWithoutResult(
          status -> {
            List<Inventory> rows = new ArrayList<>();
            for (String sku : quantities.keySet()) {
              Inventory inventory =
                  inventoryRepository
                      .findById(sku)
                      .orElseThrow(() -> CodedErrorFactory.create(MappedError.PROD_4001, sku));
              mutation.accept(inventory);
              rows.add(inventory);
            }
            inventoryRepository.saveAll(rows);
          });
    } catch (OptimisticLockingFailureException e) {
      throw CodedErrorFactory.create(MappedError.PROD_4008, e);
    } catch (DataAccessResourceFailureException | CannotCreateTransactionException e) {
      throw CodedErrorFactory.create(MappedError.PROD_5001, e);
    }
  }

  /** Validates all lines and sums quantities per SKU, keeping request order. */
  private static Map<String, Integer> aggregate(List<InventoryReservationRequest> requests) {
    Map<String, Integer> quantities = new LinkedHashMap<>();
    if (requests != null) {
      for (InventoryReservationRequest request : requests) {
        if (request == null || request.getItems() == null) {
          continue;
        }
        for (ItemDetails item : request.getItems()) {
          validateItem(item);
          quantities.merge(item.getSku().trim(), item.getQuantity(), Integer::sum);
        }
      }
    }
    if (quantities.isEmpty()) {
      throw CodedErrorFactory.create(MappedError.PROD_4006, 0, "none");
    }
    return quantities;
  }

  private static void validateItem(ItemDetails item) {
    if (item == null || item.getSku() == null || item.getSku().isBlank()) {
      throw CodedErrorFactory.create(MappedError.PROD_4001, item == null ? null : item.getSku());
    }
    Integer quantity = item.getQuantity();
    if (quantity != null && quantity < 0) {
      throw CodedErrorFactory.create(MappedError.PROD_4007);
    }
    if (quantity == null || quantity == 0) {
      throw CodedErrorFactory.create(MappedError.PROD_4006, quantity, item.getSku());
    }
  }
}
