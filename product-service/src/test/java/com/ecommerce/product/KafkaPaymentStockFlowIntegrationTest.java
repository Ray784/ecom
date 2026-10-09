package com.ecommerce.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.common.dto.ItemDetails;
import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.dto.ProductCreationDTO;
import com.ecommerce.common.util.KafkaTopicNames;
import com.ecommerce.product.model.Inventory;
import com.ecommerce.product.repository.InventoryRepository;
import com.ecommerce.product.service.InventoryManagerService;
import com.ecommerce.product.service.InventoryManagerService.ReservationState;
import com.ecommerce.product.service.ProductCatalogService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

/**
 * End to end settlement flow against an embedded broker: an order-placed record reserves stock,
 * then a payment result on payment-successful-topic decrements it or a payment result on
 * payment-failed-topic releases it. Replays must not change stock a second time.
 */
@SpringBootTest(
    properties = {
      "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
      "product.search.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:product-payment-it;MODE=MySQL;DB_CLOSE_DELAY=-1"
    })
@EmbeddedKafka(
    partitions = 1,
    topics = {
      KafkaTopicNames.ORDER_PLACED_TOPIC,
      KafkaTopicNames.INVENTORY_RESERVED_TOPIC,
      KafkaTopicNames.INVENTORY_FAILED_TOPIC,
      KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC,
      KafkaTopicNames.PAYMENT_FAILED_TOPIC
    })
class KafkaPaymentStockFlowIntegrationTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
  @Autowired private ProductCatalogService productCatalogService;
  @Autowired private InventoryRepository inventoryRepository;
  @Autowired private InventoryManagerService inventoryManagerService;

  @Test
  void paymentSuccessful_decrementsReservedStockOnceEvenOnReplay() throws Exception {
    createProduct("PAY-OK-1", 15);
    createProduct("PAY-OK-MARK", 15);
    placeOrder("ORD-PAY-OK", "PAY-OK-1", 1);
    placeOrder("ORD-PAY-OK-MARK", "PAY-OK-MARK", 1);
    awaitStock("PAY-OK-1", 15, 1);
    awaitState("ORD-PAY-OK-MARK", ReservationState.RESERVED);

    PaymentResultEvent paid = new PaymentResultEvent("ORD-PAY-OK", "pay_ok", true, null);
    kafkaTemplate.send(KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC, "ORD-PAY-OK", paid);
    awaitStock("PAY-OK-1", 14, 0);
    assertThat(inventoryManagerService.getReservationState("ORD-PAY-OK"))
        .isEqualTo(ReservationState.DECREMENTED);

    // Replay as a typed record and as a JSON string payload, then a marker order on the same
    // partition proves both replays were consumed before the stock assertion.
    kafkaTemplate.send(KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC, "ORD-PAY-OK", paid);
    kafkaTemplate.send(
        KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC,
        "ORD-PAY-OK",
        "{\"orderId\":\"ORD-PAY-OK\",\"transactionId\":\"pay_ok\",\"success\":true}");
    kafkaTemplate.send(
        KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC,
        "ORD-PAY-OK-MARK",
        new PaymentResultEvent("ORD-PAY-OK-MARK", "pay_mark", true, null));
    awaitState("ORD-PAY-OK-MARK", ReservationState.DECREMENTED);

    assertStock("PAY-OK-1", 14, 0);
  }

  @Test
  void paymentFailed_releasesReservedStockOnceEvenOnReplay() throws Exception {
    createProduct("PAY-FAIL-1", 15);
    createProduct("PAY-FAIL-MARK", 15);
    placeOrder("ORD-PAY-FAIL", "PAY-FAIL-1", 2);
    placeOrder("ORD-PAY-FAIL-MARK", "PAY-FAIL-MARK", 1);
    awaitStock("PAY-FAIL-1", 15, 2);
    awaitState("ORD-PAY-FAIL-MARK", ReservationState.RESERVED);

    PaymentResultEvent declined =
        new PaymentResultEvent("ORD-PAY-FAIL", "pay_fail", false, "BAD_REQUEST_ERROR");
    kafkaTemplate.send(KafkaTopicNames.PAYMENT_FAILED_TOPIC, "ORD-PAY-FAIL", declined);
    awaitStock("PAY-FAIL-1", 15, 0);
    assertThat(inventoryManagerService.getReservationState("ORD-PAY-FAIL"))
        .isEqualTo(ReservationState.RELEASED);

    kafkaTemplate.send(KafkaTopicNames.PAYMENT_FAILED_TOPIC, "ORD-PAY-FAIL", declined);
    kafkaTemplate.send(
        KafkaTopicNames.PAYMENT_FAILED_TOPIC,
        "ORD-PAY-FAIL-MARK",
        new PaymentResultEvent("ORD-PAY-FAIL-MARK", "pay_mark", false, "BAD_REQUEST_ERROR"));
    awaitState("ORD-PAY-FAIL-MARK", ReservationState.RELEASED);

    assertStock("PAY-FAIL-1", 15, 0);
    assertStock("PAY-FAIL-MARK", 15, 0);
  }

  private void createProduct(String sku, int stock) {
    productCatalogService.createProduct(
        new ProductCreationDTO(
            sku, "Item " + sku, "desc", new BigDecimal("10.00"), "ELECTRONICS", stock, null));
  }

  private void placeOrder(String orderId, String sku, int quantity) {
    kafkaTemplate.send(
        KafkaTopicNames.ORDER_PLACED_TOPIC,
        orderId,
        new OrderPlacedEvent(
            orderId, "U1", List.of(new ItemDetails(sku, quantity, null)), BigDecimal.TEN));
  }

  private void assertStock(String sku, int current, int reserved) {
    Inventory inventory = inventoryRepository.findById(sku).orElseThrow();
    assertThat(inventory.getCurrentStock()).isEqualTo(current);
    assertThat(inventory.getReservedStock()).isEqualTo(reserved);
  }

  private void awaitStock(String sku, int current, int reserved) throws InterruptedException {
    await(
        () ->
            inventoryRepository
                .findById(sku)
                .map(
                    inventory ->
                        inventory.getCurrentStock() == current
                            && inventory.getReservedStock() == reserved)
                .orElse(false));
    assertStock(sku, current, reserved);
  }

  private void awaitState(String orderId, ReservationState state) throws InterruptedException {
    await(() -> inventoryManagerService.getReservationState(orderId) == state);
    assertThat(inventoryManagerService.getReservationState(orderId)).isEqualTo(state);
  }

  private static void await(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
      Thread.sleep(100);
    }
  }
}
