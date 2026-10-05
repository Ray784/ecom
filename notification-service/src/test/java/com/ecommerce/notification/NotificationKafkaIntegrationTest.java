package com.ecommerce.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.common.util.KafkaTopicNames;
import com.ecommerce.notification.model.NotificationLog;
import com.ecommerce.notification.model.NotificationTemplate;
import com.ecommerce.notification.service.NotificationSenderService;
import java.util.List;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * End to end check of both listeners: OrderPlaced and PaymentResult JSON records produce two
 * notifications for the same user.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(
    partitions = 1,
    topics = {
      KafkaTopicNames.ORDER_PLACED_TOPIC,
      KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC,
      KafkaTopicNames.PAYMENT_FAILED_TOPIC
    })
class NotificationKafkaIntegrationTest {

  @Autowired private EmbeddedKafkaBroker broker;
  @Autowired private NotificationSenderService senderService;

  private List<NotificationLog> awaitLogs(String orderId, int expected) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 30_000;
    List<NotificationLog> logs = senderService.getNotificationsByOrderId(orderId);
    while (logs.size() < expected && System.currentTimeMillis() < deadline) {
      Thread.sleep(250);
      logs = senderService.getNotificationsByOrderId(orderId);
    }
    return logs;
  }

  @Test
  void orderAndPaymentRecords_produceNotificationsForTheUser() throws Exception {
    KafkaTemplate<String, String> producer =
        new KafkaTemplate<>(
            new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));

    producer
        .send(new ProducerRecord<>(KafkaTopicNames.ORDER_PLACED_TOPIC, "IT-1",
            "{\"orderId\":\"IT-1\",\"userId\":\"kate\",\"items\":[],\"totalAmount\":30.00}"))
        .get();
    assertThat(awaitLogs("IT-1", 1)).hasSize(1);

    producer
        .send(new ProducerRecord<>(KafkaTopicNames.PAYMENT_FAILED_TOPIC, "IT-1",
            "{\"orderId\":\"IT-1\",\"transactionId\":\"TXN-1\",\"success\":false,"
                + "\"failureReason\":\"PAY_4001: declined\"}"))
        .get();
    List<NotificationLog> logs = awaitLogs("IT-1", 2);

    assertThat(logs)
        .extracting(NotificationLog::getTemplateId)
        .containsExactly(NotificationTemplate.ORDER_PLACED_TEMPLATE, NotificationTemplate.PAYMENT_FAILED_TEMPLATE);
    assertThat(senderService.getNotificationsByUserId("kate")).hasSize(2);
  }
}
