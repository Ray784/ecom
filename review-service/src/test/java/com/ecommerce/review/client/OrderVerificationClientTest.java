package com.ecommerce.review.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class OrderVerificationClientTest {

  private static final String URL = "http://order/orders/verified-purchase?userId=u1&sku=p1";

  private MockRestServiceServer server;
  private OrderVerificationClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://order");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new OrderVerificationClient(builder.build());
  }

  @Test
  void isVerifiedPurchaser_verifiedTrue_returnsTrue() {
    server.expect(requestTo(URL))
        .andRespond(
            withSuccess(
                "{\"userId\":\"u1\",\"sku\":\"p1\",\"verified\":true}",
                MediaType.APPLICATION_JSON));

    assertThat(client.isVerifiedPurchaser("u1", "p1")).isTrue();
    server.verify();
  }

  @Test
  void isVerifiedPurchaser_verifiedFalse_returnsFalse() {
    server.expect(requestTo(URL))
        .andRespond(withSuccess("{\"verified\":false}", MediaType.APPLICATION_JSON));

    assertThat(client.isVerifiedPurchaser("u1", "p1")).isFalse();
  }

  @Test
  void isVerifiedPurchaser_missingOrNonBooleanFlag_returnsFalse() {
    server.expect(requestTo(URL))
        .andRespond(withSuccess("{\"verified\":\"yes\"}", MediaType.APPLICATION_JSON));
    server.expect(requestTo(URL)).andRespond(withSuccess());

    assertThat(client.isVerifiedPurchaser("u1", "p1")).isFalse();
    assertThat(client.isVerifiedPurchaser("u1", "p1")).isFalse();
  }

  @Test
  void isVerifiedPurchaser_serverError_propagates() {
    server.expect(requestTo(URL)).andRespond(withServerError());

    assertThatThrownBy(() -> client.isVerifiedPurchaser("u1", "p1"))
        .isInstanceOf(HttpServerErrorException.class);
  }
}
