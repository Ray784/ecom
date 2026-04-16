package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

/** Synchronous request asking the product service to reserve stock for an order. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryReservationRequest {

  private String orderId;
  private List<ItemDetails> items;

  /** Creates an empty instance for JSON deserialization. */
  public InventoryReservationRequest() {}

  /** Creates an instance with all fields populated. */
  public InventoryReservationRequest(String orderId, List<ItemDetails> items) {
    this.orderId = orderId;
    this.items = items;
  }

  /** Creates a reservation request for a single SKU and quantity. */
  public InventoryReservationRequest(String orderId, String sku, Integer quantity) {
    this(orderId, List.of(new ItemDetails(sku, quantity, null)));
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public List<ItemDetails> getItems() {
    return items;
  }

  public void setItems(List<ItemDetails> items) {
    this.items = items;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InventoryReservationRequest that = (InventoryReservationRequest) o;
    return Objects.equals(orderId, that.orderId)
        && Objects.equals(items, that.items);
  }

  @Override
  public int hashCode() {
    return Objects.hash(orderId, items);
  }

  @Override
  public String toString() {
    return "InventoryReservationRequest{" + "orderId=" + orderId
        + ", items=" + items + "}";
  }
}
