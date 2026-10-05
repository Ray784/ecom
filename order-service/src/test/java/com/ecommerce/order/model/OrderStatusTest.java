package com.ecommerce.order.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

  @Test
  void pendingCanMoveToEveryOtherState() {
    assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.INVENTORY_RESERVED)).isTrue();
    assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAID)).isTrue();
    assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.FAILED)).isTrue();
    assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
  }

  @Test
  void inventoryReservedCannotGoBackToPending() {
    assertThat(OrderStatus.INVENTORY_RESERVED.canTransitionTo(OrderStatus.PENDING)).isFalse();
    assertThat(OrderStatus.INVENTORY_RESERVED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
  }

  @Test
  void paidCanOnlyFail() {
    assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.FAILED)).isTrue();
    assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    assertThat(OrderStatus.PAID.isTerminal()).isFalse();
  }

  @Test
  void failedAndCancelledAreTerminal() {
    assertThat(OrderStatus.FAILED.isTerminal()).isTrue();
    assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
    assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PAID)).isFalse();
    assertThat(OrderStatus.PENDING.isTerminal()).isFalse();
  }
}
