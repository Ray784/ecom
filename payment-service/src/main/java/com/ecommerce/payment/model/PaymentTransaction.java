package com.ecommerce.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Record of one payment attempt for an order. */
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

  public static final String STATUS_INITIATED = "INITIATED";
  public static final String STATUS_SUCCESS = "SUCCESS";
  public static final String STATUS_FAILED = "FAILED";

  @Id
  @Column(length = 64)
  private String id;

  @Column(nullable = false, length = 64)
  private String orderId;

  @Column(length = 128)
  private String userId;

  @Column(precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(length = 64)
  private String gatewayRef;

  @Column(length = 64)
  private String razorpayOrderId;

  @Column(nullable = false, length = 16)
  private String status;

  @Column(length = 512)
  private String failureReason;

  @Column(nullable = false)
  private Instant createdAt;

  public PaymentTransaction() {}

  public PaymentTransaction(
      String id, String orderId, String userId, BigDecimal amount, String gatewayRef, String status) {
    this.id = id;
    this.orderId = orderId;
    this.userId = userId;
    this.amount = amount;
    this.gatewayRef = gatewayRef;
    this.status = status;
    this.createdAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getGatewayRef() {
    return gatewayRef;
  }

  public void setGatewayRef(String gatewayRef) {
    this.gatewayRef = gatewayRef;
  }

  public String getRazorpayOrderId() {
    return razorpayOrderId;
  }

  public void setRazorpayOrderId(String razorpayOrderId) {
    this.razorpayOrderId = razorpayOrderId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  /** Returns true once the transaction reached SUCCESS or FAILED. */
  public boolean isTerminal() {
    return STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status);
  }

  @Override
  public String toString() {
    return "PaymentTransaction{id=" + id + ", orderId=" + orderId + ", userId=" + userId
        + ", amount=" + amount + ", status=" + status + ", failureReason=" + failureReason + "}";
  }
}
