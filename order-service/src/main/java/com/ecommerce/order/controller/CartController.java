package com.ecommerce.order.controller;

import com.ecommerce.common.dto.CartDTO;
import com.ecommerce.order.service.CartService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cart endpoints. The user is taken from X-User-Id, then the userId query parameter or body. */
@RestController
@RequestMapping("/carts")
public class CartController {

  private final CartService cartService;

  public CartController(CartService cartService) {
    this.cartService = cartService;
  }

  @PostMapping("/items")
  public CartDTO addItem(
      @RequestHeader(value = RequestUser.USER_ID_HEADER, required = false) String headerUserId,
      @RequestParam(value = "userId", required = false) String queryUserId,
      @RequestBody AddCartItemRequest request) {
    String userId = RequestUser.resolve(headerUserId, queryUserId, request.getUserId());
    return cartService.addToCart(userId, request);
  }

  @GetMapping
  public CartDTO getCart(
      @RequestHeader(value = RequestUser.USER_ID_HEADER, required = false) String headerUserId,
      @RequestParam(value = "userId", required = false) String queryUserId) {
    return cartService.getCart(RequestUser.resolve(headerUserId, queryUserId));
  }
}
