package com.ecommerce.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.common.dto.ItemDetails;
import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.dto.ProductCreationDTO;
import com.ecommerce.common.util.KafkaTopicNames;
import com.ecommerce.product.repository.InventoryRepository;
import com.ecommerce.product.service.ProductCatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * End to end Kafka flow against an embedded broker: an order-placed record is consumed, stock is
 * reserved in H2 and the outcome lands on the reserved or failed topic.
 */
@SpringBootTest(
    properties = {
      "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
      "product.search.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:product-kafka-it;MODE=MySQL;DB_CLOSE_DELAY=-1"
    })
@EmbeddedKafka(
    partitions = 1,
    topics = {
      KafkaTopicNames.ORDER_PLACED_TOPIC,
      KafkaTopicNames.INVENTORY_RESERVED_TOPIC,
      KafkaTopicNames.INVENTORY_FAILED_TOPIC
    })
class KafkaInventoryFlowIntegrationTest {

  @Autowired private EmbeddedKafkaBroker broker;
  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
  @Autowired private ProductCatalogService productCatalogService;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void orderPlaced_reservesStockAndPublishesReservedThenFailed() throws Exception {
    productCatalogService.createProduct(
        new ProductCreationDTO("K-1", "Kindle", "e-reader", new BigDecimal("120.00"), "ELECTRONICS", 2, null));

    Map<String, Object> props = KafkaTestUtils.consumerProps("verifier", "false", broker);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    try (Consumer<String, String> consumer =
        new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
            .createConsumer()) {
      broker.consumeFromEmbeddedTopics(
          consumer, KafkaTopicNames.INVENTORY_RESERVED_TOPIC, KafkaTopicNames.INVENTORY_FAILED_TOPIC);

      kafkaTemplate.send(
          KafkaTopicNames.ORDER_PLACED_TOPIC,
          "ORD-1",
          new OrderPlacedEvent("ORD-1", "U1", List.of(new ItemDetails("K-1", 2, null)), BigDecimal.ONE));
      ConsumerRecord<String, String> reserved =
          KafkaTestUtils.getSingleRecord(
              consumer, KafkaTopicNames.INVENTORY_RESERVED_TOPIC, Duration.ofSeconds(30));
      JsonNode reservedJson = objectMapper.readTree(reserved.value());
      assertThat(reserved.key()).isEqualTo("ORD-1");
      assertThat(reservedJson.get("success").asBoolean()).isTrue();
      assertThat(inventoryRepository.findById("K-1").orElseThrow().getReservedStock()).isEqualTo(2);

      kafkaTemplate.send(
          KafkaTopicNames.ORDER_PLACED_TOPIC,
          "ORD-2",
          new OrderPlacedEvent("ORD-2", "U1", List.of(new ItemDetails("K-1", 1, null)), BigDecimal.ONE));
      ConsumerRecord<String, String> failed =
          KafkaTestUtils.getSingleRecord(
              consumer, KafkaTopicNames.INVENTORY_FAILED_TOPIC, Duration.ofSeconds(30));
      JsonNode failedJson = objectMapper.readTree(failed.value());
      assertThat(failedJson.get("orderId").asText()).isEqualTo("ORD-2");
      assertThat(failedJson.get("success").asBoolean()).isFalse();
      assertThat(failedJson.get("message").asText()).startsWith("PROD_4002");
    }
  }
}
