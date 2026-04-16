package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.Objects;

/** Line item carried inside Kafka events and inventory reservation requests. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemDetails {

  private String sku;
  private Integer quantity;
  private BigDecimal unitPrice;

  /** Creates an empty instance for JSON deserialization. */
  public ItemDetails() {}

  /** Creates an instance with all fields populated. */
  public ItemDetails(String sku, Integer quantity, BigDecimal unitPrice) {
    this.sku = sku;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
  }

  public String getSku() {
    return sku;
  }

  public void setSku(String sku) {
    this.sku = sku;
  }

  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public void setUnitPrice(BigDecimal unitPrice) {
    this.unitPrice = unitPrice;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ItemDetails that = (ItemDetails) o;
    return Objects.equals(sku, that.sku)
        && Objects.equals(quantity, that.quantity)
        && Objects.equals(unitPrice, that.unitPrice);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sku, quantity, unitPrice);
  }

  @Override
  public String toString() {
    return "ItemDetails{" + "sku=" + sku
        + ", quantity=" + quantity
        + ", unitPrice=" + unitPrice + "}";
  }
}
