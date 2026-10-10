package com.ecommerce.order;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.order.client.ProductCatalogClient;
import com.ecommerce.order.client.ProductCatalogClient.ProductSnapshot;
import com.ecommerce.order.service.KafkaProducerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the full service against H2 to verify JPA mappings and the end to end order saga.
 *
 * <p>Kafka listeners are not started and the producer and product-service client are mocked,
 * so the test needs no broker or peer service.
 */
@SpringBootTest(
    properties = {
      "order.kafka.listeners.auto-startup=false",
      "spring.datasource.url=jdbc:h2:mem:orderdb-it;MODE=MySQL;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureMockMvc
class OrderServiceIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private KafkaProducerService kafkaProducerService;
  @MockBean private ProductCatalogClient productCatalogClient;

  private String placeOrder(String user) throws Exception {
    when(productCatalogClient.fetchProduct("SKU-1"))
        .thenReturn(new ProductSnapshot("SKU-1", new BigDecimal("15.00"), 100));
    mvc.perform(post("/carts/items")
            .header("X-User-Id", user)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sku\":\"SKU-1\",\"quantity\":2}"))
        .andExpect(status().isOk());
    mvc.perform(post("/carts/items")
            .header("X-User-Id", user)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"sku\":\"SKU-2\",\"quantity\":1,\"unitPrice\":4.5}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.subtotal", is(34.5)));

    String body =
        mvc.perform(post("/orders")
                .header("X-User-Id", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shippingAddress\":\"1 Main St\",\"expectedTotal\":34.50}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("PENDING")))
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode json = objectMapper.readTree(body);
    return json.get("orderId").asText();
  }

  @Test
  void fullSagaMovesOrderToPaidAndClearsCart() throws Exception {
    String orderId = placeOrder("it-user-1");

    mvc.perform(get("/carts").header("X-User-Id", "it-user-1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode", is("ORDER_4005")));

    mvc.perform(post("/events/inventory-result")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderId\":\"" + orderId + "\",\"success\":true}"))
        .andExpect(jsonPath("$.status", is("INVENTORY_RESERVED")));
    mvc.perform(post("/events/payment-result")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderId\":\"" + orderId + "\",\"success\":true}"))
        .andExpect(jsonPath("$.status", is("PAID")));

    mvc.perform(get("/orders/" + orderId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("PAID")))
        .andExpect(jsonPath("$.totalAmount", is(34.5)))
        .andExpect(jsonPath("$.shippingAddress", is("1 Main St")));
    mvc.perform(put("/orders/" + orderId + "/cancel"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode", is("ORDER_4003")));
  }

  @Test
  void verifiedPurchaseRequiresPaidOrderWithSku() throws Exception {
    String orderId = placeOrder("it-user-4");

    mvc.perform(get("/orders/verified-purchase?userId=it-user-4&sku=SKU-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verified", is(false)));

    mvc.perform(post("/events/payment-result")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderId\":\"" + orderId + "\",\"success\":true}"))
        .andExpect(jsonPath("$.status", is("PAID")));

    mvc.perform(get("/orders/verified-purchase?userId=it-user-4&sku=SKU-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId", is("it-user-4")))
        .andExpect(jsonPath("$.sku", is("SKU-1")))
        .andExpect(jsonPath("$.verified", is(true)));
    mvc.perform(get("/orders/verified-purchase?userId=it-user-4&sku=SKU-404"))
        .andExpect(jsonPath("$.verified", is(false)));
    mvc.perform(get("/orders/verified-purchase?userId=someone-else&sku=SKU-1"))
        .andExpect(jsonPath("$.verified", is(false)));

    mvc.perform(get("/orders?userId=it-user-4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].orderId", is(orderId)));
    mvc.perform(get("/orders").header("X-User-Id", "it-user-4"))
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void paymentFailureThenCancelReturnsOrder4010() throws Exception {
    String orderId = placeOrder("it-user-2");

    mvc.perform(post("/events/payment-result")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderId\":\"" + orderId + "\",\"success\":false}"))
        .andExpect(jsonPath("$.status", is("FAILED")));
    mvc.perform(put("/orders/" + orderId + "/cancel"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode", is("ORDER_4010")));
  }

  @Test
  void pendingOrderCanBeCancelled() throws Exception {
    String orderId = placeOrder("it-user-3");

    mvc.perform(put("/orders/" + orderId + "/cancel"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("CANCELLED")));
  }

  @Test
  void orderWithEmptyCartAndUnknownOrderAreRejected() throws Exception {
    mvc.perform(post("/orders").header("X-User-Id", "nobody"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode", is("ORDER_4001")));
    mvc.perform(get("/orders/does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode", is("ORDER_4002")));
    mvc.perform(post("/events/inventory-result")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"orderId\":\"does-not-exist\",\"success\":true}"))
        .andExpect(status().isNotFound());
  }
}
