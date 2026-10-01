package com.ecommerce.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecommerce.common.dto.InventoryReservedEvent;
import com.ecommerce.common.dto.ProductSummaryDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OutboundClientsTest {

  private MockRestServiceServer server;
  private RestClient restClient;

  private final ProductSummaryDTO product =
      new ProductSummaryDTO("P1", "Phone", "d", BigDecimal.TEN, "ELECTRONICS", "img");

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    restClient = builder.build();
  }

  @Test
  void searchIndexClient_accepted_returnsTrue() {
    server
        .expect(requestTo("http://search:8089/search/index"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.sku").value("P1"))
        .andRespond(withSuccess());

    assertThat(new SearchIndexClient(restClient, true, "http://search:8089").pushProduct(product))
        .isTrue();
    server.verify();
  }

  @Test
  void searchIndexClient_serverError_returnsFalse() {
    server.expect(requestTo("http://search:8089/search/index")).andRespond(withServerError());

    assertThat(new SearchIndexClient(restClient, true, "http://search:8089").pushProduct(product))
        .isFalse();
  }

  @Test
  void searchIndexClient_disabledOrNull_skipsCall() {
    assertThat(new SearchIndexClient(restClient, false, "http://x").pushProduct(product)).isFalse();
    assertThat(new SearchIndexClient(restClient, true, "http://x").pushProduct(null)).isFalse();
    server.verify();
  }

  @Test
  void orderWebhookClient_configured_postsEvent() {
    server
        .expect(requestTo("http://order:8084/hook"))
        .andExpect(jsonPath("$.orderId").value("O1"))
        .andExpect(jsonPath("$.success").value(true))
        .andRespond(withSuccess());

    OrderWebhookClient client = new OrderWebhookClient(restClient, "http://order:8084/hook");
    assertThat(client.isConfigured()).isTrue();
    assertThat(client.notifyInventoryResult(new InventoryReservedEvent("O1", true, "ok"))).isTrue();
    server.verify();
  }

  @Test
  void orderWebhookClient_serverError_returnsFalse() {
    server.expect(requestTo("http://order:8084/hook")).andRespond(withServerError());

    assertThat(
            new OrderWebhookClient(restClient, "http://order:8084/hook")
                .notifyInventoryResult(new InventoryReservedEvent("O1", true, "ok")))
        .isFalse();
  }

  @Test
  void orderWebhookClient_notConfiguredOrNullEvent_skipsCall() {
    OrderWebhookClient blank = new OrderWebhookClient(restClient, "");
    assertThat(blank.isConfigured()).isFalse();
    assertThat(blank.notifyInventoryResult(new InventoryReservedEvent("O1", true, "ok"))).isFalse();
    assertThat(new OrderWebhookClient(restClient, null).isConfigured()).isFalse();
    assertThat(new OrderWebhookClient(restClient, "http://x").notifyInventoryResult(null)).isFalse();
    server.verify();
  }
}
