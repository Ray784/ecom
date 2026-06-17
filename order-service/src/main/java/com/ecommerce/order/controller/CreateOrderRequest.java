package com.ecommerce.order.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Optional body of POST /orders. Every field may be omitted. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateOrderRequest {

  private String userId;
  private String shippingAddress;
  private BigDecimal expectedTotal;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getShippingAddress() {
    return shippingAddress;
  }

  public void setShippingAddress(String shippingAddress) {
    this.shippingAddress = shippingAddress;
  }

  public BigDecimal getExpectedTotal() {
    return expectedTotal;
  }

  public void setExpectedTotal(BigDecimal expectedTotal) {
    this.expectedTotal = expectedTotal;
  }
}
