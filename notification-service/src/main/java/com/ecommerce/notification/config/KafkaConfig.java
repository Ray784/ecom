package com.ecommerce.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.JsonMessageConverter;
import org.springframework.kafka.support.converter.RecordMessageConverter;

/**
 * Kafka wiring for the notification service.
 *
 * <p>Records are consumed as JSON strings and converted to the listener parameter type, which lets
 * one container factory serve both OrderPlaced and PaymentResult listeners.
 */
@Configuration
public class KafkaConfig {

  @Bean
  public RecordMessageConverter recordMessageConverter() {
    return new JsonMessageConverter();
  }
}
