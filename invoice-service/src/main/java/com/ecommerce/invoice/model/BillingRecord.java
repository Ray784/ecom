package com.ecommerce.invoice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Monetary breakdown used to bill one order. */
@Entity
@Table(name = "billing_records")
public class BillingRecord {

  @Id
  @Column(length = 64)
  private String orderId;

  @Column(precision = 19, scale = 2)
  private BigDecimal subtotal;

  @Column(precision = 19, scale = 2)
  private BigDecimal discount;

  @Column(precision = 19, scale = 2)
  private BigDecimal taxAmount;

  @Column(precision = 19, scale = 2)
  private BigDecimal finalTotal;

  public BillingRecord() {}

  public BillingRecord(
      String orderId,
      BigDecimal subtotal,
      BigDecimal discount,
      BigDecimal taxAmount,
      BigDecimal finalTotal) {
    this.orderId = orderId;
    this.subtotal = subtotal;
    this.discount = discount;
    this.taxAmount = taxAmount;
    this.finalTotal = finalTotal;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public BigDecimal getSubtotal() {
    return subtotal;
  }

  public void setSubtotal(BigDecimal subtotal) {
    this.subtotal = subtotal;
  }

  public BigDecimal getDiscount() {
    return discount;
  }

  public void setDiscount(BigDecimal discount) {
    this.discount = discount;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }

  public void setTaxAmount(BigDecimal taxAmount) {
    this.taxAmount = taxAmount;
  }

  public BigDecimal getFinalTotal() {
    return finalTotal;
  }

  public void setFinalTotal(BigDecimal finalTotal) {
    this.finalTotal = finalTotal;
  }
}
