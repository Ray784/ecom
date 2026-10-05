package com.ecommerce.order.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.common.dto.CartDTO;
import com.ecommerce.common.dto.InventoryReservedEvent;
import com.ecommerce.common.dto.OrderDetailsDTO;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.order.service.CartService;
import com.ecommerce.order.service.KafkaListenerService;
import com.ecommerce.order.service.OrderManagerService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {CartController.class, OrderController.class,
    EventWebhookController.class})
@Import(GlobalExceptionHandler.class)
class ControllersTest {

  @Autowired private MockMvc mvc;

  @MockBean private CartService cartService;
  @MockBean private OrderManagerService orderManagerService;
  @MockBean private KafkaListenerService kafkaListenerService;

  private static OrderDetailsDTO order(String status) {
    return new OrderDetailsDTO("o1", "u1", status, BigDecimal.TEN, "addr", List.of(), "now");
  }

  @Test
  void addItemPrefersHeaderUserOverQueryAndBody() throws Exception {
    when(cartService.addToCart(eq("header-user"), any()))
        .thenReturn(new CartDTO("header-user", List.of(), BigDecimal.ZERO));

    mvc.perform(post("/carts/items?userId=query-user")
            .header("X-User-Id", "header-user")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sku\":\"S1\",\"quantity\":1,\"userId\":\"body-user\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId", is("header-user")));
  }

  @Test
  void addItemFallsBackToQueryThenBodyUser() throws Exception {
    when(cartService.addToCart(any(), any()))
        .thenReturn(new CartDTO("x", List.of(), BigDecimal.ZERO));

    mvc.perform(post("/carts/items?userId=query-user")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sku\":\"S1\",\"quantity\":1,\"userId\":\"body-user\"}"))
        .andExpect(status().isOk());
    verify(cartService).addToCart(eq("query-user"), any());

    mvc.perform(post("/carts/items")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sku\":\"S1\",\"quantity\":1,\"userId\":\"body-user\"}"))
        .andExpect(status().isOk());
    verify(cartService).addToCart(eq("body-user"), any());
  }

  @Test
  void addItemWithoutAnyUserReturns401() throws Exception {
    mvc.perform(post("/carts/items")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sku\":\"S1\",\"quantity\":1}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode", is("IAM_4002")));
  }

  @Test
  void addItemValidationErrorIsMappedToCatalogStatus() throws Exception {
    when(cartService.addToCart(eq("u1"), any()))
        .thenThrow(CodedErrorFactory.create(MappedError.ORDER_4006, "S1", "0"));

    mvc.perform(post("/carts/items")
            .header("X-User-Id", "u1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sku\":\"S1\",\"quantity\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode", is("ORDER_4006")));
  }

  @Test
  void getCartUsesQueryUserAndMapsMissingCartTo404() throws Exception {
    when(cartService.getCart("u1"))
        .thenThrow(CodedErrorFactory.create(MappedError.ORDER_4005, "u1"));

    mvc.perform(get("/carts?userId=u1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode", is("ORDER_4005")));
  }

  @Test
  void getCartReturnsCart() throws Exception {
    when(cartService.getCart("u1")).thenReturn(new CartDTO("u1", List.of(), BigDecimal.ONE));

    mvc.perform(get("/carts").header("X-User-Id", "u1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subtotal", is(1)));
  }

  @Test
  void createOrderReturns201WithBodyAddressOverHeader() throws Exception {
    when(orderManagerService.createOrder("u1", "body addr", new BigDecimal("10")))
        .thenReturn(order("PENDING"));

    mvc.perform(post("/orders")
            .header("X-User-Id", "u1")
            .header("X-Shipping-Address", "header addr")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"shippingAddress\":\"body addr\",\"expectedTotal\":10}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.statusCode", is(201)))
        .andExpect(jsonPath("$.status", is("PENDING")));
  }

  @Test
  void createOrderWithoutBodyUsesHeaderAddress() throws Exception {
    when(orderManagerService.createOrder("u1", "header addr", null)).thenReturn(order("PENDING"));

    mvc.perform(post("/orders")
            .header("X-User-Id", "u1")
            .header("X-Shipping-Address", "header addr"))
        .andExpect(status().isCreated());
  }

  @Test
  void createOrderPassesNullAddressWhenOmittedAndUsesBodyUser() throws Exception {
    when(orderManagerService.createOrder(eq("body-user"), isNull(), isNull()))
        .thenReturn(order("PENDING"));

    mvc.perform(post("/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userId\":\"body-user\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void createOrderEmptyAddressIsMappedTo400() throws Exception {
    when(orderManagerService.createOrder("u1", "", null))
        .thenThrow(CodedErrorFactory.create(MappedError.ORDER_4007));

    mvc.perform(post("/orders")
            .header("X-User-Id", "u1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"shippingAddress\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode", is("ORDER_4007")));
  }

  @Test
  void getOrderReturnsDetailsOr404() throws Exception {
    when(orderManagerService.getOrderDetails("o1")).thenReturn(order("PAID"));
    when(orderManagerService.getOrderDetails("missing"))
        .thenThrow(CodedErrorFactory.create(MappedError.ORDER_4002, "missing"));

    mvc.perform(get("/orders/o1")).andExpect(jsonPath("$.status", is("PAID")));
    mvc.perform(get("/orders/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode", is("ORDER_4002")));
  }

  @Test
  void cancelOrderReturnsCancelledOr400() throws Exception {
    when(orderManagerService.cancelOrder("o1")).thenReturn(order("CANCELLED"));
    when(orderManagerService.cancelOrder("paid"))
        .thenThrow(CodedErrorFactory.create(MappedError.ORDER_4003, "paid", "PAID"));

    mvc.perform(put("/orders/o1/cancel")).andExpect(jsonPath("$.status", is("CANCELLED")));
    mvc.perform(put("/orders/paid/cancel"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode", is("ORDER_4003")));
  }

  @Test
  void inventoryWebhookDelegatesToListener() throws Exception {
    when(kafkaListenerService.handleInventoryUpdate(new InventoryReservedEvent("o1", true, null)))
        .thenReturn(order("INVENTORY_RESERVED"));

    mvc.perform(post("/events/inventory-result")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderId\":\"o1\",\"success\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("INVENTORY_RESERVED")));
  }

  @Test
  void paymentWebhookDelegatesToListener() throws Exception {
    when(kafkaListenerService.handlePaymentResult(new PaymentResultEvent("o1", "t1", true, null)))
        .thenReturn(order("PAID"));

    mvc.perform(post("/events/payment-result")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderId\":\"o1\",\"transactionId\":\"t1\",\"success\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("PAID")));
  }
}
