package com.ecommerce.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.InventoryReservedEvent;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.common.util.KafkaTopicNames;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaProducerServiceTest {

  private KafkaTemplate<String, Object> kafkaTemplate;
  private OrderWebhookClient webhookClient;
  private KafkaProducerService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    kafkaTemplate = mock(KafkaTemplate.class);
    webhookClient = mock(OrderWebhookClient.class);
    service = new KafkaProducerService(kafkaTemplate, webhookClient, 1000);
  }

  @AfterEach
  void clearInterruptFlag() {
    Thread.interrupted();
  }

  private void kafkaSucceeds() {
    when(kafkaTemplate.send(anyString(), anyString(), any()))
        .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
  }

  @Test
  void sendInventoryResult_success_routesToReservedTopic() {
    kafkaSucceeds();
    InventoryReservedEvent event = new InventoryReservedEvent("O1", true, "ok");

    assertThat(service.sendInventoryResult(event)).isEqualTo(KafkaTopicNames.INVENTORY_RESERVED_TOPIC);
    verify(kafkaTemplate).send(KafkaTopicNames.INVENTORY_RESERVED_TOPIC, "O1", event);
    verify(webhookClient).notifyInventoryResult(event);
  }

  @Test
  void sendInventoryResult_failure_routesToFailedTopic() {
    kafkaSucceeds();
    InventoryReservedEvent event = new InventoryReservedEvent("O1", false, "no stock");

    assertThat(service.sendInventoryResult(event)).isEqualTo(KafkaTopicNames.INVENTORY_FAILED_TOPIC);
    verify(kafkaTemplate).send(KafkaTopicNames.INVENTORY_FAILED_TOPIC, "O1", event);
  }

  @Test
  void sendInventoryResult_kafkaDownButWebhookDelivered_doesNotThrow() {
    when(kafkaTemplate.send(anyString(), anyString(), any()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
    when(webhookClient.notifyInventoryResult(any())).thenReturn(true);

    assertThat(service.sendInventoryResult(new InventoryReservedEvent("O1", true, "ok")))
        .isEqualTo(KafkaTopicNames.INVENTORY_RESERVED_TOPIC);
  }

  @Test
  void sendInventoryResult_kafkaThrowsAndNoWebhook_throwsProd5002() {
    when(kafkaTemplate.send(anyString(), anyString(), any()))
        .thenThrow(new IllegalStateException("no metadata"));

    assertThatThrownBy(() -> service.sendInventoryResult(new InventoryReservedEvent("O1", true, "ok")))
        .isInstanceOf(ApplicationBaseException.class)
        .extracting("errorCode")
        .isEqualTo("PROD_5002");
  }

  @Test
  @SuppressWarnings("unchecked")
  void sendInventoryResult_kafkaTimeout_throwsProd5002() throws Exception {
    CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
    when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException());
    when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

    assertThatThrownBy(() -> service.sendInventoryResult(new InventoryReservedEvent("O1", true, "ok")))
        .extracting("errorCode")
        .isEqualTo("PROD_5002");
  }

  @Test
  @SuppressWarnings("unchecked")
  void sendInventoryResult_interrupted_restoresInterruptFlag() throws Exception {
    CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
    when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
    when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);
    when(webhookClient.notifyInventoryResult(any())).thenReturn(true);

    service.sendInventoryResult(new InventoryReservedEvent("O1", true, "ok"));

    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }
}
