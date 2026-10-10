package com.ecommerce.review.service;

import com.ecommerce.common.dto.RatingSummaryDTO;
import com.ecommerce.common.dto.ReviewDTO;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.review.client.OrderVerificationClient;
import com.ecommerce.review.client.ProductCatalogClient;
import com.ecommerce.review.model.AggregateRating;
import com.ecommerce.review.model.Review;
import com.ecommerce.review.repository.AggregateRatingRepository;
import com.ecommerce.review.repository.ReviewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles review submission, moderation and aggregate rating calculation.
 *
 * <p>Validation runs in a fixed order so the reported error is deterministic: caller identity,
 * product ID, rating range, comment length, prohibited language, product existence in
 * product-service, verified purchase in order-service and finally duplicates. The two remote
 * checks are toggled by review.validation.product-check-enabled and
 * review.validation.purchase-check-enabled.
 */
@Service
public class ReviewManagerService {

  public static final int MIN_RATING = 1;
  public static final int MAX_RATING = 5;
  public static final int MAX_COMMENT_LENGTH = 1000;
  public static final String SUBMITTED_MESSAGE = "Review submitted successfully";

  private static final int RATING_SCALE = 2;
  private static final Pattern WORD_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");

  private final ReviewRepository reviewRepository;
  private final AggregateRatingRepository aggregateRatingRepository;
  private final Set<String> prohibitedWords;
  private final Clock clock;

  /** Null when the product existence check is disabled. */
  private final ProductCatalogClient productCatalogClient;

  /** Null when the verified purchase check is disabled. */
  private final OrderVerificationClient orderVerificationClient;

  @Autowired
  public ReviewManagerService(
      ReviewRepository reviewRepository,
      AggregateRatingRepository aggregateRatingRepository,
      @Value("${review.prohibited-words:spam,prohibited,offensive}") List<String> prohibitedWords,
      ProductCatalogClient productCatalogClient,
      OrderVerificationClient orderVerificationClient,
      @Value("${review.validation.product-check-enabled:true}") boolean productCheckEnabled,
      @Value("${review.validation.purchase-check-enabled:true}") boolean purchaseCheckEnabled) {
    this(
        reviewRepository,
        aggregateRatingRepository,
        prohibitedWords,
        Clock.systemUTC(),
        productCheckEnabled ? productCatalogClient : null,
        purchaseCheckEnabled ? orderVerificationClient : null);
  }

  /** Creates a service with both remote checks disabled, for tests that only need local rules. */
  ReviewManagerService(
      ReviewRepository reviewRepository,
      AggregateRatingRepository aggregateRatingRepository,
      List<String> prohibitedWords,
      Clock clock) {
    this(reviewRepository, aggregateRatingRepository, prohibitedWords, clock, null, null);
  }

  /** Full constructor. A null client disables the matching remote check. */
  ReviewManagerService(
      ReviewRepository reviewRepository,
      AggregateRatingRepository aggregateRatingRepository,
      List<String> prohibitedWords,
      Clock clock,
      ProductCatalogClient productCatalogClient,
      OrderVerificationClient orderVerificationClient) {
    this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository");
    this.aggregateRatingRepository =
        Objects.requireNonNull(aggregateRatingRepository, "aggregateRatingRepository");
    this.prohibitedWords =
        prohibitedWords.stream()
            .filter(Objects::nonNull)
            .map(word -> word.trim().toLowerCase(Locale.ROOT))
            .filter(word -> !word.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    this.clock = Objects.requireNonNull(clock, "clock");
    this.productCatalogClient = productCatalogClient;
    this.orderVerificationClient = orderVerificationClient;
  }

  /**
   * Validates and stores a review, then refreshes the aggregate rating of its product.
   *
   * @throws ApplicationBaseException with IAM_4002 when no user ID is present, REVIEW_4004 for a
   *     blank product ID or a product unknown to product-service, REVIEW_4003 for an out of range
   *     rating, REVIEW_4005 for an overlong comment, REVIEW_4001 for prohibited language,
   *     REVIEW_4006 when the user has no paid order for the product, REVIEW_4002 for a duplicate
   *     review and REVIEW_5001 when the aggregate rating cannot be refreshed
   */
  @Transactional
  public ReviewDTO submitReview(ReviewDTO request) {
    Objects.requireNonNull(request, "request");
    String userId = trimToNull(request.getUserId());
    if (userId == null) {
      throw CodedErrorFactory.create(MappedError.IAM_4002);
    }
    String productId = requireProductId(request.getProductId());
    Integer rating = request.getRating();
    if (rating == null || rating < MIN_RATING || rating > MAX_RATING) {
      throw CodedErrorFactory.create(MappedError.REVIEW_4003, rating);
    }
    String comment = request.getComment();
    if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
      throw CodedErrorFactory.create(MappedError.REVIEW_4005, MAX_COMMENT_LENGTH);
    }
    if (containsProhibitedWord(comment)) {
      throw CodedErrorFactory.create(MappedError.REVIEW_4001);
    }
    if (productCatalogClient != null && !productCatalogClient.productExists(productId)) {
      throw CodedErrorFactory.create(MappedError.REVIEW_4004, productId);
    }
    if (orderVerificationClient != null
        && !orderVerificationClient.isVerifiedPurchaser(userId, productId)) {
      throw CodedErrorFactory.create(MappedError.REVIEW_4006, userId, productId);
    }
    if (reviewRepository.existsByUserIdAndProductId(userId, productId)) {
      throw CodedErrorFactory.create(MappedError.REVIEW_4002, userId, productId);
    }
    Review saved;
    try {
      saved =
          reviewRepository.saveAndFlush(
              new Review(userId, productId, rating, comment, clock.instant()));
    } catch (DataIntegrityViolationException e) {
      throw CodedErrorFactory.create(MappedError.REVIEW_4002, e, userId, productId);
    }
    calculateAggregateRating(productId);
    ReviewDTO response = toDto(saved);
    response.setStatusCode(201);
    response.setMessage(SUBMITTED_MESSAGE);
    return response;
  }

  /** Returns all reviews of a product, newest first; an unknown product yields an empty list. */
  @Transactional(readOnly = true)
  public List<ReviewDTO> getReviewsByProductId(String productId) {
    String id = requireProductId(productId);
    return reviewRepository.findByProductIdOrderBySubmissionDateDescIdDesc(id).stream()
        .map(ReviewManagerService::toDto)
        .toList();
  }

  /**
   * Returns a single review.
   *
   * @throws ApplicationBaseException with REVIEW_4007 if no review has the given ID
   */
  @Transactional(readOnly = true)
  public ReviewDTO getReviewById(Long id) {
    return reviewRepository
        .findById(id)
        .map(ReviewManagerService::toDto)
        .orElseThrow(() -> CodedErrorFactory.create(MappedError.REVIEW_4007, id));
  }

  /**
   * Recomputes and stores the average rating and review count of a product.
   *
   * <p>A product without reviews reports an average of 0.00 and a count of 0.
   *
   * @throws ApplicationBaseException with REVIEW_4004 for a blank product ID or REVIEW_5001 when
   *     the calculation or persistence fails
   */
  @Transactional
  public RatingSummaryDTO calculateAggregateRating(String productId) {
    String id = requireProductId(productId);
    try {
      long count = reviewRepository.countByProductId(id);
      Double average = count == 0 ? null : reviewRepository.averageRatingByProductId(id);
      BigDecimal averageRating =
          average == null
              ? BigDecimal.ZERO.setScale(RATING_SCALE)
              : BigDecimal.valueOf(average).setScale(RATING_SCALE, RoundingMode.HALF_UP);
      aggregateRatingRepository.save(new AggregateRating(id, averageRating, count));
      return new RatingSummaryDTO(id, averageRating, count);
    } catch (ApplicationBaseException e) {
      throw e;
    } catch (RuntimeException e) {
      throw CodedErrorFactory.create(MappedError.REVIEW_5001, e, id);
    }
  }

  boolean containsProhibitedWord(String text) {
    if (text == null || text.isBlank() || prohibitedWords.isEmpty()) {
      return false;
    }
    for (String word : WORD_SEPARATOR.split(text.toLowerCase(Locale.ROOT))) {
      if (prohibitedWords.contains(word)) {
        return true;
      }
    }
    return false;
  }

  private static String requireProductId(String productId) {
    String id = trimToNull(productId);
    if (id == null) {
      throw CodedErrorFactory.create(MappedError.REVIEW_4004, productId);
    }
    return id;
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static ReviewDTO toDto(Review review) {
    return new ReviewDTO(
        review.getId(),
        review.getProductId(),
        review.getRating(),
        review.getComment(),
        review.getUserId(),
        review.getSubmissionDate() == null ? null : review.getSubmissionDate().toString());
  }
}
