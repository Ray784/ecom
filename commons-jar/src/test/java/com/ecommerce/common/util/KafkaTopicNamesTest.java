package com.ecommerce.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KafkaTopicNamesTest {

  @Test
  void topicNamesMatchTheDocumentedContract() {
    assertThat(KafkaTopicNames.ORDER_PLACED_TOPIC).isEqualTo("order-placed-topic");
    assertThat(KafkaTopicNames.INVENTORY_RESERVED_TOPIC).isEqualTo("inventory-reserved-topic");
    assertThat(KafkaTopicNames.INVENTORY_FAILED_TOPIC).isEqualTo("inventory-failed-topic");
    assertThat(KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC).isEqualTo("payment-successful-topic");
    assertThat(KafkaTopicNames.PAYMENT_FAILED_TOPIC).isEqualTo("payment-failed-topic");
  }

  @Test
  void allTopicsListsEveryTopicOnce() {
    assertThat(KafkaTopicNames.ALL_TOPICS)
        .containsExactly(
            "order-placed-topic",
            "inventory-reserved-topic",
            "inventory-failed-topic",
            "payment-successful-topic",
            "payment-failed-topic");
  }
}
