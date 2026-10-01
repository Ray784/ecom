package com.ecommerce.iam.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.common.util.KafkaTopicNames;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

class EmbeddedKafkaBrokerConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(EmbeddedKafkaBrokerConfig.class);

  @Test
  void disabledByDefault_noBrokerBean() {
    runner.run(context -> assertThat(context).doesNotHaveBean(EmbeddedKafkaBroker.class));
  }

  @Test
  void explicitlyDisabled_noBrokerBean() {
    runner
        .withPropertyValues("embedded.kafka.broker.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(EmbeddedKafkaBroker.class));
  }

  @Test
  void enabled_startsBrokerWithAllPlatformTopics() {
    runner
        .withPropertyValues("embedded.kafka.enabled=true", "embedded.kafka.broker.port=0")
        .run(
            context -> {
              assertThat(context).hasSingleBean(EmbeddedKafkaBroker.class);
              String brokers = context.getBean(EmbeddedKafkaBroker.class).getBrokersAsString();
              try (AdminClient admin =
                  AdminClient.create(
                      Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers))) {
                Set<String> topics = admin.listTopics().names().get(30, TimeUnit.SECONDS);
                assertThat(topics).containsAll(KafkaTopicNames.ALL_TOPICS);
              }
            });
  }
}
