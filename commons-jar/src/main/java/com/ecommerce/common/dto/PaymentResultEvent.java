package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/** Kafka payload published on the payment-successful-topic or payment-failed-topic. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentResultEvent {

  private String orderId;
  private String transactionId;
  private boolean success;
  private String failureReason;

  /** Creates an empty instance for JSON deserialization. */
  public PaymentResultEvent() {}

  /** Creates an instance with all fields populated. */
  public PaymentResultEvent(String orderId, String transactionId, boolean success, String failureReason) {
    this.orderId = orderId;
    this.transactionId = transactionId;
    this.success = success;
    this.failureReason = failureReason;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(String transactionId) {
    this.transactionId = transactionId;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PaymentResultEvent that = (PaymentResultEvent) o;
    return Objects.equals(orderId, that.orderId)
        && Objects.equals(transactionId, that.transactionId)
        && success == that.success
        && Objects.equals(failureReason, that.failureReason);
  }

  @Override
  public int hashCode() {
    return Objects.hash(orderId, transactionId, success, failureReason);
  }

  @Override
  public String toString() {
    return "PaymentResultEvent{" + "orderId=" + orderId
        + ", transactionId=" + transactionId
        + ", success=" + success
        + ", failureReason=" + failureReason + "}";
  }
}
