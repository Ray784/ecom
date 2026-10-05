package com.ecommerce.invoice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecommerce.common.dto.OrderDetailsDTO;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OrderServiceClientTest {

  private MockRestServiceServer server;
  private OrderServiceClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://orders.test");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new OrderServiceClient(builder.build());
  }

  @Test
  void fetchOrder_found_returnsDetails() {
    server
        .expect(requestTo("http://orders.test/orders/ORD-1"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"orderId\":\"ORD-1\",\"status\":\"PAID\",\"totalAmount\":42.50}",
                MediaType.APPLICATION_JSON));

    Optional<OrderDetailsDTO> order = client.fetchOrder("ORD-1");

    assertThat(order).isPresent();
    assertThat(order.get().getTotalAmount()).isEqualByComparingTo("42.50");
    assertThat(order.get().getStatus()).isEqualTo("PAID");
    server.verify();
  }

  @Test
  void fetchOrder_notFound_returnsEmpty() {
    server.expect(requestTo("http://orders.test/orders/ORD-2")).andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThat(client.fetchOrder("ORD-2")).isEmpty();
  }

  @Test
  void fetchOrder_connectionFailure_returnsEmpty() {
    server
        .expect(requestTo("http://orders.test/orders/ORD-3"))
        .andRespond(withException(new IOException("connection refused")));

    assertThat(client.fetchOrder("ORD-3")).isEmpty();
  }
}
