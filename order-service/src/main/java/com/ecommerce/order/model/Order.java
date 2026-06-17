package com.ecommerce.order.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A placed order. The status is stored as the name of an OrderStatus constant.
 *
 * <p>The table is named orders because ORDER is a reserved SQL keyword.
 */
@Entity
@Table(name = "orders")
public class Order {

  /** Failure reason recorded when inventory reservation fails. */
  public static final String REASON_INVENTORY_FAILED = "INVENTORY_FAILED";

  /** Failure reason recorded when payment fails. */
  public static final String REASON_PAYMENT_FAILED = "PAYMENT_FAILED";

  /** Failure reason recorded when the OrderPlaced event could not be published. */
  public static final String REASON_PUBLISH_FAILED = "PUBLISH_FAILED";

  @Id
  @Column(length = 64)
  private String id;

  @Column(nullable = false, length = 64)
  private String userId;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal totalAmount;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(nullable = false, length = 512)
  private String shippingAddress;

  @Column(nullable = false)
  private Instant creationDate;

  @Column(length = 64)
  private String failureReason;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "order_id", nullable = false)
  @OrderBy("id ASC")
  private List<OrderItem> items = new ArrayList<>();

  /** Creates an empty instance for JPA. */
  protected Order() {}

  /** Creates a new PENDING order without items. */
  public Order(String id, String userId, String shippingAddress, Instant creationDate) {
    this.id = id;
    this.userId = userId;
    this.shippingAddress = shippingAddress;
    this.creationDate = creationDate;
    this.status = OrderStatus.PENDING.name();
    this.totalAmount = BigDecimal.ZERO;
  }

  public String getId() {
    return id;
  }

  public String getUserId() {
    return userId;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  /** Returns the status parsed as an OrderStatus constant. */
  public OrderStatus statusValue() {
    return OrderStatus.valueOf(status);
  }

  public String getShippingAddress() {
    return shippingAddress;
  }

  public Instant getCreationDate() {
    return creationDate;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public List<OrderItem> getItems() {
    return items;
  }

  /** Appends a line and binds it to this order. */
  public void addItem(OrderItem item) {
    item.setOrderId(id);
    items.add(item);
  }
}
