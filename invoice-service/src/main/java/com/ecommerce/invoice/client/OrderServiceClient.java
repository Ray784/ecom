package com.ecommerce.invoice.client;

import com.ecommerce.common.dto.OrderDetailsDTO;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Reads order details from the order service over REST. */
@Component
public class OrderServiceClient {

  private static final Logger log = LoggerFactory.getLogger(OrderServiceClient.class);

  private final RestClient orderServiceRestClient;

  public OrderServiceClient(RestClient orderServiceRestClient) {
    this.orderServiceRestClient = orderServiceRestClient;
  }

  /**
   * Fetches the order, returning empty when the order is unknown or the order service cannot be
   * reached, so callers can decide on a fallback.
   */
  public Optional<OrderDetailsDTO> fetchOrder(String orderId) {
    try {
      return Optional.ofNullable(
          orderServiceRestClient
              .get()
              .uri("/orders/{orderId}", orderId)
              .retrieve()
              .body(OrderDetailsDTO.class));
    } catch (RestClientException ex) {
      log.warn("Order service lookup for {} failed: {}", orderId, ex.getMessage());
      return Optional.empty();
    }
  }
}
