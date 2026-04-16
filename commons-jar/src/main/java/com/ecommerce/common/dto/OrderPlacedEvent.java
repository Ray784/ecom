package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Kafka payload published on the order-placed-topic when an order is created. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderPlacedEvent {

  private String orderId;
  private String userId;
  private List<ItemDetails> items;
  private BigDecimal totalAmount;

  /** Creates an empty instance for JSON deserialization. */
  public OrderPlacedEvent() {}

  /** Creates an instance with all fields populated. */
  public OrderPlacedEvent(String orderId, String userId, List<ItemDetails> items, BigDecimal totalAmount) {
    this.orderId = orderId;
    this.userId = userId;
    this.items = items;
    this.totalAmount = totalAmount;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public List<ItemDetails> getItems() {
    return items;
  }

  public void setItems(List<ItemDetails> items) {
    this.items = items;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OrderPlacedEvent that = (OrderPlacedEvent) o;
    return Objects.equals(orderId, that.orderId)
        && Objects.equals(userId, that.userId)
        && Objects.equals(items, that.items)
        && Objects.equals(totalAmount, that.totalAmount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(orderId, userId, items, totalAmount);
  }

  @Override
  public String toString() {
    return "OrderPlacedEvent{" + "orderId=" + orderId
        + ", userId=" + userId
        + ", items=" + items
        + ", totalAmount=" + totalAmount + "}";
  }
}
