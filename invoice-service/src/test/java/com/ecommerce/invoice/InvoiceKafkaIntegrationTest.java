package com.ecommerce.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.common.dto.InvoiceDTO;
import com.ecommerce.common.util.KafkaTopicNames;
import com.ecommerce.invoice.service.InvoiceGeneratorService;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * End to end check of the Kafka path: a PaymentResult JSON record is consumed and an invoice is
 * generated with the fallback subtotal because the order service is unreachable.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
      "spring.datasource.url=jdbc:h2:mem:invoice-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "invoice.storage-dir=${java.io.tmpdir}/invoice-it",
      "invoice.order-service.base-url=http://127.0.0.1:1",
      "invoice.order-service.timeout-ms=500"
    })
@EmbeddedKafka(partitions = 1, topics = KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC)
class InvoiceKafkaIntegrationTest {

  @Autowired private EmbeddedKafkaBroker broker;
  @Autowired private InvoiceGeneratorService generator;

  @Test
  void paymentSuccessfulRecord_generatesInvoiceDocument() throws Exception {
    KafkaTemplate<String, String> producer =
        new KafkaTemplate<>(
            new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));
    producer
        .send(
            new ProducerRecord<>(
                KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC,
                "IT-ORD-1",
                "{\"orderId\":\"IT-ORD-1\",\"transactionId\":\"TXN-1\",\"success\":true}"))
        .get();

    InvoiceDTO invoice = null;
    long deadline = System.currentTimeMillis() + 30_000;
    while (invoice == null && System.currentTimeMillis() < deadline) {
      try {
        invoice = generator.getInvoice("IT-ORD-1");
      } catch (RuntimeException notYet) {
        Thread.sleep(250);
      }
    }

    assertThat(invoice).as("invoice generated from Kafka record").isNotNull();
    assertThat(invoice.getSubtotal()).isEqualByComparingTo("0.00");
    assertThat(Path.of(invoice.getPdfStorageLink())).exists();
    assertThat(Files.readString(Path.of(invoice.getPdfStorageLink()))).startsWith("%PDF");
  }
}
