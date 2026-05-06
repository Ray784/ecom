package com.ecommerce.product.service;

import com.ecommerce.common.dto.ProductSummaryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Pushes product snapshots to search-service.
 *
 * <p>Indexing is best effort: any failure is logged and swallowed so catalog writes never fail
 * because search is unavailable.
 */
@Component
public class SearchIndexClient {

  /** Path of the search-service indexing endpoint. */
  public static final String INDEX_PATH = "/search/index";

  private static final Logger log = LoggerFactory.getLogger(SearchIndexClient.class);

  private final RestClient restClient;
  private final boolean enabled;
  private final String baseUrl;

  public SearchIndexClient(
      RestClient outboundRestClient,
      @Value("${product.search.enabled:true}") boolean enabled,
      @Value("${product.search.base-url:http://localhost:8089}") String baseUrl) {
    this.restClient = outboundRestClient;
    this.enabled = enabled;
    this.baseUrl = baseUrl;
  }

  /** Sends the product to search-service and returns true only when it was accepted. */
  public boolean pushProduct(ProductSummaryDTO product) {
    if (!enabled || product == null) {
      return false;
    }
    try {
      restClient
          .post()
          .uri(baseUrl + INDEX_PATH)
          .contentType(MediaType.APPLICATION_JSON)
          .body(product)
          .retrieve()
          .toBodilessEntity();
      log.debug("Indexed product {} in search-service", product.getSku());
      return true;
    } catch (RuntimeException e) {
      log.warn("Search indexing failed for product {}: {}", product.getSku(), e.getMessage());
      return false;
    }
  }
}
