package com.ecommerce.common.util;

import java.util.List;

/** Names of the Kafka topics used by the order, inventory and payment saga. */
public final class KafkaTopicNames {

  public static final String ORDER_PLACED_TOPIC = "order-placed-topic";
  public static final String INVENTORY_RESERVED_TOPIC = "inventory-reserved-topic";
  public static final String INVENTORY_FAILED_TOPIC = "inventory-failed-topic";
  public static final String PAYMENT_SUCCESSFUL_TOPIC = "payment-successful-topic";
  public static final String PAYMENT_FAILED_TOPIC = "payment-failed-topic";

  /** Every topic, useful for creating them up front on an embedded broker. */
  public static final List<String> ALL_TOPICS =
      List.of(
          ORDER_PLACED_TOPIC,
          INVENTORY_RESERVED_TOPIC,
          INVENTORY_FAILED_TOPIC,
          PAYMENT_SUCCESSFUL_TOPIC,
          PAYMENT_FAILED_TOPIC);

  private KafkaTopicNames() {}
}
