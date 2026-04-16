package com.ecommerce.common.dto;

import java.math.BigDecimal;
import java.util.Objects;

/** Item added to or held in a shopping cart. */
public class CartItemDTO {

  private String sku;
  private Integer quantity;
  private BigDecimal unitPrice;

  /** Creates an empty instance for JSON deserialization. */
  public CartItemDTO() {}

  /** Creates an instance with all fields populated. */
  public CartItemDTO(String sku, Integer quantity, BigDecimal unitPrice) {
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
    CartItemDTO that = (CartItemDTO) o;
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
    return "CartItemDTO{" + "sku=" + sku
        + ", quantity=" + quantity
        + ", unitPrice=" + unitPrice + "}";
  }
}
