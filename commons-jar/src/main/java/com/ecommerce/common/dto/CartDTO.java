package com.ecommerce.common.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Current state of a user shopping cart. */
public class CartDTO extends BaseResponseDTO {

  private String userId;
  private List<CartItemDTO> items;
  private BigDecimal subtotal;

  /** Creates an empty instance for JSON deserialization. */
  public CartDTO() {}

  /** Creates a successful response with status 200 carrying the given payload fields. */
  public CartDTO(String userId, List<CartItemDTO> items, BigDecimal subtotal) {
    super(true, 200, "Success");
    this.userId = userId;
    this.items = items;
    this.subtotal = subtotal;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public List<CartItemDTO> getItems() {
    return items;
  }

  public void setItems(List<CartItemDTO> items) {
    this.items = items;
  }

  public BigDecimal getSubtotal() {
    return subtotal;
  }

  public void setSubtotal(BigDecimal subtotal) {
    this.subtotal = subtotal;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    CartDTO that = (CartDTO) o;
    return Objects.equals(userId, that.userId)
        && Objects.equals(items, that.items)
        && Objects.equals(subtotal, that.subtotal);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), userId, items, subtotal);
  }

  @Override
  public String toString() {
    return "CartDTO{" + "userId=" + userId
        + ", items=" + items
        + ", subtotal=" + subtotal
        + ", " + super.toString() + "}";
  }
}
