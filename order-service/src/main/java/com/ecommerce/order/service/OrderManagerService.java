package com.ecommerce.order.service;

import com.ecommerce.common.dto.ItemDetails;
import com.ecommerce.common.dto.OrderDetailsDTO;
import com.ecommerce.common.dto.OrderItemDTO;
import com.ecommerce.common.dto.OrderPlacedEvent;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.ApplicationBaseException;
import com.ecommerce.order.model.Cart;
import com.ecommerce.order.model.CartItem;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order lifecycle: creating orders from carts, reading them and moving them between states.
 *
 * <p>Order creation is deliberately not one transaction. The order row is committed before the
 * OrderPlaced event is published so downstream consumers always find it, and the cart is only
 * cleared after the broker acknowledged the event.
 */
@Service
public class OrderManagerService {

  private static final Logger log = LoggerFactory.getLogger(OrderManagerService.class);

  private final OrderRepository orderRepository;
  private final CartService cartService;
  private final KafkaProducerService kafkaProducerService;
  private final String defaultShippingAddress;
  private final Clock clock;

  @Autowired
  public OrderManagerService(
      OrderRepository orderRepository,
      CartService cartService,
      KafkaProducerService kafkaProducerService,
      @Value("${order.default-shipping-address:Default shipping address on file}")
          String defaultShippingAddress) {
    this(orderRepository, cartService, kafkaProducerService, defaultShippingAddress,
        Clock.systemUTC());
  }

  /** Full constructor that allows tests to control the clock. */
  public OrderManagerService(
      OrderRepository orderRepository,
      CartService cartService,
      KafkaProducerService kafkaProducerService,
      String defaultShippingAddress,
      Clock clock) {
    this.orderRepository = orderRepository;
    this.cartService = cartService;
    this.kafkaProducerService = kafkaProducerService;
    this.defaultShippingAddress = defaultShippingAddress;
    this.clock = clock;
  }

  /**
   * Creates an order from the user's active cart.
   *
   * <p>A null shipping address falls back to the configured default, while a blank one is
   * rejected with ORDER_4007. When expectedTotal is given it must equal the cart subtotal or
   * ORDER_4008 is thrown. An empty or missing cart yields ORDER_4001.
   */
  public OrderDetailsDTO createOrder(
      String userId, String shippingAddress, BigDecimal expectedTotal) {
    String address = resolveShippingAddress(shippingAddress);
    Cart cart =
        cartService
            .findActiveCart(userId)
            .filter(found -> !found.isEmpty())
            .orElseThrow(() -> CodedErrorFactory.create(MappedError.ORDER_4001, userId));
    return placeOrder(userId, cart, address, expectedTotal);
  }

  /** Creates an order from the given cart using the default shipping address. */
  public OrderDetailsDTO createOrder(String userId, Cart cart) {
    if (cart == null || cart.isEmpty()) {
      throw CodedErrorFactory.create(MappedError.ORDER_4001, userId);
    }
    return placeOrder(userId, cart, defaultShippingAddress, null);
  }

  /** Returns the order or throws ORDER_4002 when it does not exist. */
  @Transactional(readOnly = true)
  public OrderDetailsDTO getOrderDetails(String orderId) {
    return toDto(findOrder(orderId));
  }

  /** Returns all orders of the user, newest first. An unknown user yields an empty list. */
  @Transactional(readOnly = true)
  public List<OrderDetailsDTO> getOrdersByUserId(String userId) {
    return orderRepository.findByUserIdOrderByCreationDateDesc(userId).stream()
        .map(OrderManagerService::toDto)
        .toList();
  }

  /** Returns true when the user has at least one PAID order containing the given SKU. */
  @Transactional(readOnly = true)
  public boolean isVerifiedPurchase(String userId, String sku) {
    return orderRepository.existsByUserIdAndStatusAndItemsSku(
        userId, OrderStatus.PAID.name(), sku);
  }

  /** Moves the order to the given status, validating the transition. */
  @Transactional
  public OrderDetailsDTO updateOrderStatus(String orderId, OrderStatus status) {
    return updateOrderStatus(orderId, status, null);
  }

  /**
   * Moves the order to the given status and records the failure reason for FAILED orders.
   *
   * <p>Re-applying the current status is a no-op. An invalid transition throws ORDER_4010 when
   * the order already failed because of payment, otherwise ORDER_4003.
   */
  @Transactional
  public OrderDetailsDTO updateOrderStatus(
      String orderId, OrderStatus status, String failureReason) {
    Order order = findOrder(orderId);
    OrderStatus current = order.statusValue();
    if (current == status) {
      return toDto(order);
    }
    if (!current.canTransitionTo(status)) {
      if (current == OrderStatus.FAILED
          && Order.REASON_PAYMENT_FAILED.equals(order.getFailureReason())) {
        throw CodedErrorFactory.create(MappedError.ORDER_4010, orderId);
      }
      throw CodedErrorFactory.create(MappedError.ORDER_4003, orderId, current.name());
    }
    order.setStatus(status.name());
    if (status == OrderStatus.FAILED) {
      order.setFailureReason(failureReason);
    }
    log.info("Order {} moved from {} to {}", orderId, current, status);
    return toDto(orderRepository.save(order));
  }

  /** Cancels the order when its current status still allows it. */
  @Transactional
  public OrderDetailsDTO cancelOrder(String orderId) {
    return updateOrderStatus(orderId, OrderStatus.CANCELLED);
  }

  /** Returns the current status of the order or throws ORDER_4002. */
  @Transactional(readOnly = true)
  public OrderStatus getOrderStatus(String orderId) {
    return findOrder(orderId).statusValue();
  }

  private OrderDetailsDTO placeOrder(
      String userId, Cart cart, String shippingAddress, BigDecimal expectedTotal) {
    BigDecimal total = cart.subtotal().setScale(2, RoundingMode.HALF_UP);
    if (expectedTotal != null && expectedTotal.compareTo(total) != 0) {
      throw CodedErrorFactory.create(
          MappedError.ORDER_4008, expectedTotal.toPlainString(), total.toPlainString());
    }

    Order order = new Order(UUID.randomUUID().toString(), userId, shippingAddress, clock.instant());
    for (CartItem item : cart.getItems()) {
      order.addItem(new OrderItem(item.getSku(), item.getQuantity(), item.getUnitPrice()));
    }
    order.setTotalAmount(total);
    Order saved = orderRepository.save(order);

    List<ItemDetails> eventItems =
        saved.getItems().stream()
            .map(item -> new ItemDetails(item.getSku(), item.getQuantity(), item.getUnitPrice()))
            .toList();
    try {
      kafkaProducerService.sendOrderPlaced(
          new OrderPlacedEvent(saved.getId(), userId, eventItems, total));
    } catch (ApplicationBaseException e) {
      saved.setStatus(OrderStatus.FAILED.name());
      saved.setFailureReason(Order.REASON_PUBLISH_FAILED);
      orderRepository.save(saved);
      throw e;
    }
    cartService.clearCart(userId);
    return toDto(saved);
  }

  private String resolveShippingAddress(String shippingAddress) {
    if (shippingAddress == null) {
      return defaultShippingAddress;
    }
    if (shippingAddress.isBlank()) {
      throw CodedErrorFactory.create(MappedError.ORDER_4007);
    }
    return shippingAddress.trim();
  }

  private Order findOrder(String orderId) {
    if (orderId == null) {
      throw CodedErrorFactory.create(MappedError.ORDER_4002, "null");
    }
    return orderRepository
        .findById(orderId)
        .orElseThrow(() -> CodedErrorFactory.create(MappedError.ORDER_4002, orderId));
  }

  /** Maps an order entity to its external representation. */
  public static OrderDetailsDTO toDto(Order order) {
    List<OrderItemDTO> items =
        order.getItems().stream()
            .map(item -> new OrderItemDTO(item.getSku(), item.getQuantity(), item.getUnitPrice()))
            .toList();
    return new OrderDetailsDTO(
        order.getId(),
        order.getUserId(),
        order.getStatus(),
        order.getTotalAmount(),
        order.getShippingAddress(),
        items,
        order.getCreationDate().toString());
  }
}
