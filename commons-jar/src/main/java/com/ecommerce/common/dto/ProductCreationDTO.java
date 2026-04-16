package com.ecommerce.common.dto;

import java.math.BigDecimal;
import java.util.Objects;

/** Data required to create a new catalog item. */
public class ProductCreationDTO {

  private String sku;
  private String name;
  private String description;
  private BigDecimal price;
  private String categoryId;
  private Integer initialStock;
  private String imageUrl;

  /** Creates an empty instance for JSON deserialization. */
  public ProductCreationDTO() {}

  /** Creates an instance with all fields populated. */
  public ProductCreationDTO(String sku, String name, String description, BigDecimal price, String categoryId, Integer initialStock, String imageUrl) {
    this.sku = sku;
    this.name = name;
    this.description = description;
    this.price = price;
    this.categoryId = categoryId;
    this.initialStock = initialStock;
    this.imageUrl = imageUrl;
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

  public Integer getInitialStock() {
    return initialStock;
  }

  public void setInitialStock(Integer initialStock) {
    this.initialStock = initialStock;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProductCreationDTO that = (ProductCreationDTO) o;
    return Objects.equals(sku, that.sku)
        && Objects.equals(name, that.name)
        && Objects.equals(description, that.description)
        && Objects.equals(price, that.price)
        && Objects.equals(categoryId, that.categoryId)
        && Objects.equals(initialStock, that.initialStock)
        && Objects.equals(imageUrl, that.imageUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sku, name, description, price, categoryId, initialStock, imageUrl);
  }

  @Override
  public String toString() {
    return "ProductCreationDTO{" + "sku=" + sku
        + ", name=" + name
        + ", description=" + description
        + ", price=" + price
        + ", categoryId=" + categoryId
        + ", initialStock=" + initialStock
        + ", imageUrl=" + imageUrl + "}";
  }
}
