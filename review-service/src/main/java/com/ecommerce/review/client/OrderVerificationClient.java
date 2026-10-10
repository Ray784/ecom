package com.ecommerce.review.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Asks order-service whether a user has a PAID order containing the reviewed product.
 *
 * <p>A response without verified=true counts as not verified. Transport failures propagate so a
 * review is never accepted without a positive answer.
 */
@Component
public class OrderVerificationClient {

  private final RestClient restClient;

  public OrderVerificationClient(@Qualifier("orderRestClient") RestClient restClient) {
    this.restClient = restClient;
  }

  /** Calls GET /orders/verified-purchase with the product ID passed as the sku parameter. */
  public boolean isVerifiedPurchaser(String userId, String productId) {
    JsonNode body =
        restClient
            .get()
            .uri(
                uri ->
                    uri.path("/orders/verified-purchase")
                        .queryParam("userId", userId)
                        .queryParam("sku", productId)
                        .build())
            .retrieve()
            .body(JsonNode.class);
    if (body == null) {
      return false;
    }
    JsonNode verified = body.get("verified");
    return verified != null && verified.isBoolean() && verified.booleanValue();
  }
}
