package com.ecommerce.order.client;

import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Synchronous client for the product-service catalog used to price cart items.
 *
 * <p>A 404 from product-service means the SKU cannot be sold and maps to ORDER_4004. Any other
 * transport failure or a response without a price maps to ORDER_5002.
 */
@Component
public class ProductCatalogClient {

  private static final Logger log = LoggerFactory.getLogger(ProductCatalogClient.class);

  private final RestClient restClient;

  public ProductCatalogClient(@Qualifier("productRestClient") RestClient restClient) {
    this.restClient = restClient;
  }

  /** Fetches price and stock for the given SKU from GET /products/{sku}. */
  public ProductSnapshot fetchProduct(String sku) {
    JsonNode body;
    try {
      body = restClient.get().uri("/products/{sku}", sku).retrieve().body(JsonNode.class);
    } catch (HttpClientErrorException.NotFound e) {
      throw CodedErrorFactory.create(MappedError.ORDER_4004, e, sku);
    } catch (RestClientException e) {
      log.warn("Product lookup for {} failed: {}", sku, e.getMessage());
      throw CodedErrorFactory.create(MappedError.ORDER_5002, e);
    }
    if (body == null) {
      throw CodedErrorFactory.create(MappedError.ORDER_5002);
    }
    JsonNode product = body.hasNonNull("data") ? body.get("data") : body;
    JsonNode price = product.get("price");
    if (price == null || !price.isNumber()) {
      throw CodedErrorFactory.create(MappedError.ORDER_5002);
    }
    JsonNode stock = product.get("currentStock");
    Integer currentStock = stock != null && stock.isNumber() ? stock.intValue() : null;
    return new ProductSnapshot(sku, price.decimalValue(), currentStock);
  }

  /** Price and stock of a product at lookup time. currentStock is null when unknown. */
  public record ProductSnapshot(String sku, BigDecimal price, Integer currentStock) {}
}
