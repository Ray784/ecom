package com.ecommerce.order.controller;

import com.ecommerce.common.dto.CartItemDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Body of POST /carts/items: a CartItemDTO that may also name the user. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddCartItemRequest extends CartItemDTO {

  private String userId;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }
}
