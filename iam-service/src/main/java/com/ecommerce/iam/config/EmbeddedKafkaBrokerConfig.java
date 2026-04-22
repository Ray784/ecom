package com.ecommerce.iam.config;

import com.ecommerce.common.util.KafkaTopicNames;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaZKBroker;

/**
 * Optionally hosts the shared local Kafka broker for the whole platform.
 *
 * <p>Enabled with embedded.kafka.broker.enabled=true or the shorter embedded.kafka.enabled=true.
 * The broker listens on embedded.kafka.broker.port, default 9092, and creates every topic in
 * KafkaTopicNames.ALL_TOPICS up front. The broker starts through afterPropertiesSet and stops
 * through destroy, both invoked by the Spring container.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(
    "${embedded.kafka.broker.enabled:false} or ${embedded.kafka.enabled:false}")
public class EmbeddedKafkaBrokerConfig {

  @Bean
  public EmbeddedKafkaBroker embeddedKafkaBroker(
      @Value("${embedded.kafka.broker.port:9092}") int port,
      @Value("${embedded.kafka.broker.partitions:1}") int partitions) {
    return new EmbeddedKafkaZKBroker(
            1, true, partitions, KafkaTopicNames.ALL_TOPICS.toArray(String[]::new))
        .kafkaPorts(port);
  }
}
