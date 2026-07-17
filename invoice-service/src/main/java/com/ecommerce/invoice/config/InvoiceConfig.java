package com.ecommerce.invoice.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.kafka.support.converter.JsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Infrastructure beans for the invoice service.
 *
 * <p>Kafka records are consumed as JSON strings and converted to the listener parameter type.
 * The order service client uses short timeouts so a slow order service only delays the fallback.
 */
@Configuration
public class InvoiceConfig {

  @Bean
  public RecordMessageConverter recordMessageConverter() {
    return new JsonMessageConverter();
  }

  @Bean
  public RestClient orderServiceRestClient(
      @Value("${invoice.order-service.base-url:http://localhost:8084}") String baseUrl,
      @Value("${invoice.order-service.timeout-ms:2000}") int timeoutMs) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
    requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
    return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }
}
