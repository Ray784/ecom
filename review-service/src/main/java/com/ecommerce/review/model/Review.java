package com.ecommerce.review.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * A single customer review of a product.
 *
 * <p>The unique constraint on user and product backs up the duplicate review check performed by
 * ReviewManagerService so concurrent submissions cannot create two reviews.
 */
@Entity
@Table(
    name = "reviews",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_reviews_user_product",
            columnNames = {"user_id", "product_id"}))
public class Review {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, length = 128)
  private String userId;

  @Column(name = "product_id", nullable = false, length = 128)
  private String productId;

  @Column(nullable = false)
  private Integer rating;

  @Column(length = 1000)
  private String comment;

  @Column(name = "submission_date", nullable = false)
  private Instant submissionDate;

  /** Required by JPA. */
  protected Review() {}

  public Review(
      String userId, String productId, Integer rating, String comment, Instant submissionDate) {
    this.userId = userId;
    this.productId = productId;
    this.rating = rating;
    this.comment = comment;
    this.submissionDate = submissionDate;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUserId() {
    return userId;
  }

  public String getProductId() {
    return productId;
  }

  public Integer getRating() {
    return rating;
  }

  public String getComment() {
    return comment;
  }

  public Instant getSubmissionDate() {
    return submissionDate;
  }
}
