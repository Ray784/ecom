package com.ecommerce.order.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.order.client.ProductCatalogClient.ProductSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
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

  private static void assertError(Runnable call, MappedError expected) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ApplicationBaseException.class)
        .extracting(e -> ((ApplicationBaseException) e).getMappedError())
        .isEqualTo(expected);
  }

  @Test
  void returnsPriceAndStockFromProductDetails() {
    server.expect(requestTo("http://product/products/SKU-1"))
        .andRespond(withSuccess(
            "{\"sku\":\"SKU-1\",\"price\":19.99,\"currentStock\":7}", MediaType.APPLICATION_JSON));

    ProductSnapshot product = client.fetchProduct("SKU-1");

    assertThat(product.price()).isEqualByComparingTo("19.99");
    assertThat(product.currentStock()).isEqualTo(7);
  }

  @Test
  void unwrapsDataEnvelopeAndToleratesMissingStock() {
    server.expect(requestTo("http://product/products/SKU-1"))
        .andRespond(withSuccess("{\"data\":{\"price\":5}}", MediaType.APPLICATION_JSON));

    ProductSnapshot product = client.fetchProduct("SKU-1");

    assertThat(product.price()).isEqualByComparingTo("5");
    assertThat(product.currentStock()).isNull();
  }

  @Test
  void notFoundMapsToOrder4004() {
    server.expect(requestTo("http://product/products/NOPE")).andRespond(withResourceNotFound());

    assertError(() -> client.fetchProduct("NOPE"), MappedError.ORDER_4004);
  }

  @Test
  void serverErrorMapsToOrder5002() {
    server.expect(requestTo("http://product/products/SKU-1")).andRespond(withServerError());

    assertError(() -> client.fetchProduct("SKU-1"), MappedError.ORDER_5002);
  }

  @Test
  void missingPriceMapsToOrder5002() {
    server.expect(requestTo("http://product/products/SKU-1"))
        .andRespond(withSuccess("{\"price\":\"n/a\"}", MediaType.APPLICATION_JSON));

    assertError(() -> client.fetchProduct("SKU-1"), MappedError.ORDER_5002);
  }

  @Test
  void emptyBodyMapsToOrder5002() {
    server.expect(requestTo("http://product/products/SKU-1")).andRespond(withSuccess());

    assertError(() -> client.fetchProduct("SKU-1"), MappedError.ORDER_5002);
  }
}
