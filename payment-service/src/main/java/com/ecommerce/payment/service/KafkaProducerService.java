package com.ecommerce.payment.service;

import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.util.KafkaTopicNames;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** Publishes payment outcomes to the success or failure topic. */
@Service
public class KafkaProducerService {

  private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final long sendTimeoutMs;

  public KafkaProducerService(
      KafkaTemplate<String, Object> kafkaTemplate,
      @Value("${payment.kafka.send-timeout-ms:10000}") long sendTimeoutMs) {
    this.kafkaTemplate = kafkaTemplate;
    this.sendTimeoutMs = sendTimeoutMs;
  }

  /**
   * Sends the event keyed by order ID and waits for the broker acknowledgement.
   *
   * <p>Any send failure is reported as PAY_5003.
   *
   * @return the topic the event was written to
   */
  public String sendPaymentResult(PaymentResultEvent event) {
    String topic =
        event.isSuccess()
            ? KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC
            : KafkaTopicNames.PAYMENT_FAILED_TOPIC;
    try {
      kafkaTemplate.send(topic, event.getOrderId(), event).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw CodedErrorFactory.create(MappedError.PAY_5003, ex, event.getOrderId());
    } catch (ExecutionException | TimeoutException | RuntimeException ex) {
      throw CodedErrorFactory.create(MappedError.PAY_5003, ex, event.getOrderId());
    }
    log.info("Published payment result for order {} to {}", event.getOrderId(), topic);
    return topic;
  }
}
