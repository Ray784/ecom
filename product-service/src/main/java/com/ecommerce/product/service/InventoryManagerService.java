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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
  private final Map<String, OrderReservation> orderReservations = new ConcurrentHashMap<>();

  public InventoryManagerService(
      InventoryRepository inventoryRepository, TransactionTemplate transactionTemplate) {
    this.inventoryRepository = inventoryRepository;
    this.transactionTemplate = transactionTemplate;
  }

  /** Settlement state of a reservation that was made for one order. */
  public enum ReservationState {
    RESERVED,
    DECREMENTED,
    RELEASED
  }

  /** Items reserved for one order together with how far the order has been settled. */
  private record OrderReservation(List<ItemDetails> items, ReservationState state) {}

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
   * <p>The failure message is the formatted catalog message, for example PROD_4002. A successful
   * reservation is remembered per order id so the payment result can later settle it.
   */
  public InventoryReservedEvent reserveStockForOrder(String orderId, List<ItemDetails> items) {
    try {
      reserveStock(List.of(new InventoryReservationRequest(orderId, items)));
      if (orderId != null) {
        orderReservations.put(
            orderId, new OrderReservation(List.copyOf(items), ReservationState.RESERVED));
      }
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

  /**
   * Returns reserved units to available stock without touching currentStock.
   *
   * <p>reservedStock never drops below zero.
   */
  public void releaseStock(List<InventoryReservationRequest> requests) {
    Map<String, Integer> quantities = aggregate(requests);
    runAtomically(
        quantities,
        inventory ->
            inventory.setReservedStock(
                Math.max(0, inventory.getReservedStock() - quantities.get(inventory.getSku()))));
  }

  /**
   * Decrements stock for a paid order using the items reserved for it.
   *
   * <p>Returns false without changing stock when the order has no open reservation, for example on
   * a replayed payment event.
   */
  public boolean decrementStockForOrder(String orderId) {
    return settleOrder(orderId, ReservationState.DECREMENTED, this::decrementStock);
  }

  /**
   * Releases the stock reserved for an order whose payment failed.
   *
   * <p>Returns false without changing stock when the order has no open reservation.
   */
  public boolean releaseReservedStockForOrder(String orderId) {
    return settleOrder(orderId, ReservationState.RELEASED, this::releaseStock);
  }

  /** Returns the settlement state for an order, or null when nothing was reserved for it. */
  public ReservationState getReservationState(String orderId) {
    OrderReservation reservation = orderId == null ? null : orderReservations.get(orderId);
    return reservation == null ? null : reservation.state();
  }

  /**
   * Applies a stock action to a RESERVED order exactly once and moves it to the target state.
   *
   * <p>The map entry is locked while the action runs, so concurrent duplicates cannot both apply.
   * If the action throws, the order stays RESERVED and the error propagates.
   */
  private boolean settleOrder(
      String orderId,
      ReservationState target,
      Consumer<List<InventoryReservationRequest>> action) {
    if (orderId == null || orderId.isBlank()) {
      return false;
    }
    AtomicBoolean applied = new AtomicBoolean(false);
    orderReservations.computeIfPresent(
        orderId,
        (id, reservation) -> {
          if (reservation.state() != ReservationState.RESERVED) {
            return reservation;
          }
          action.accept(List.of(new InventoryReservationRequest(id, reservation.items())));
          applied.set(true);
          return new OrderReservation(reservation.items(), target);
        });
    if (!applied.get()) {
      log.info(
          "Order {} has no open reservation, skipping {} with state {}",
          orderId, target, getReservationState(orderId));
    }
    return applied.get();
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
