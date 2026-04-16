package com.ecommerce.common.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Full details of a finalized or pending order. */
public class OrderDetailsDTO extends BaseResponseDTO {

  private String orderId;
  private String userId;
  private String status;
  private BigDecimal totalAmount;
  private String shippingAddress;
  private List<OrderItemDTO> items;
  private String creationDate;

  /** Creates an empty instance for JSON deserialization. */
  public OrderDetailsDTO() {}

  /** Creates a successful response with status 200 carrying the given payload fields. */
  public OrderDetailsDTO(String orderId, String userId, String status, BigDecimal totalAmount, String shippingAddress, List<OrderItemDTO> items, String creationDate) {
    super(true, 200, "Success");
    this.orderId = orderId;
    this.userId = userId;
    this.status = status;
    this.totalAmount = totalAmount;
    this.shippingAddress = shippingAddress;
    this.items = items;
    this.creationDate = creationDate;
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }

  public String getShippingAddress() {
    return shippingAddress;
  }

  public void setShippingAddress(String shippingAddress) {
    this.shippingAddress = shippingAddress;
  }

  public List<OrderItemDTO> getItems() {
    return items;
  }

  public void setItems(List<OrderItemDTO> items) {
    this.items = items;
  }

  public String getCreationDate() {
    return creationDate;
  }

  public void setCreationDate(String creationDate) {
    this.creationDate = creationDate;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    OrderDetailsDTO that = (OrderDetailsDTO) o;
    return Objects.equals(orderId, that.orderId)
        && Objects.equals(userId, that.userId)
        && Objects.equals(status, that.status)
        && Objects.equals(totalAmount, that.totalAmount)
        && Objects.equals(shippingAddress, that.shippingAddress)
        && Objects.equals(items, that.items)
        && Objects.equals(creationDate, that.creationDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), orderId, userId, status, totalAmount, shippingAddress, items, creationDate);
  }

  @Override
  public String toString() {
    return "OrderDetailsDTO{" + "orderId=" + orderId
        + ", userId=" + userId
        + ", status=" + status
        + ", totalAmount=" + totalAmount
        + ", shippingAddress=" + shippingAddress
        + ", items=" + items
        + ", creationDate=" + creationDate
        + ", " + super.toString() + "}";
  }
}
