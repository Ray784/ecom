package com.ecommerce.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.common.util.KafkaTopicNames;
import com.ecommerce.payment.model.PaymentTransaction;
import com.ecommerce.payment.repository.PaymentTransactionRepository;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * End to end check of the Kafka path against an embedded broker: an OrderPlaced JSON record
 * produced as a plain string is consumed, persisted and answered on the right result topic.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
      "spring.datasource.url=jdbc:h2:mem:payment-it;MODE=MySQL;DB_CLOSE_DELAY=-1"
    })
@EmbeddedKafka(
    partitions = 1,
    topics = {
      KafkaTopicNames.ORDER_PLACED_TOPIC,
      KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC,
      KafkaTopicNames.PAYMENT_FAILED_TOPIC
    })
class PaymentKafkaIntegrationTest {

  @Autowired private EmbeddedKafkaBroker broker;
  @Autowired private PaymentTransactionRepository repository;

  private Consumer<String, String> resultConsumer;
  private KafkaTemplate<String, String> rawProducer;

  @BeforeEach
  void setUp() {
    Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("payment-it", "true", broker);
    consumerProps.put("auto.offset.reset", "earliest");
    resultConsumer =
        new DefaultKafkaConsumerFactory<>(
                consumerProps, new StringDeserializer(), new StringDeserializer())
            .createConsumer();
    broker.consumeFromEmbeddedTopics(
        resultConsumer,
        KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC,
        KafkaTopicNames.PAYMENT_FAILED_TOPIC);
    rawProducer =
        new KafkaTemplate<>(
            new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
  }

  @AfterEach
  void tearDown() {
    resultConsumer.close();
  }

  private ConsumerRecord<String, String> sendAndAwaitResult(String orderId, String userId)
      throws Exception {
    String json =
        "{\"orderId\":\"" + orderId + "\",\"userId\":\"" + userId
            + "\",\"items\":[{\"sku\":\"SKU-1\",\"quantity\":2,\"unitPrice\":10.00}],"
            + "\"totalAmount\":20.00}";
    rawProducer.send(new ProducerRecord<>(KafkaTopicNames.ORDER_PLACED_TOPIC, orderId, json)).get();
    long deadline = System.currentTimeMillis() + 30_000;
    while (System.currentTimeMillis() < deadline) {
      for (ConsumerRecord<String, String> record : resultConsumer.poll(Duration.ofMillis(500))) {
        if (orderId.equals(record.key())) {
          return record;
        }
      }
    }
    throw new AssertionError("No payment result received for " + orderId);
  }

  @Test
  void orderPlacedRecord_normalUser_publishesSuccessAndPersistsSuccess() throws Exception {
    ConsumerRecord<String, String> result = sendAndAwaitResult("it-order-1", "alice");

    assertThat(result.topic()).isEqualTo(KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC);
    assertThat(result.value()).contains("\"success\":true").contains("it-order-1");
    PaymentTransaction tx = repository.findFirstByOrderIdOrderByCreatedAtDesc("it-order-1").orElseThrow();
    assertThat(tx.getStatus()).isEqualTo(PaymentTransaction.STATUS_SUCCESS);
  }

  @Test
  void orderPlacedRecord_declinedUser_publishesFailureWithPay4001() throws Exception {
    ConsumerRecord<String, String> result = sendAndAwaitResult("it-order-2", "decline-bob");

    assertThat(result.topic()).isEqualTo(KafkaTopicNames.PAYMENT_FAILED_TOPIC);
    assertThat(result.value()).contains("\"success\":false").contains("PAY_4001");
    assertThat(repository.findFirstByOrderIdOrderByCreatedAtDesc("it-order-2").orElseThrow().getStatus())
        .isEqualTo(PaymentTransaction.STATUS_FAILED);
  }
}
