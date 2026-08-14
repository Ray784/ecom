package com.ecommerce.review.controller;

import com.ecommerce.common.dto.RatingSummaryDTO;
import com.ecommerce.common.dto.ReviewDTO;
import com.ecommerce.review.service.ReviewManagerService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints of the review service. Errors are rendered by the commons GlobalExceptionHandler. */
@RestController
@RequestMapping("/reviews")
public class ReviewController {

  /** Header set by the API gateway with the authenticated user ID. */
  public static final String USER_ID_HEADER = "X-User-Id";

  private final ReviewManagerService reviewManagerService;

  public ReviewController(ReviewManagerService reviewManagerService) {
    this.reviewManagerService = reviewManagerService;
  }

  /**
   * Submits a review. The gateway supplied X-User-Id header takes precedence over any userId in
   * the body so a client cannot post reviews on behalf of another user.
   */
  @PostMapping
  public ResponseEntity<ReviewDTO> submitReview(
      @RequestHeader(value = USER_ID_HEADER, required = false) String headerUserId,
      @RequestBody ReviewDTO request) {
    if (headerUserId != null && !headerUserId.isBlank()) {
      request.setUserId(headerUserId.trim());
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(reviewManagerService.submitReview(request));
  }

  @GetMapping("/products/{productId}")
  public List<ReviewDTO> getReviewsByProduct(@PathVariable String productId) {
    return reviewManagerService.getReviewsByProductId(productId);
  }

  @GetMapping("/products/{productId}/rating")
  public RatingSummaryDTO getRating(@PathVariable String productId) {
    return reviewManagerService.calculateAggregateRating(productId);
  }

  @GetMapping("/{id}")
  public ReviewDTO getReview(@PathVariable Long id) {
    return reviewManagerService.getReviewById(id);
  }
}
