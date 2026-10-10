package com.ecommerce.review.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class ProductCatalogClientTest {

  private MockRestServiceServer server;
  private ProductCatalogClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://product");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new ProductCatalogClient(builder.build());
  }

  @Test
  void productExists_okResponse_returnsTrue() {
    server.expect(requestTo("http://product/products/p1"))
        .andRespond(withSuccess("{\"sku\":\"p1\",\"price\":10}", MediaType.APPLICATION_JSON));

    assertThat(client.productExists("p1")).isTrue();
    server.verify();
  }

  @Test
  void productExists_notFound_returnsFalse() {
    server.expect(requestTo("http://product/products/missing")).andRespond(withResourceNotFound());

    assertThat(client.productExists("missing")).isFalse();
  }

  @Test
  void productExists_existsFalseInDataEnvelope_returnsFalse() {
    server.expect(requestTo("http://product/products/p2"))
        .andRespond(withSuccess("{\"data\":{\"exists\":false}}", MediaType.APPLICATION_JSON));

    assertThat(client.productExists("p2")).isFalse();
  }

  @Test
  void productExists_emptyOkBody_returnsTrue() {
    server.expect(requestTo("http://product/products/p3")).andRespond(withSuccess());

    assertThat(client.productExists("p3")).isTrue();
  }

  @Test
  void productExists_serverError_propagates() {
    server.expect(requestTo("http://product/products/p4")).andRespond(withServerError());

    assertThatThrownBy(() -> client.productExists("p4"))
        .isInstanceOf(HttpServerErrorException.class);
  }
}
