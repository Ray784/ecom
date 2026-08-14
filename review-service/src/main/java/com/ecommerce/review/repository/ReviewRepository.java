package com.ecommerce.review.repository;

import com.ecommerce.review.model.Review;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence access for reviews. */
public interface ReviewRepository extends JpaRepository<Review, Long> {

  boolean existsByUserIdAndProductId(String userId, String productId);

  List<Review> findByProductIdOrderBySubmissionDateDescIdDesc(String productId);

  long countByProductId(String productId);

  /** Returns the mean rating of a product, or null when it has no reviews. */
  @Query("select avg(r.rating) from Review r where r.productId = :productId")
  Double averageRatingByProductId(@Param("productId") String productId);
}
