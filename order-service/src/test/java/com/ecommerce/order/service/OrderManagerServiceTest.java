package com.ecommerce.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.common.dto.OrderDetailsDTO;
import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.order.model.Cart;
import com.ecommerce.order.model.CartItem;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderManagerServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");
  private static final String USER = "user-1";
  private static final String DEFAULT_ADDRESS = "Default address";

  @Mock private OrderRepository orderRepository;
  @Mock private CartService cartService;
  @Mock private KafkaProducerService kafkaProducerService;

  private OrderManagerService service;

  @BeforeEach
  void setUp() {
    service =
        new OrderManagerService(
            orderRepository, cartService, kafkaProducerService, DEFAULT_ADDRESS,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static Cart cart() {
    Cart cart = new Cart(USER);
    cart.addItem(new CartItem("SKU-1", 2, new BigDecimal("10.00")));
    cart.addItem(new CartItem("SKU-2", 1, new BigDecimal("5.50")));
    return cart;
  }

  private static Order order(String id, OrderStatus status, String failureReason) {
    Order order = new Order(id, USER, "addr", NOW);
    order.setStatus(status.name());
    order.setFailureReason(failureReason);
    return order;
  }

  private void stubSaveReturnsArgument() {
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private static void assertError(Runnable call, MappedError expected) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ApplicationBaseException.class)
        .extracting(e -> ((ApplicationBaseException) e).getMappedError())
        .isEqualTo(expected);
  }

  @Test
  void createOrderPersistsPendingOrderPublishesEventAndClearsCart() {
    when(cartService.findActiveCart(USER)).thenReturn(Optional.of(cart()));
    stubSaveReturnsArgument();

    OrderDetailsDTO dto = service.createOrder(USER, " 1 Main St ", new BigDecimal("25.5"));

    assertThat(dto.getStatus()).isEqualTo("PENDING");
    assertThat(dto.getTotalAmount()).isEqualByComparingTo("25.50");
    assertThat(dto.getShippingAddress()).isEqualTo("1 Main St");
    assertThat(dto.getItems()).hasSize(2);
    assertThat(dto.getCreationDate()).isEqualTo(NOW.toString());
    ArgumentCaptor<OrderPlacedEvent> event = ArgumentCaptor.forClass(OrderPlacedEvent.class);
    verify(kafkaProducerService).sendOrderPlaced(event.capture());
    assertThat(event.getValue().getOrderId()).isEqualTo(dto.getOrderId());
    assertThat(event.getValue().getUserId()).isEqualTo(USER);
    assertThat(event.getValue().getItems()).hasSize(2);
    assertThat(event.getValue().getTotalAmount()).isEqualByComparingTo("25.50");
    verify(cartService).clearCart(USER);
  }

  @Test
  void createOrderUsesDefaultAddressWhenOmitted() {
    when(cartService.findActiveCart(USER)).thenReturn(Optional.of(cart()));
    stubSaveReturnsArgument();

    assertThat(service.createOrder(USER, null, null).getShippingAddress())
        .isEqualTo(DEFAULT_ADDRESS);
  }

  @Test
  void createOrderRejectsBlankAddress() {
    assertError(() -> service.createOrder(USER, "", null), MappedError.ORDER_4007);
    assertError(() -> service.createOrder(USER, "   ", null), MappedError.ORDER_4007);
    verify(orderRepository, never()).save(any());
  }

  @Test
  void createOrderRejectsMissingOrEmptyCart() {
    when(cartService.findActiveCart(USER)).thenReturn(Optional.empty());
    assertError(() -> service.createOrder(USER, null, null), MappedError.ORDER_4001);

    when(cartService.findActiveCart(USER)).thenReturn(Optional.of(new Cart(USER)));
    assertError(() -> service.createOrder(USER, null, null), MappedError.ORDER_4001);
  }

  @Test
  void createOrderRejectsTotalMismatch() {
    when(cartService.findActiveCart(USER)).thenReturn(Optional.of(cart()));

    assertError(() -> service.createOrder(USER, null, new BigDecimal("1.00")),
        MappedError.ORDER_4008);
    verify(orderRepository, never()).save(any());
  }

  @Test
  void createOrderMarksOrderFailedAndKeepsCartWhenPublishFails() {
    when(cartService.findActiveCart(USER)).thenReturn(Optional.of(cart()));
    stubSaveReturnsArgument();
    doThrow(CodedErrorFactory.create(MappedError.ORDER_5001, "x"))
        .when(kafkaProducerService).sendOrderPlaced(any());

    assertError(() -> service.createOrder(USER, null, null), MappedError.ORDER_5001);

    ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository, times(2)).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo("FAILED");
    assertThat(saved.getValue().getFailureReason()).isEqualTo(Order.REASON_PUBLISH_FAILED);
    verify(cartService, never()).clearCart(anyString());
  }

  @Test
  void createOrderFromCartUsesDefaultAddressAndRejectsEmptyCart() {
    stubSaveReturnsArgument();

    assertThat(service.createOrder(USER, cart()).getShippingAddress()).isEqualTo(DEFAULT_ADDRESS);
    assertError(() -> service.createOrder(USER, (Cart) null), MappedError.ORDER_4001);
    assertError(() -> service.createOrder(USER, new Cart(USER)), MappedError.ORDER_4001);
  }

  @Test
  void getOrderDetailsReturnsOrderOrThrowsNotFound() {
    when(orderRepository.findById("o1"))
        .thenReturn(Optional.of(order("o1", OrderStatus.PAID, null)));
    when(orderRepository.findById("missing")).thenReturn(Optional.empty());

    assertThat(service.getOrderDetails("o1").getStatus()).isEqualTo("PAID");
    assertError(() -> service.getOrderDetails("missing"), MappedError.ORDER_4002);
    assertError(() -> service.getOrderDetails(null), MappedError.ORDER_4002);
  }

  @Test
  void updateOrderStatusAppliesValidTransitionAndRecordsFailureReason() {
    when(orderRepository.findById("o1"))
        .thenReturn(Optional.of(order("o1", OrderStatus.PENDING, null)));
    stubSaveReturnsArgument();

    OrderDetailsDTO dto =
        service.updateOrderStatus("o1", OrderStatus.FAILED, Order.REASON_INVENTORY_FAILED);

    assertThat(dto.getStatus()).isEqualTo("FAILED");
    ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository).save(saved.capture());
    assertThat(saved.getValue().getFailureReason()).isEqualTo(Order.REASON_INVENTORY_FAILED);
  }

  @Test
  void updateOrderStatusToSameStatusIsNoOp() {
    when(orderRepository.findById("o1"))
        .thenReturn(Optional.of(order("o1", OrderStatus.PAID, null)));

    assertThat(service.updateOrderStatus("o1", OrderStatus.PAID).getStatus()).isEqualTo("PAID");
    verify(orderRepository, never()).save(any());
  }

  @Test
  void invalidTransitionThrowsOrder4003() {
    when(orderRepository.findById("o1"))
        .thenReturn(Optional.of(order("o1", OrderStatus.PAID, null)));

    assertError(() -> service.cancelOrder("o1"), MappedError.ORDER_4003);
  }

  @Test
  void invalidTransitionAfterPaymentFailureThrowsOrder4010() {
    when(orderRepository.findById("o1"))
        .thenReturn(Optional.of(order("o1", OrderStatus.FAILED, Order.REASON_PAYMENT_FAILED)));

    assertError(() -> service.cancelOrder("o1"), MappedError.ORDER_4010);
  }

  @Test
  void invalidTransitionAfterInventoryFailureThrowsOrder4003() {
    when(orderRepository.findById("o1"))
        .thenReturn(Optional.of(order("o1", OrderStatus.FAILED, Order.REASON_INVENTORY_FAILED)));

    assertError(() -> service.cancelOrder("o1"), MappedError.ORDER_4003);
  }

  @Test
  void cancelPendingOrderSucceeds() {
    when(orderRepository.findById("o1"))
        .thenReturn(Optional.of(order("o1", OrderStatus.PENDING, null)));
    stubSaveReturnsArgument();

    assertThat(service.cancelOrder("o1").getStatus()).isEqualTo("CANCELLED");
  }

  @Test
  void getOrderStatusReturnsEnumValue() {
    when(orderRepository.findById("o1"))
        .thenReturn(Optional.of(order("o1", OrderStatus.INVENTORY_RESERVED, null)));

    assertThat(service.getOrderStatus("o1")).isEqualTo(OrderStatus.INVENTORY_RESERVED);
  }
}
