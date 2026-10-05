package com.ecommerce.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.RatingSummaryDTO;
import com.ecommerce.common.dto.ReviewDTO;
import com.ecommerce.common.exception.ApiExternalException;
import com.ecommerce.common.exception.ApiInternalException;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.review.model.AggregateRating;
import com.ecommerce.review.model.Review;
import com.ecommerce.review.repository.AggregateRatingRepository;
import com.ecommerce.review.repository.ReviewRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataRetrievalFailureException;

class ReviewManagerServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-10T08:00:00Z");

  private ReviewRepository reviewRepository;
  private AggregateRatingRepository aggregateRatingRepository;
  private ReviewManagerService service;

  @BeforeEach
  void setUp() {
    reviewRepository = mock(ReviewRepository.class);
    aggregateRatingRepository = mock(AggregateRatingRepository.class);
    service =
        new ReviewManagerService(
            reviewRepository,
            aggregateRatingRepository,
            Arrays.asList("Spam", " prohibited ", "offensive", "", null),
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(reviewRepository.saveAndFlush(any(Review.class)))
        .thenAnswer(
            invocation -> {
              Review review = invocation.getArgument(0);
              review.setId(42L);
              return review;
            });
  }

  private static ReviewDTO request(String userId, String productId, Integer rating, String comment) {
    ReviewDTO dto = new ReviewDTO();
    dto.setUserId(userId);
    dto.setProductId(productId);
    dto.setRating(rating);
    dto.setComment(comment);
    return dto;
  }

  private static void assertError(Runnable call, String code, int status) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ApplicationBaseException.class)
        .satisfies(
            e -> {
              ApplicationBaseException ex = (ApplicationBaseException) e;
              assertThat(ex.getErrorCode()).isEqualTo(code);
              assertThat(ex.getHttpStatus()).isEqualTo(status);
            });
  }

  @Test
  void submitReview_validRequest_persistsTrimmedValuesAndReturnsCreatedDto() {
    when(reviewRepository.countByProductId("p1")).thenReturn(1L);
    when(reviewRepository.averageRatingByProductId("p1")).thenReturn(4.0);

    ReviewDTO result = service.submitReview(request(" u1 ", " p1 ", 4, "Great product"));

    assertThat(result.getId()).isEqualTo(42L);
    assertThat(result.getUserId()).isEqualTo("u1");
    assertThat(result.getProductId()).isEqualTo("p1");
    assertThat(result.getRating()).isEqualTo(4);
    assertThat(result.getComment()).isEqualTo("Great product");
    assertThat(result.getSubmissionDate()).isEqualTo(NOW.toString());
    assertThat(result.getStatusCode()).isEqualTo(201);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getMessage()).isEqualTo(ReviewManagerService.SUBMITTED_MESSAGE);
    verify(aggregateRatingRepository).save(any(AggregateRating.class));
  }

  @Test
  void submitReview_nullComment_isAccepted() {
    ReviewDTO result = service.submitReview(request("u1", "p1", 5, null));

    assertThat(result.getComment()).isNull();
  }

  @Test
  void submitReview_commentOfExactlyMaxLength_isAccepted() {
    String comment = "a".repeat(ReviewManagerService.MAX_COMMENT_LENGTH);

    ReviewDTO result = service.submitReview(request("u1", "p1", 3, comment));

    assertThat(result.getComment()).hasSize(ReviewManagerService.MAX_COMMENT_LENGTH);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void submitReview_blankUserId_throwsIam4002(String userId) {
    assertError(() -> service.submitReview(request(userId, "p1", 4, "ok")), "IAM_4002", 401);
  }

  @Test
  void submitReview_nullUserId_throwsIam4002() {
    assertError(() -> service.submitReview(request(null, "p1", 4, "ok")), "IAM_4002", 401);
  }

  @Test
  void submitReview_blankProductId_throwsReview4004() {
    assertError(() -> service.submitReview(request("u1", " ", 4, "ok")), "REVIEW_4004", 404);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 6, -1})
  void submitReview_ratingOutOfRange_throwsReview4003(int rating) {
    assertError(
        () -> service.submitReview(request("u1", "p1", rating, "ok")), "REVIEW_4003", 400);
    verify(reviewRepository, never()).saveAndFlush(any());
  }

  @Test
  void submitReview_nullRating_throwsReview4003() {
    assertError(() -> service.submitReview(request("u1", "p1", null, "ok")), "REVIEW_4003", 400);
  }

  @Test
  void submitReview_commentTooLong_throwsReview4005() {
    String comment = "a".repeat(ReviewManagerService.MAX_COMMENT_LENGTH + 1);

    assertError(
        () -> service.submitReview(request("u1", "p1", 4, comment)), "REVIEW_4005", 400);
  }

  @ParameterizedTest
  @ValueSource(strings = {"This is SPAM!", "totally prohibited content", "so,offensive."})
  void submitReview_prohibitedWord_throwsReview4001(String comment) {
    assertError(
        () -> service.submitReview(request("u1", "p1", 4, comment)), "REVIEW_4001", 400);
    verify(reviewRepository, never()).saveAndFlush(any());
  }

  @Test
  void submitReview_prohibitedWordInsideLongerWord_isAccepted() {
    ReviewDTO result = service.submitReview(request("u1", "p1", 4, "Not spammy at all"));

    assertThat(result.getId()).isEqualTo(42L);
  }

  @Test
  void submitReview_existingReviewForUserAndProduct_throwsReview4002() {
    when(reviewRepository.existsByUserIdAndProductId("u1", "p1")).thenReturn(true);

    assertError(() -> service.submitReview(request("u1", "p1", 4, "ok")), "REVIEW_4002", 409);
    verify(reviewRepository, never()).saveAndFlush(any());
  }

  @Test
  void submitReview_uniqueConstraintRace_throwsReview4002() {
    when(reviewRepository.saveAndFlush(any(Review.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    assertError(() -> service.submitReview(request("u1", "p1", 4, "ok")), "REVIEW_4002", 409);
  }

  @Test
  void getReviewsByProductId_mapsAllReviews() {
    Review review = new Review("u1", "p1", 5, "nice", NOW);
    review.setId(7L);
    when(reviewRepository.findByProductIdOrderBySubmissionDateDescIdDesc("p1"))
        .thenReturn(List.of(review));

    List<ReviewDTO> result = service.getReviewsByProductId("p1");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getId()).isEqualTo(7L);
    assertThat(result.get(0).getSubmissionDate()).isEqualTo(NOW.toString());
  }

  @Test
  void getReviewsByProductId_blankProductId_throwsReview4004() {
    assertError(() -> service.getReviewsByProductId(""), "REVIEW_4004", 404);
  }

  @Test
  void getReviewById_existing_returnsDto() {
    Review review = new Review("u1", "p1", 2, "meh", NOW);
    review.setId(9L);
    when(reviewRepository.findById(9L)).thenReturn(Optional.of(review));

    assertThat(service.getReviewById(9L).getRating()).isEqualTo(2);
  }

  @Test
  void getReviewById_missing_throwsReview4007() {
    when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

    assertError(() -> service.getReviewById(99L), "REVIEW_4007", 404);
  }

  @Test
  void calculateAggregateRating_noReviews_returnsZeroAndPersists() {
    when(reviewRepository.countByProductId("p1")).thenReturn(0L);

    RatingSummaryDTO result = service.calculateAggregateRating("p1");

    assertThat(result.getAverageRating()).isEqualByComparingTo("0.00");
    assertThat(result.getAverageRating().scale()).isEqualTo(2);
    assertThat(result.getReviewCount()).isZero();
    verify(reviewRepository, never()).averageRatingByProductId(any());
  }

  @Test
  void calculateAggregateRating_roundsHalfUpToTwoDecimalsAndPersists() {
    when(reviewRepository.countByProductId("p1")).thenReturn(3L);
    when(reviewRepository.averageRatingByProductId("p1")).thenReturn(14.0 / 3.0);

    RatingSummaryDTO result = service.calculateAggregateRating("p1");

    assertThat(result.getProductId()).isEqualTo("p1");
    assertThat(result.getAverageRating()).isEqualTo(new BigDecimal("4.67"));
    assertThat(result.getReviewCount()).isEqualTo(3L);
    ArgumentCaptor<AggregateRating> captor = ArgumentCaptor.forClass(AggregateRating.class);
    verify(aggregateRatingRepository).save(captor.capture());
    assertThat(captor.getValue().getAverageRating()).isEqualTo(new BigDecimal("4.67"));
    assertThat(captor.getValue().getReviewCount()).isEqualTo(3L);
  }

  @Test
  void calculateAggregateRating_repositoryFailure_throwsInternalReview5001() {
    when(reviewRepository.countByProductId("p1"))
        .thenThrow(new DataRetrievalFailureException("db down"));

    assertThatThrownBy(() -> service.calculateAggregateRating("p1"))
        .isInstanceOf(ApiInternalException.class)
        .hasFieldOrPropertyWithValue("errorCode", "REVIEW_5001")
        .hasCauseInstanceOf(DataRetrievalFailureException.class);
  }

  @Test
  void calculateAggregateRating_blankProductId_throwsExternalReview4004() {
    assertThatThrownBy(() -> service.calculateAggregateRating(null))
        .isInstanceOf(ApiExternalException.class)
        .hasFieldOrPropertyWithValue("errorCode", "REVIEW_4004");
  }

  @Test
  void containsProhibitedWord_emptyConfiguredList_neverMatches() {
    ReviewManagerService permissive =
        new ReviewManagerService(
            reviewRepository, aggregateRatingRepository, List.of(), Clock.systemUTC());

    assertThat(permissive.containsProhibitedWord("spam spam spam")).isFalse();
  }
}
