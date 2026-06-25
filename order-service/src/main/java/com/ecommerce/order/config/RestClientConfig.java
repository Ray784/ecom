package com.ecommerce.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** HTTP client wiring for calls to other services. */
@Configuration
public class RestClientConfig {

  /** RestClient pointed at product-service with bounded timeouts so cart calls never hang. */
  @Bean
  public RestClient productRestClient(
      RestClient.Builder builder,
      @Value("${order.product-service.base-url}") String baseUrl,
      @Value("${order.product-service.connect-timeout-ms:2000}") int connectTimeoutMs,
      @Value("${order.product-service.read-timeout-ms:5000}") int readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeoutMs);
    requestFactory.setReadTimeout(readTimeoutMs);
    return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
  }
}
