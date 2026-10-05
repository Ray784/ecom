package com.ecommerce.review.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Full stack tests against the H2 database; each test uses its own product ID. */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private ResultActions submit(String headerUserId, String body) throws Exception {
    var builder = post("/reviews").contentType(MediaType.APPLICATION_JSON).content(body);
    if (headerUserId != null) {
      builder.header(ReviewController.USER_ID_HEADER, headerUserId);
    }
    return mockMvc.perform(builder);
  }

  private static String body(String userId, String productId, Integer rating, String comment) {
    StringBuilder json = new StringBuilder("{\"productId\":\"").append(productId).append('"');
    if (userId != null) {
      json.append(",\"userId\":\"").append(userId).append('"');
    }
    if (rating != null) {
      json.append(",\"rating\":").append(rating);
    }
    if (comment != null) {
      json.append(",\"comment\":\"").append(comment).append('"');
    }
    return json.append('}').toString();
  }

  @Test
  void postReview_withHeaderUser_returns201AndHeaderOverridesBody() throws Exception {
    submit("header-user", body("body-user", "c-p1", 5, "Excellent"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.userId").value("header-user"))
        .andExpect(jsonPath("$.productId").value("c-p1"))
        .andExpect(jsonPath("$.rating").value(5))
        .andExpect(jsonPath("$.statusCode").value(201))
        .andExpect(jsonPath("$.submissionDate").isString());
  }

  @Test
  void postReview_userIdOnlyInBody_isAccepted() throws Exception {
    submit(null, body("body-user", "c-p2", 4, "Good"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value("body-user"));
  }

  @Test
  void postReview_withoutAnyUser_returns401Iam4002() throws Exception {
    submit(null, body(null, "c-p3", 4, "Good"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("IAM_4002"))
        .andExpect(jsonPath("$.httpStatusCode").value(401));
  }

  @Test
  void postReview_invalidRating_returns400Review4003() throws Exception {
    submit("u1", body(null, "c-p4", 9, "Good"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("REVIEW_4003"))
        .andExpect(jsonPath("$.errorDetails").value("Rating value 9 is outside the allowed range (1 to 5)."));
  }

  @Test
  void postReview_prohibitedLanguage_returns400Review4001() throws Exception {
    submit("u1", body(null, "c-p5", 3, "buy spam here"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("REVIEW_4001"));
  }

  @Test
  void postReview_commentTooLong_returns400Review4005() throws Exception {
    submit("u1", body(null, "c-p6", 3, "x".repeat(1001)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("REVIEW_4005"));
  }

  @Test
  void postReview_duplicate_returns409Review4002() throws Exception {
    submit("dup-user", body(null, "c-p7", 3, "first")).andExpect(status().isCreated());

    submit("dup-user", body(null, "c-p7", 4, "second"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("REVIEW_4002"));
  }

  @Test
  void postReview_malformedJson_returns400BadRequest() throws Exception {
    submit("u1", "{not json")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
  }

  @Test
  void getReviewsAndRating_afterSubmissions_returnListAndAggregate() throws Exception {
    submit("u1", body(null, "c-p8", 5, "a")).andExpect(status().isCreated());
    submit("u2", body(null, "c-p8", 4, "b")).andExpect(status().isCreated());
    submit("u3", body(null, "c-p8", 4, "c")).andExpect(status().isCreated());

    mockMvc
        .perform(get("/reviews/products/c-p8"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)));
    mockMvc
        .perform(get("/reviews/products/c-p8/rating"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productId").value("c-p8"))
        .andExpect(jsonPath("$.averageRating").value(4.33))
        .andExpect(jsonPath("$.reviewCount").value(3));
  }

  @Test
  void getRating_productWithoutReviews_returnsZero() throws Exception {
    mockMvc
        .perform(get("/reviews/products/c-none/rating"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.averageRating").value(0.0))
        .andExpect(jsonPath("$.reviewCount").value(0));
    mockMvc
        .perform(get("/reviews/products/c-none"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void getReviewById_existingAndMissing() throws Exception {
    String created =
        submit("u1", body(null, "c-p9", 2, "meh"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode node = objectMapper.readTree(created);

    mockMvc
        .perform(get("/reviews/" + node.get("id").asLong()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productId").value("c-p9"));
    mockMvc
        .perform(get("/reviews/999999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("REVIEW_4007"));
  }

  @Test
  void getReviewById_nonNumericId_returns400() throws Exception {
    mockMvc
        .perform(get("/reviews/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
  }
}
