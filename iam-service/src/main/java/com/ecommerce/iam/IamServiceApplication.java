package com.ecommerce.iam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;

/**
 * Entry point of the IAM service.
 *
 * <p>Kafka client auto configuration is excluded because this service never produces or consumes
 * events; spring-kafka is only on the classpath to optionally host the embedded broker.
 */
@SpringBootApplication(exclude = KafkaAutoConfiguration.class)
public class IamServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(IamServiceApplication.class, args);
  }
}
