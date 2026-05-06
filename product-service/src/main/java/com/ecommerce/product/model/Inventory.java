package com.ecommerce.product.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Stock counters for one SKU.
 *
 * <p>Available stock is currentStock minus reservedStock. The version column enables optimistic
 * locking so concurrent reservations cannot oversell.
 */
@Entity
@Table(name = "inventory")
public class Inventory {

  @Id
  @Column(length = 64)
  private String sku;

  @Column(nullable = false)
  private Integer currentStock;

  @Column(nullable = false)
  private Integer reservedStock;

  @Version private Long version;

  public Inventory() {}

  public Inventory(String sku, Integer currentStock, Integer reservedStock) {
    this.sku = sku;
    this.currentStock = currentStock;
    this.reservedStock = reservedStock;
  }

  /** Returns the number of units that can still be reserved. */
  public int getAvailableStock() {
    return currentStock - reservedStock;
  }

  public String getSku() {
    return sku;
  }

  public void setSku(String sku) {
    this.sku = sku;
  }

  public Integer getCurrentStock() {
    return currentStock;
  }

  public void setCurrentStock(Integer currentStock) {
    this.currentStock = currentStock;
  }

  public Integer getReservedStock() {
    return reservedStock;
  }

  public void setReservedStock(Integer reservedStock) {
    this.reservedStock = reservedStock;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }
}
