package com.ecommerce.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.common.dto.InventoryReservedEvent;
import com.ecommerce.common.dto.PaymentResultEvent;
import com.ecommerce.common.dto.ProductDetailsDTO;
import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;
import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.product.model.Category;
import com.ecommerce.product.service.CategoryService;
import com.ecommerce.product.service.InventoryManagerService;
import com.ecommerce.product.service.KafkaListenerService;
import com.ecommerce.product.service.ProductCatalogService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({
  ProductController.class,
  InventoryController.class,
  CategoryController.class,
  OrderEventController.class
})
@Import(GlobalExceptionHandler.class)
class ProductControllersTest {

  private static final String PRODUCT_JSON =
      "{\"sku\":\"P1\",\"name\":\"Phone\",\"price\":10,\"categoryId\":\"ELECTRONICS\",\"initialStock\":5}";

  @Autowired private MockMvc mockMvc;
  @MockBean private ProductCatalogService productCatalogService;
  @MockBean private InventoryManagerService inventoryManagerService;
  @MockBean private CategoryService categoryService;
  @MockBean private KafkaListenerService kafkaListenerService;

  private static ProductDetailsDTO details() {
    ProductDetailsDTO dto =
        new ProductDetailsDTO("P1", "Phone", null, BigDecimal.TEN, "ELECTRONICS", 5, 0, null);
    dto.setSuccess(true);
    return dto;
  }

  @Test
  void postProducts_returns201WithDetails() throws Exception {
    when(productCatalogService.createProduct(any())).thenReturn(details());

    mockMvc
        .perform(post("/products").contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sku").value("P1"))
        .andExpect(jsonPath("$.currentStock").value(5));
  }

  @Test
  void postProducts_duplicate_returnsProd4004() throws Exception {
    when(productCatalogService.createProduct(any()))
        .thenThrow(CodedErrorFactory.create(MappedError.PROD_4004, "P1"));

    mockMvc
        .perform(post("/products").contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("PROD_4004"));
  }

  @Test
  void getProduct_unknown_returns404Prod4001() throws Exception {
    when(productCatalogService.getProductDetails("NOPE"))
        .thenThrow(CodedErrorFactory.create(MappedError.PROD_4001, "NOPE"));

    mockMvc
        .perform(get("/products/NOPE"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("PROD_4001"));
  }

  @Test
  void getProduct_existing_returns200() throws Exception {
    when(productCatalogService.getProductDetails("P1")).thenReturn(details());
    mockMvc.perform(get("/products/P1")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Phone"));
  }

  @Test
  void putProduct_returnsUpdatedDetails() throws Exception {
    when(productCatalogService.updateProduct(eq("P1"), any())).thenReturn(details());

    mockMvc
        .perform(put("/products/P1").contentType(MediaType.APPLICATION_JSON).content(PRODUCT_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sku").value("P1"));
  }

  @Test
  void getProducts_returnsList() throws Exception {
    when(productCatalogService.listAllProducts()).thenReturn(List.of(details()));
    mockMvc.perform(get("/products")).andExpect(status().isOk()).andExpect(jsonPath("$[0].sku").value("P1"));
  }

  @Test
  void reserveInventory_success_returnsSuccessEvent() throws Exception {
    mockMvc
        .perform(
            post("/inventory/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"O1\",\"items\":[{\"sku\":\"P1\",\"quantity\":2}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value("O1"))
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void reserveInventory_insufficient_returnsProd4002() throws Exception {
    doThrow(CodedErrorFactory.create(MappedError.PROD_4002, "P1", 1))
        .when(inventoryManagerService)
        .reserveStock(anyList());

    mockMvc
        .perform(
            post("/inventory/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"O1\",\"items\":[{\"sku\":\"P1\",\"quantity\":2}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("PROD_4002"));
  }

  @Test
  void postCategories_returns201() throws Exception {
    when(categoryService.createCategory(any())).thenReturn(new Category("TOYS", "Toys"));

    mockMvc
        .perform(
            post("/categories").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"toys\",\"name\":\"Toys\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("TOYS"));
  }

  @Test
  void getCategory_missing_returnsProd4009() throws Exception {
    when(categoryService.getCategory("NOPE"))
        .thenThrow(CodedErrorFactory.create(MappedError.PROD_4009, "NOPE"));

    mockMvc
        .perform(get("/categories/NOPE"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("PROD_4009"));
  }

  @Test
  void getCategories_returnsList() throws Exception {
    when(categoryService.listCategories()).thenReturn(List.of(new Category("BOOKS", "Books")));
    mockMvc.perform(get("/categories")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("BOOKS"));
  }

  @Test
  void orderPlacedWebhook_validEvent_returns202WithOutcome() throws Exception {
    when(kafkaListenerService.handleOrderPlaced(any()))
        .thenReturn(new InventoryReservedEvent("O1", true, "ok"));

    mockMvc
        .perform(
            post("/events/order-placed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"O1\",\"items\":[{\"sku\":\"P1\",\"quantity\":1}]}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void orderPlacedWebhook_missingOrderId_returns400() throws Exception {
    when(kafkaListenerService.handleOrderPlaced(any()))
        .thenReturn(new InventoryReservedEvent(null, false, "Order event is missing an orderId"));

    mockMvc
        .perform(post("/events/order-placed").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void paymentResultWebhook_validEvent_returns202AndDelegates() throws Exception {
    when(kafkaListenerService.handlePaymentResult(any())).thenReturn(true);

    mockMvc
        .perform(
            post("/events/payment-result")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"O1\",\"transactionId\":\"pay_1\",\"success\":true}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.orderId").value("O1"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.applied").value(true));
    verify(kafkaListenerService)
        .handlePaymentResult(new PaymentResultEvent("O1", "pay_1", true, null));
  }

  @Test
  void paymentResultWebhook_missingOrderId_returns400WithoutDelegating() throws Exception {
    mockMvc
        .perform(
            post("/events/payment-result")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"success\":false}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.applied").value(false));
    verify(kafkaListenerService, never()).handlePaymentResult(any());
  }
}
