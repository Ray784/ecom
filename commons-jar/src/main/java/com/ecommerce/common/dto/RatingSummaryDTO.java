package com.ecommerce.common.dto;

import java.math.BigDecimal;
import java.util.Objects;

/** Aggregated rating statistics for a product. */
public class RatingSummaryDTO extends BaseResponseDTO {

  private String productId;
  private BigDecimal averageRating;
  private Long reviewCount;

  /** Creates an empty instance for JSON deserialization. */
  public RatingSummaryDTO() {}

  /** Creates a successful response with status 200 carrying the given payload fields. */
  public RatingSummaryDTO(String productId, BigDecimal averageRating, Long reviewCount) {
    super(true, 200, "Success");
    this.productId = productId;
    this.averageRating = averageRating;
    this.reviewCount = reviewCount;
  }

  public String getProductId() {
    return productId;
  }

  public void setProductId(String productId) {
    this.productId = productId;
  }

  public BigDecimal getAverageRating() {
    return averageRating;
  }

  public void setAverageRating(BigDecimal averageRating) {
    this.averageRating = averageRating;
  }

  public Long getReviewCount() {
    return reviewCount;
  }

  public void setReviewCount(Long reviewCount) {
    this.reviewCount = reviewCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    RatingSummaryDTO that = (RatingSummaryDTO) o;
    return Objects.equals(productId, that.productId)
        && Objects.equals(averageRating, that.averageRating)
        && Objects.equals(reviewCount, that.reviewCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), productId, averageRating, reviewCount);
  }

  @Override
  public String toString() {
    return "RatingSummaryDTO{" + "productId=" + productId
        + ", averageRating=" + averageRating
        + ", reviewCount=" + reviewCount
        + ", " + super.toString() + "}";
  }
}
