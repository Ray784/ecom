package com.ecommerce.product.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Catalog entry for a sellable product keyed by its SKU. */
@Entity
@Table(name = "products")
public class Product {

  @Id
  @Column(length = 64)
  private String sku;

  @Column(nullable = false)
  private String name;

  @Column(length = 2000)
  private String description;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal price;

  @Column(nullable = false, length = 64)
  private String categoryId;

  @Column(length = 1024)
  private String imageUrl;

  @Column(precision = 3, scale = 2)
  private BigDecimal averageRating;

  public Product() {}

  public Product(
      String sku,
      String name,
      String description,
      BigDecimal price,
      String categoryId,
      String imageUrl,
      BigDecimal averageRating) {
    this.sku = sku;
    this.name = name;
    this.description = description;
    this.price = price;
    this.categoryId = categoryId;
    this.imageUrl = imageUrl;
    this.averageRating = averageRating;
  }

  public String getSku() {
    return sku;
  }

  public void setSku(String sku) {
    this.sku = sku;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public String getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(String categoryId) {
    this.categoryId = categoryId;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public BigDecimal getAverageRating() {
    return averageRating;
  }

  public void setAverageRating(BigDecimal averageRating) {
    this.averageRating = averageRating;
  }
}
