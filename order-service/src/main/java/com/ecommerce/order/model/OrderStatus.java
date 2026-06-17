package com.ecommerce.order.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle states of an order and the transitions allowed between them.
 *
 * <p>PENDING is the initial state. FAILED and CANCELLED are terminal. A PAID order may still
 * become FAILED when inventory reservation fails after payment, but it can no longer be
 * cancelled by the customer.
 */
public enum OrderStatus {
  PENDING,
  INVENTORY_RESERVED,
  PAID,
  FAILED,
  CANCELLED;

  private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS =
      Map.of(
          PENDING, EnumSet.of(INVENTORY_RESERVED, PAID, FAILED, CANCELLED),
          INVENTORY_RESERVED, EnumSet.of(PAID, FAILED, CANCELLED),
          PAID, EnumSet.of(FAILED),
          FAILED, EnumSet.noneOf(OrderStatus.class),
          CANCELLED, EnumSet.noneOf(OrderStatus.class));

  /** Returns true when no further transition is possible from this state. */
  public boolean isTerminal() {
    return TRANSITIONS.get(this).isEmpty();
  }

  /** Returns true when moving from this state to the target state is allowed. */
  public boolean canTransitionTo(OrderStatus target) {
    return TRANSITIONS.get(this).contains(target);
  }
}
