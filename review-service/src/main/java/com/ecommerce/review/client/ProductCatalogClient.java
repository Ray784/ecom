package com.ecommerce.review.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Asks product-service whether a product exists before a review for it is accepted.
 *
 * <p>Only a 404 or an explicit exists=false body means the product is unknown. Any other failure
 * propagates so a review is never accepted without a positive answer.
 */
@Component
public class ProductCatalogClient {

  private final RestClient restClient;

  public ProductCatalogClient(@Qualifier("productRestClient") RestClient restClient) {
    this.restClient = restClient;
  }

  /** Calls GET /products/{productId} and returns false when product-service does not know it. */
  public boolean productExists(String productId) {
    JsonNode body;
    try {
      body =
          restClient.get().uri("/products/{productId}", productId).retrieve().body(JsonNode.class);
    } catch (HttpClientErrorException.NotFound e) {
      return false;
    }
    if (body == null) {
      return true;
    }
    JsonNode product = body.hasNonNull("data") ? body.get("data") : body;
    JsonNode exists = product.get("exists");
    return exists == null || !exists.isBoolean() || exists.booleanValue();
  }
}
