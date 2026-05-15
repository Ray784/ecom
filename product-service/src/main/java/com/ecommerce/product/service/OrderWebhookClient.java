package com.ecommerce.product.service;

import com.ecommerce.common.dto.InventoryReservedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Optional REST fallback that forwards inventory results to order-service.
 *
 * <p>Disabled unless product.order-webhook.url is set.
 */
@Component
public class OrderWebhookClient {

  private static final Logger log = LoggerFactory.getLogger(OrderWebhookClient.class);

  private final RestClient restClient;
  private final String webhookUrl;

  public OrderWebhookClient(
      RestClient outboundRestClient, @Value("${product.order-webhook.url:}") String webhookUrl) {
    this.restClient = outboundRestClient;
    this.webhookUrl = webhookUrl;
  }

  public boolean isConfigured() {
    return webhookUrl != null && !webhookUrl.isBlank();
  }

  /** Posts the event to the webhook and returns true only when delivery succeeded. */
  public boolean notifyInventoryResult(InventoryReservedEvent event) {
    if (!isConfigured() || event == null) {
      return false;
    }
    try {
      restClient
          .post()
          .uri(webhookUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .body(event)
          .retrieve()
          .toBodilessEntity();
      return true;
    } catch (RuntimeException e) {
      log.warn("Order webhook delivery failed for order {}: {}", event.getOrderId(), e.getMessage());
      return false;
    }
  }
}
