package com.ecommerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.InventoryReservedEvent;
import com.ecommerce.common.dto.OrderDetailsDTO;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.common.util.KafkaTopicNames;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaListenerServiceTest {

  @Mock private OrderManagerService orderManagerService;

  private KafkaListenerService listener;
  private final OrderDetailsDTO details = new OrderDetailsDTO();

  @BeforeEach
  void setUp() {
    listener = new KafkaListenerService(orderManagerService, new ObjectMapper());
  }

  private void givenStatus(OrderStatus status) {
    when(orderManagerService.getOrderStatus("o1")).thenReturn(status);
  }

  @Test
  void inventoryReservedMovesPendingOrderToInventoryReserved() {
    givenStatus(OrderStatus.PENDING);
    when(orderManagerService.updateOrderStatus("o1", OrderStatus.INVENTORY_RESERVED))
        .thenReturn(details);

    assertThat(listener.handleInventoryUpdate(new InventoryReservedEvent("o1", true, null)))
        .isSameAs(details);
  }

  @Test
  void inventoryReservedDoesNotDowngradePaidOrder() {
    givenStatus(OrderStatus.PAID);
    when(orderManagerService.getOrderDetails("o1")).thenReturn(details);

    assertThat(listener.handleInventoryUpdate(new InventoryReservedEvent("o1", true, null)))
        .isSameAs(details);
    verify(orderManagerService, never()).updateOrderStatus(anyString(), any());
  }

  @Test
  void inventoryFailureMarksOrderFailedWithInventoryReason() {
    givenStatus(OrderStatus.PAID);
    when(orderManagerService.updateOrderStatus(
            "o1", OrderStatus.FAILED, Order.REASON_INVENTORY_FAILED))
        .thenReturn(details);

    assertThat(listener.handleInventoryUpdate(new InventoryReservedEvent("o1", false, "oos")))
        .isSameAs(details);
  }

  @Test
  void inventoryFailureIgnoredForTerminalOrder() {
    givenStatus(OrderStatus.CANCELLED);
    when(orderManagerService.getOrderDetails("o1")).thenReturn(details);

    listener.handleInventoryUpdate(new InventoryReservedEvent("o1", false, "oos"));

    verify(orderManagerService, never()).updateOrderStatus(anyString(), any(), any());
  }

  @Test
  void paymentSuccessMarksReservedOrderPaid() {
    givenStatus(OrderStatus.INVENTORY_RESERVED);
    when(orderManagerService.updateOrderStatus("o1", OrderStatus.PAID)).thenReturn(details);

    assertThat(listener.handlePaymentResult(new PaymentResultEvent("o1", "t1", true, null)))
        .isSameAs(details);
  }

  @Test
  void paymentSuccessMarksPendingOrderPaid() {
    givenStatus(OrderStatus.PENDING);
    when(orderManagerService.updateOrderStatus("o1", OrderStatus.PAID)).thenReturn(details);

    assertThat(listener.handlePaymentResult(new PaymentResultEvent("o1", "t1", true, null)))
        .isSameAs(details);
  }

  @Test
  void paymentSuccessIgnoredForFailedOrder() {
    givenStatus(OrderStatus.FAILED);
    when(orderManagerService.getOrderDetails("o1")).thenReturn(details);

    listener.handlePaymentResult(new PaymentResultEvent("o1", "t1", true, null));

    verify(orderManagerService, never()).updateOrderStatus(anyString(), any());
  }

  @Test
  void paymentFailureMarksOrderFailedWithPaymentReason() {
    givenStatus(OrderStatus.INVENTORY_RESERVED);
    when(orderManagerService.updateOrderStatus(
            "o1", OrderStatus.FAILED, Order.REASON_PAYMENT_FAILED))
        .thenReturn(details);

    assertThat(listener.handlePaymentResult(new PaymentResultEvent("o1", "t1", false, "card")))
        .isSameAs(details);
  }

  @Test
  void paymentFailureIgnoredForTerminalOrder() {
    givenStatus(OrderStatus.CANCELLED);
    when(orderManagerService.getOrderDetails("o1")).thenReturn(details);

    listener.handlePaymentResult(new PaymentResultEvent("o1", "t1", false, "card"));

    verify(orderManagerService, never()).updateOrderStatus(anyString(), any(), any());
  }

  @Test
  void handleMethodsPropagateUnknownOrder() {
    when(orderManagerService.getOrderStatus("nope"))
        .thenThrow(CodedErrorFactory.create(MappedError.ORDER_4002, "nope"));

    assertThatThrownBy(
            () -> listener.handlePaymentResult(new PaymentResultEvent("nope", null, true, null)))
        .isInstanceOf(ApplicationBaseException.class);
  }

  @Test
  void inventoryFailedTopicForcesFailureEvenWhenPayloadSaysSuccess() {
    givenStatus(OrderStatus.PENDING);

    listener.onInventoryMessage(
        "{\"orderId\":\"o1\",\"success\":true}", KafkaTopicNames.INVENTORY_FAILED_TOPIC);

    verify(orderManagerService)
        .updateOrderStatus("o1", OrderStatus.FAILED, Order.REASON_INVENTORY_FAILED);
  }

  @Test
  void inventoryReservedTopicAppliesReservation() {
    givenStatus(OrderStatus.PENDING);

    listener.onInventoryMessage(
        "{\"orderId\":\"o1\",\"success\":true,\"extra\":1}",
        KafkaTopicNames.INVENTORY_RESERVED_TOPIC);

    verify(orderManagerService).updateOrderStatus("o1", OrderStatus.INVENTORY_RESERVED);
  }

  @Test
  void paymentFailedTopicForcesFailure() {
    givenStatus(OrderStatus.PENDING);

    listener.onPaymentMessage(
        "{\"orderId\":\"o1\",\"success\":true}", KafkaTopicNames.PAYMENT_FAILED_TOPIC);

    verify(orderManagerService)
        .updateOrderStatus("o1", OrderStatus.FAILED, Order.REASON_PAYMENT_FAILED);
  }

  @Test
  void paymentSuccessfulTopicMarksPaid() {
    givenStatus(OrderStatus.PENDING);

    listener.onPaymentMessage(
        "{\"orderId\":\"o1\",\"success\":true}", KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC);

    verify(orderManagerService).updateOrderStatus("o1", OrderStatus.PAID);
  }

  @Test
  void unparseableOrNullMessagesAreDropped() {
    listener.onInventoryMessage("not-json", KafkaTopicNames.INVENTORY_RESERVED_TOPIC);
    listener.onPaymentMessage(null, KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC);

    verifyNoInteractions(orderManagerService);
  }

  @Test
  void kafkaEntryPointsSwallowApplicationErrors() {
    when(orderManagerService.getOrderStatus("nope"))
        .thenThrow(CodedErrorFactory.create(MappedError.ORDER_4002, "nope"));

    listener.onInventoryMessage(
        "{\"orderId\":\"nope\",\"success\":true}", KafkaTopicNames.INVENTORY_RESERVED_TOPIC);
    listener.onPaymentMessage(
        "{\"orderId\":\"nope\",\"success\":true}", KafkaTopicNames.PAYMENT_SUCCESSFUL_TOPIC);

    verify(orderManagerService, never()).updateOrderStatus(anyString(), any());
  }
}
