package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/** Kafka payload published on the inventory-reserved-topic or inventory-failed-topic. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryReservedEvent {

  private String orderId;
  private boolean success;
  private String message;

  /** Creates an empty instance for JSON deserialization. */
  public InventoryReservedEvent() {}

  /** Creates an instance with all fields populated. */
  public InventoryReservedEvent(String orderId, boolean success, String message) {
    this.orderId = orderId;
    this.success = success;
    this.message = message;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InventoryReservedEvent that = (InventoryReservedEvent) o;
    return Objects.equals(orderId, that.orderId)
        && success == that.success
        && Objects.equals(message, that.message);
  }

  @Override
  public int hashCode() {
    return Objects.hash(orderId, success, message);
  }

  @Override
  public String toString() {
    return "InventoryReservedEvent{" + "orderId=" + orderId
        + ", success=" + success
        + ", message=" + message + "}";
  }
}
