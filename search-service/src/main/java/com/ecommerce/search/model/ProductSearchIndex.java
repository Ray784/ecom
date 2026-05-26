package com.ecommerce.search.model;

import java.math.BigDecimal;
import java.util.List;

/** Denormalized search document for one product, keyed by SKU. */
public class ProductSearchIndex {

  private String productId;
  private String name;
  private String description;
  private BigDecimal price;
  private String categoryId;
  private String imageUrl;
  private List<String> keywords;

  public ProductSearchIndex() {}

  public ProductSearchIndex(
      String productId,
      String name,
      String description,
      BigDecimal price,
      String categoryId,
      String imageUrl,
      List<String> keywords) {
    this.productId = productId;
    this.name = name;
    this.description = description;
    this.price = price;
    this.categoryId = categoryId;
    this.imageUrl = imageUrl;
    this.keywords = keywords == null ? List.of() : List.copyOf(keywords);
  }

  public String getProductId() {
    return productId;
  }

  public void setProductId(String productId) {
    this.productId = productId;
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

  public List<String> getKeywords() {
    return keywords;
  }

  public void setKeywords(List<String> keywords) {
    this.keywords = keywords == null ? List.of() : List.copyOf(keywords);
  }
}
