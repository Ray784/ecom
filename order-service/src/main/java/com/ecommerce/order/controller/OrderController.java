package com.ecommerce.order.controller;

import com.ecommerce.common.dto.OrderDetailsDTO;
import com.ecommerce.order.service.OrderManagerService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Order endpoints. */
@RestController
@RequestMapping("/orders")
public class OrderController {

  /** Optional header carrying the shipping address when the body does not. */
  static final String SHIPPING_ADDRESS_HEADER = "X-Shipping-Address";

  private final OrderManagerService orderManagerService;

  public OrderController(OrderManagerService orderManagerService) {
    this.orderManagerService = orderManagerService;
  }

  /**
   * Places an order from the caller's cart.
   *
   * <p>The body shipping address wins over the header. Omitting both uses the default address,
   * while an explicit empty string is rejected with ORDER_4007.
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public OrderDetailsDTO createOrder(
      @RequestHeader(value = RequestUser.USER_ID_HEADER, required = false) String headerUserId,
      @RequestHeader(value = SHIPPING_ADDRESS_HEADER, required = false) String headerAddress,
      @RequestBody(required = false) CreateOrderRequest request) {
    CreateOrderRequest body = request == null ? new CreateOrderRequest() : request;
    String userId = RequestUser.resolve(headerUserId, body.getUserId());
    String address = body.getShippingAddress() != null ? body.getShippingAddress() : headerAddress;
    OrderDetailsDTO created =
        orderManagerService.createOrder(userId, address, body.getExpectedTotal());
    created.setStatusCode(HttpStatus.CREATED.value());
    created.setMessage("Order created");
    return created;
  }

  /**
   * Lists the orders of a user, newest first.
   *
   * <p>The userId query parameter wins over the gateway header. Omitting both yields IAM_4002.
   */
  @GetMapping
  public List<OrderDetailsDTO> listOrders(
      @RequestParam(required = false) String userId,
      @RequestHeader(value = RequestUser.USER_ID_HEADER, required = false) String headerUserId) {
    return orderManagerService.getOrdersByUserId(RequestUser.resolve(userId, headerUserId));
  }

  /** Reports whether the user has a PAID order containing the SKU, used by review-service. */
  @GetMapping("/verified-purchase")
  public VerifiedPurchaseResponse verifiedPurchase(
      @RequestParam String userId, @RequestParam String sku) {
    return new VerifiedPurchaseResponse(
        userId, sku, orderManagerService.isVerifiedPurchase(userId, sku));
  }

  @GetMapping("/{orderId}")
  public OrderDetailsDTO getOrder(@PathVariable String orderId) {
    return orderManagerService.getOrderDetails(orderId);
  }

  @PutMapping("/{orderId}/cancel")
  public OrderDetailsDTO cancelOrder(@PathVariable String orderId) {
    return orderManagerService.cancelOrder(orderId);
  }
}
