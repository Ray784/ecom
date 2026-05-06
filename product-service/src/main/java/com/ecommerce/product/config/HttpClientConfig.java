package com.ecommerce.product.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Builds the outbound HTTP client used for best effort calls to sibling services. */
@Configuration
public class HttpClientConfig {

  /** Short timeouts keep product writes fast even when a sibling service is down. */
  @Bean
  public RestClient outboundRestClient(
      RestClient.Builder builder,
      @Value("${product.http.connect-timeout-ms:2000}") int connectTimeoutMs,
      @Value("${product.http.read-timeout-ms:3000}") int readTimeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeoutMs);
    requestFactory.setReadTimeout(readTimeoutMs);
    return builder.requestFactory(requestFactory).build();
  }
}
