package com.ecommerce.review.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Cached average rating and review count of a product, refreshed on every new review. */
@Entity
@Table(name = "aggregate_ratings")
public class AggregateRating {

  @Id
  @Column(name = "product_id", length = 128)
  private String productId;

  @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
  private BigDecimal averageRating;

  @Column(name = "review_count", nullable = false)
  private Long reviewCount;

  /** Required by JPA. */
  protected AggregateRating() {}

  public AggregateRating(String productId, BigDecimal averageRating, Long reviewCount) {
    this.productId = productId;
    this.averageRating = averageRating;
    this.reviewCount = reviewCount;
  }

  public String getProductId() {
    return productId;
  }

  public BigDecimal getAverageRating() {
    return averageRating;
  }

  public Long getReviewCount() {
    return reviewCount;
  }
}
