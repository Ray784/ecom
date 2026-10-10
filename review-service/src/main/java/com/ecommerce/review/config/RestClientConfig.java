package com.ecommerce.review.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP client wiring for the review validation calls to product-service and order-service. */
@Configuration
public class RestClientConfig {

  /** RestClient pointed at product-service, used to confirm the reviewed product exists. */
  @Bean
  public RestClient productRestClient(
      RestClient.Builder builder,
      @Value("${review.product-service.url:http://localhost:8083}") String baseUrl,
      @Value("${review.http.connect-timeout-ms:2000}") int connectTimeoutMs,
      @Value("${review.http.read-timeout-ms:5000}") int readTimeoutMs) {
    return build(builder, baseUrl, connectTimeoutMs, readTimeoutMs);
  }

  /** RestClient pointed at order-service, used to confirm the reviewer bought the product. */
  @Bean
  public RestClient orderRestClient(
      RestClient.Builder builder,
      @Value("${review.order-service.url:http://localhost:8084}") String baseUrl,
      @Value("${review.http.connect-timeout-ms:2000}") int connectTimeoutMs,
      @Value("${review.http.read-timeout-ms:5000}") int readTimeoutMs) {
    return build(builder, baseUrl, connectTimeoutMs, readTimeoutMs);
  }

  /** Bounded timeouts so a slow peer cannot hang review submission. */
  private static RestClient build(
      RestClient.Builder builder, String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeoutMs);
    requestFactory.setReadTimeout(readTimeoutMs);
    return builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }
}
