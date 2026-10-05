package com.ecommerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.common.util.KafkaTopicNames;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class KafkaProducerServiceTest {

  @Mock private KafkaTemplate<String, Object> kafkaTemplate;

  private KafkaProducerService producer;
  private final OrderPlacedEvent event =
      new OrderPlacedEvent("o1", "u1", List.of(), BigDecimal.TEN);

  @BeforeEach
  void setUp() {
    producer = new KafkaProducerService(kafkaTemplate, 50);
  }

  private static void assertPublishError(Runnable call) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ApplicationBaseException.class)
        .extracting(e -> ((ApplicationBaseException) e).getMappedError())
        .isEqualTo(MappedError.ORDER_5001);
  }

  @Test
  void sendOrderPlacedPublishesToOrderPlacedTopicKeyedByOrderId() {
    CompletableFuture<SendResult<String, Object>> done = CompletableFuture.completedFuture(null);
    when(kafkaTemplate.send(KafkaTopicNames.ORDER_PLACED_TOPIC, "o1", event)).thenReturn(done);

    assertThatCode(() -> producer.sendOrderPlaced(event)).doesNotThrowAnyException();
    verify(kafkaTemplate).send(eq(KafkaTopicNames.ORDER_PLACED_TOPIC), eq("o1"), any());
  }

  @Test
  void brokerFailureMapsToOrder5001() {
    when(kafkaTemplate.send(any(String.class), any(String.class), any()))
        .thenReturn(CompletableFuture.failedFuture(new KafkaException("down")));

    assertPublishError(() -> producer.sendOrderPlaced(event));
  }

  @Test
  void synchronousSendFailureMapsToOrder5001() {
    when(kafkaTemplate.send(any(String.class), any(String.class), any()))
        .thenThrow(new KafkaException("metadata timeout"));

    assertPublishError(() -> producer.sendOrderPlaced(event));
  }

  @Test
  void missingAcknowledgementWithinTimeoutMapsToOrder5001() {
    when(kafkaTemplate.send(any(String.class), any(String.class), any()))
        .thenReturn(new CompletableFuture<>());

    assertPublishError(() -> producer.sendOrderPlaced(event));
  }

  @Test
  void interruptionMapsToOrder5001AndRestoresInterruptFlag() {
    when(kafkaTemplate.send(any(String.class), any(String.class), any()))
        .thenReturn(new CompletableFuture<>());
    Thread.currentThread().interrupt();
    try {
      assertPublishError(() -> producer.sendOrderPlaced(event));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }
}
