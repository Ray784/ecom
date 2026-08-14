package com.ecommerce.review.repository;

import com.ecommerce.review.model.AggregateRating;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for cached aggregate ratings keyed by product ID. */
public interface AggregateRatingRepository extends JpaRepository<AggregateRating, String> {}
