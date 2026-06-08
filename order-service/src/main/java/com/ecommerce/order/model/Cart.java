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
import java.util.Optional;

/** Shopping cart of a single user, keyed by the user ID. */
@Entity
@Table(name = "carts")
public class Cart {

  @Id
  @Column(name = "user_id", length = 64)
  private String userId;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id", nullable = false)
  @OrderBy("id ASC")
  private List<CartItem> items = new ArrayList<>();

  private Instant lastUpdated;

  /** Creates an empty instance for JPA. */
  protected Cart() {}

  /** Creates an empty cart for the given user. */
  public Cart(String userId) {
    this.userId = userId;
    this.lastUpdated = Instant.now();
  }

  public String getUserId() {
    return userId;
  }

  public List<CartItem> getItems() {
    return items;
  }

  public Instant getLastUpdated() {
    return lastUpdated;
  }

  public void setLastUpdated(Instant lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  /** Returns the line for the given SKU when the cart already contains it. */
  public Optional<CartItem> findItem(String sku) {
    return items.stream().filter(item -> item.getSku().equals(sku)).findFirst();
  }

  /** Appends a new line and binds it to this cart. */
  public void addItem(CartItem item) {
    item.setUserId(userId);
    items.add(item);
  }

  /** Returns the sum of quantities across all lines. */
  public int totalQuantity() {
    return items.stream().mapToInt(CartItem::getQuantity).sum();
  }

  /** Returns the sum of unit price times quantity across all lines. */
  public BigDecimal subtotal() {
    return items.stream().map(CartItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public boolean isEmpty() {
    return items.isEmpty();
  }
}
