package com.ecommerce.common.dto;

import java.math.BigDecimal;
import java.util.Objects;

/** Full product information including stock levels and rating. */
public class ProductDetailsDTO extends BaseResponseDTO {

  private String sku;
  private String name;
  private String description;
  private BigDecimal price;
  private String categoryId;
  private Integer currentStock;
  private Integer reservedStock;
  private BigDecimal averageRating;

  /** Creates an empty instance for JSON deserialization. */
  public ProductDetailsDTO() {}

  /** Creates a successful response with status 200 carrying the given payload fields. */
  public ProductDetailsDTO(String sku, String name, String description, BigDecimal price, String categoryId, Integer currentStock, Integer reservedStock, BigDecimal averageRating) {
    super(true, 200, "Success");
    this.sku = sku;
    this.name = name;
    this.description = description;
    this.price = price;
    this.categoryId = categoryId;
    this.currentStock = currentStock;
    this.reservedStock = reservedStock;
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

  public BigDecimal getAverageRating() {
    return averageRating;
  }

  public void setAverageRating(BigDecimal averageRating) {
    this.averageRating = averageRating;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    ProductDetailsDTO that = (ProductDetailsDTO) o;
    return Objects.equals(sku, that.sku)
        && Objects.equals(name, that.name)
        && Objects.equals(description, that.description)
        && Objects.equals(price, that.price)
        && Objects.equals(categoryId, that.categoryId)
        && Objects.equals(currentStock, that.currentStock)
        && Objects.equals(reservedStock, that.reservedStock)
        && Objects.equals(averageRating, that.averageRating);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sku, name, description, price, categoryId, currentStock, reservedStock, averageRating);
  }

  @Override
  public String toString() {
    return "ProductDetailsDTO{" + "sku=" + sku
        + ", name=" + name
        + ", description=" + description
        + ", price=" + price
        + ", categoryId=" + categoryId
        + ", currentStock=" + currentStock
        + ", reservedStock=" + reservedStock
        + ", averageRating=" + averageRating
        + ", " + super.toString() + "}";
  }
}
