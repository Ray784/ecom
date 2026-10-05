package com.ecommerce.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.common.util.JwtClaims;
import com.ecommerce.common.util.JwtUtil;
import com.ecommerce.gateway.testsupport.FakeDownstream;
import com.ecommerce.gateway.testsupport.FakeDownstream.Recorded;
import com.ecommerce.gateway.testsupport.FakeDownstream.Reply;
import java.net.URI;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End to end gateway tests through MockMvc against a fake downstream HTTP server that plays all
 * services, including an IAM validate endpoint backed by the shared JwtUtil.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GatewayControllerTest {

  private static final FakeDownstream DOWNSTREAM = new FakeDownstream();
  private static final JwtUtil JWT = new JwtUtil();
  private static final String CUSTOMER_TOKEN =
      JWT.generateToken("cust-1", "carol", List.of("ROLE_CUSTOMER"), null);
  private static final String ADMIN_TOKEN =
      JWT.generateToken("admin-1", "adam", List.of("ROLE_ADMIN"), null);

  @Autowired private MockMvc mockMvc;

  @DynamicPropertySource
  static void downstreamUrls(DynamicPropertyRegistry registry) {
    for (String service :
        List.of("iam", "user", "product", "order", "payment", "invoice", "review", "search")) {
      registry.add("gateway.services." + service, DOWNSTREAM::baseUrl);
    }
    registry.add("gateway.services.notification", () -> "http://localhost:1");
  }

  @AfterAll
  static void stopDownstream() {
    DOWNSTREAM.close();
  }

  @BeforeEach
  void setUp() {
    DOWNSTREAM.reset();
    DOWNSTREAM.on("/auth/validate", GatewayControllerTest::validateWithJwtUtil);
  }

  private static Reply validateWithJwtUtil(Recorded request) {
    try {
      JwtClaims claims = JWT.parseToken(request.header("Authorization"));
      String roles = String.join("\",\"", claims.roles());
      return Reply.json(
          200,
          "{\"userId\":\""
              + claims.userId()
              + "\",\"username\":\""
              + claims.username()
              + "\",\"roles\":[\""
              + roles
              + "\"],\"valid\":true}");
    } catch (ApplicationBaseException e) {
      return Reply.json(401, "{\"httpStatusCode\":401,\"errorCode\":\"IAM_4002\"}");
    }
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  @Test
  void publicLogin_isForwardedWithBodyAndWithoutAuthentication() throws Exception {
    DOWNSTREAM.on("/auth/login", r -> Reply.json(200, "{\"jwtToken\":\"t\"}"));

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"carol\",\"password\":\"pw\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jwtToken").value("t"))
        .andExpect(header().string("X-Downstream", "fake"));

    Recorded forwarded = DOWNSTREAM.last("/auth/login");
    assertThat(forwarded.method()).isEqualTo("POST");
    assertThat(forwarded.body()).isEqualTo("{\"username\":\"carol\",\"password\":\"pw\"}");
    assertThat(forwarded.header("Content-Type")).startsWith("application/json");
    assertThat(DOWNSTREAM.last("/auth/validate")).isNull();
  }

  @Test
  void publicSearch_preservesQueryStringAndStripsSpoofedUserHeaders() throws Exception {
    mockMvc
        .perform(
            get(URI.create("/search/products?q=red%20shoes&sort=price"))
                .header("X-User-Id", "spoofed")
                .header("X-User-Roles", "ROLE_ADMIN"))
        .andExpect(status().isOk());

    Recorded forwarded = DOWNSTREAM.last("/search/products");
    assertThat(forwarded.uri()).isEqualTo("/search/products?q=red%20shoes&sort=price");
    assertThat(forwarded.header("X-User-Id")).isNull();
    assertThat(forwarded.header("X-User-Roles")).isNull();
  }

  @Test
  void publicReviewRating_isForwardedToReviewService() throws Exception {
    DOWNSTREAM.on("/reviews/products", r -> Reply.json(200, "{\"reviewCount\":3}"));

    mockMvc
        .perform(get("/reviews/products/p-1/rating"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewCount").value(3));
  }

  @Test
  void protectedRoute_withoutToken_returns401Iam4002() throws Exception {
    mockMvc
        .perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("IAM_4002"))
        .andExpect(jsonPath("$.httpStatusCode").value(401));

    assertThat(DOWNSTREAM.last("/orders")).isNull();
  }

  @Test
  void protectedRoute_withInvalidToken_returns401Iam4002() throws Exception {
    mockMvc
        .perform(get("/carts").header(HttpHeaders.AUTHORIZATION, bearer("garbage.token.value")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("IAM_4002"));

    assertThat(DOWNSTREAM.last("/carts")).isNull();
  }

  @Test
  void customerRoute_withCustomerToken_forwardsIdentityHeaders() throws Exception {
    DOWNSTREAM.on("/orders", r -> Reply.json(201, "{\"orderId\":\"o-1\"}"));

    mockMvc
        .perform(
            post("/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_TOKEN))
                .header("X-User-Id", "spoofed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.orderId").value("o-1"));

    Recorded forwarded = DOWNSTREAM.last("/orders");
    assertThat(forwarded.header("X-User-Id")).isEqualTo("cust-1");
    assertThat(forwarded.header("X-User-Name")).isEqualTo("carol");
    assertThat(forwarded.header("X-User-Roles")).isEqualTo("ROLE_CUSTOMER");
    assertThat(forwarded.header("Authorization")).isEqualTo(bearer(CUSTOMER_TOKEN));
    assertThat(forwarded.body()).isEqualTo("{\"items\":[]}");
    assertThat(DOWNSTREAM.last("/auth/validate")).isNotNull();
  }

  @Test
  void customerRoutes_coverProfilesInvoicesAndReviews() throws Exception {
    mockMvc
        .perform(delete("/profiles/cust-1/addresses/a-1")
            .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_TOKEN)))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/invoices/o-1").header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_TOKEN)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/reviews")
                .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":\"p-1\",\"rating\":5}"))
        .andExpect(status().isOk());

    assertThat(DOWNSTREAM.last("/profiles/cust-1/addresses/a-1").method()).isEqualTo("DELETE");
    assertThat(DOWNSTREAM.last("/reviews").header("X-User-Id")).isEqualTo("cust-1");
  }

  @Test
  void adminRoute_withCustomerToken_returns403Iam4008() throws Exception {
    mockMvc
        .perform(
            post("/products")
                .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("IAM_4008"))
        .andExpect(
            jsonPath("$.errorDetails")
                .value("User carol does not possess the required role: ROLE_ADMIN."));

    assertThat(DOWNSTREAM.last("/products")).isNull();
  }

  @Test
  void customerRoute_withAdminToken_returns403Iam4008() throws Exception {
    mockMvc
        .perform(get("/orders/o-1").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("IAM_4008"));
  }

  @Test
  void adminRoute_withAdminToken_isForwarded() throws Exception {
    mockMvc
        .perform(
            put("/products/p-1")
                .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\"}"))
        .andExpect(status().isOk());

    Recorded forwarded = DOWNSTREAM.last("/products/p-1");
    assertThat(forwarded.method()).isEqualTo("PUT");
    assertThat(forwarded.header("X-User-Roles")).isEqualTo("ROLE_ADMIN");
  }

  @Test
  void downstreamErrorResponse_isRelayedUnchanged() throws Exception {
    String error =
        "{\"httpStatusCode\":409,\"errorCode\":\"REVIEW_4002\",\"errorDetails\":\"dup\","
            + "\"timestamp\":\"2026-10-10T00:00:00Z\"}";
    DOWNSTREAM.on("/reviews", r -> Reply.json(409, error));

    mockMvc
        .perform(
            post("/reviews")
                .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isConflict())
        .andExpect(content().json(error, true));
  }

  @Test
  void downstreamServerError_isRelayedUnchanged() throws Exception {
    DOWNSTREAM.on("/products", r -> Reply.json(500, "{\"errorCode\":\"PROD_5001\"}"));

    mockMvc
        .perform(get("/products/p-1"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.errorCode").value("PROD_5001"));
  }

  @Test
  void paymentsRoute_anyAuthenticatedUser_isForwarded() throws Exception {
    mockMvc
        .perform(get("/payments/order/o-1").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
        .andExpect(status().isOk());
  }

  @Test
  void unreachableDownstream_returns503ErrorResponse() throws Exception {
    mockMvc
        .perform(
            get("/notifications/user/cust-1")
                .header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_TOKEN)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("HTTP_503"))
        .andExpect(jsonPath("$.errorDetails").value("Downstream service notification is unavailable."));
  }

  @Test
  void unknownRoute_returns404ErrorResponse() throws Exception {
    mockMvc
        .perform(get("/does/not/exist"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("HTTP_404"))
        .andExpect(jsonPath("$.errorDetails").value("No gateway route for GET /does/not/exist."));
  }

  @Test
  void iamDown_fallsBackToLocalJwtValidation() throws Exception {
    Function<Recorded, Reply> down = r -> Reply.json(500, "{}");
    DOWNSTREAM.on("/auth/validate", down);

    mockMvc
        .perform(get("/carts").header(HttpHeaders.AUTHORIZATION, bearer(CUSTOMER_TOKEN)))
        .andExpect(status().isOk());
    assertThat(DOWNSTREAM.last("/carts").header("X-User-Id")).isEqualTo("cust-1");

    mockMvc
        .perform(get("/carts").header(HttpHeaders.AUTHORIZATION, bearer("bad.token.here")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("IAM_4002"));
  }
}
