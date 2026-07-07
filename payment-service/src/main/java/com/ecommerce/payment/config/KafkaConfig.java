package com.ecommerce.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.JsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;

/**
 * Kafka wiring for the payment service.
 *
 * <p>Records are consumed as JSON strings and converted to the listener parameter type, so the
 * service accepts events from producers that use either JsonSerializer or plain String payloads.
 * Spring Boot applies this converter to the default listener container factory.
 */
@Configuration
public class KafkaConfig {

  @Bean
  public RecordMessageConverter recordMessageConverter() {
    return new JsonMessageConverter();
  }
}
