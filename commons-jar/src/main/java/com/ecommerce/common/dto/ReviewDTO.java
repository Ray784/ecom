package com.ecommerce.common.dto;

import java.util.Objects;

/** Product review submitted by or shown to a customer. */
public class ReviewDTO extends BaseResponseDTO {

  private Long id;
  private String productId;
  private Integer rating;
  private String comment;
  private String userId;
  private String submissionDate;

  /** Creates an empty instance for JSON deserialization. */
  public ReviewDTO() {}

  /** Creates a successful response with status 200 carrying the given payload fields. */
  public ReviewDTO(Long id, String productId, Integer rating, String comment, String userId, String submissionDate) {
    super(true, 200, "Success");
    this.id = id;
    this.productId = productId;
    this.rating = rating;
    this.comment = comment;
    this.userId = userId;
    this.submissionDate = submissionDate;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getProductId() {
    return productId;
  }

  public void setProductId(String productId) {
    this.productId = productId;
  }

  public Integer getRating() {
    return rating;
  }

  public void setRating(Integer rating) {
    this.rating = rating;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getSubmissionDate() {
    return submissionDate;
  }

  public void setSubmissionDate(String submissionDate) {
    this.submissionDate = submissionDate;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    ReviewDTO that = (ReviewDTO) o;
    return Objects.equals(id, that.id)
        && Objects.equals(productId, that.productId)
        && Objects.equals(rating, that.rating)
        && Objects.equals(comment, that.comment)
        && Objects.equals(userId, that.userId)
        && Objects.equals(submissionDate, that.submissionDate);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), id, productId, rating, comment, userId, submissionDate);
  }

  @Override
  public String toString() {
    return "ReviewDTO{" + "id=" + id
        + ", productId=" + productId
        + ", rating=" + rating
        + ", comment=" + comment
        + ", userId=" + userId
        + ", submissionDate=" + submissionDate
        + ", " + super.toString() + "}";
  }
}
