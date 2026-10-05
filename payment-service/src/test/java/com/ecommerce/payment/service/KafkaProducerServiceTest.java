package com.ecommerce.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.exception.ApiInternalException;
import com.ecommerce.common.util.KafkaTopicNames;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaProducerServiceTest {

  private KafkaTemplate<String, Object> kafkaTemplate;
  private KafkaProducerService producer;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    kafkaTemplate = mock(KafkaTemplate.class);
    producer = new KafkaProducerService(kafkaTemplate, 200);
  }

  @Test
  void sendPaymentResult_success_publishesToSuccessfulTopicKeyedByOrder() {
    PaymentResultEvent event = new PaymentResultEvent("o-1", "TXN-1", true, null);
    when(kafkaTemplate.send(anyString(), anyString(), any()))
        .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

    assertThat(producer.sendPaymentResult(event)).isEqualTo(KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC);
    verify(kafkaTemplate).send(KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC, "o-1", event);
  }

  @Test
  void sendPaymentResult_failure_publishesToFailedTopic() {
    PaymentResultEvent event = new PaymentResultEvent("o-2", "TXN-2", false, "PAY_4001: declined");
    when(kafkaTemplate.send(anyString(), anyString(), any()))
        .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

    assertThat(producer.sendPaymentResult(event)).isEqualTo(KafkaTopicNames.PAYMENT_FAILED_TOPIC);
    verify(kafkaTemplate).send(eq(KafkaTopicNames.PAYMENT_FAILED_TOPIC), eq("o-2"), eq(event));
  }

  @Test
  void sendPaymentResult_brokerRejects_throwsPay5003() {
    when(kafkaTemplate.send(anyString(), anyString(), any()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

    assertThatThrownBy(() -> producer.sendPaymentResult(new PaymentResultEvent("o-3", "T", true, null)))
        .isInstanceOf(ApiInternalException.class)
        .hasMessageContaining("o-3")
        .extracting("errorCode")
        .isEqualTo("PAY_5003");
  }

  @Test
  void sendPaymentResult_noAcknowledgementInTime_throwsPay5003() {
    when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());

    assertThatThrownBy(() -> producer.sendPaymentResult(new PaymentResultEvent("o-4", "T", true, null)))
        .extracting("errorCode")
        .isEqualTo("PAY_5003");
  }

  @Test
  void sendPaymentResult_sendThrowsSynchronously_throwsPay5003() {
    when(kafkaTemplate.send(anyString(), anyString(), any()))
        .thenThrow(new IllegalStateException("metadata timeout"));

    assertThatThrownBy(() -> producer.sendPaymentResult(new PaymentResultEvent("o-5", "T", false, "x")))
        .extracting("errorCode")
        .isEqualTo("PAY_5003");
  }

  @Test
  void sendPaymentResult_interrupted_restoresFlagAndThrowsPay5003() {
    when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> producer.sendPaymentResult(new PaymentResultEvent("o-6", "T", true, null)))
          .extracting("errorCode")
          .isEqualTo("PAY_5003");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }
}
