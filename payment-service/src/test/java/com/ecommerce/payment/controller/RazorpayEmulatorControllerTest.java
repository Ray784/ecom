package com.ecommerce.payment.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.common.exception.GlobalExceptionHandler;
import com.ecommerce.payment.service.RazorpayEmulatorService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RazorpayEmulatorController.class)
@Import({RazorpayEmulatorService.class, GlobalExceptionHandler.class})
class RazorpayEmulatorControllerTest {

  private static final String AUTH =
      RazorpayEmulatorService.basicAuthorization("rzp_test_key_id", "rzp_test_key_secret");

  @Autowired private MockMvc mockMvc;

  private String createOrder(int amount) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, AUTH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":" + amount + ",\"currency\":\"INR\",\"receipt\":\"o-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(matchesPattern("order_[A-Za-z0-9]{14}")))
            .andExpect(jsonPath("$.entity").value("order"))
            .andExpect(jsonPath("$.amount").value(amount))
            .andExpect(jsonPath("$.amount_due").value(amount))
            .andExpect(jsonPath("$.status").value("created"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private static String paymentJson(String orderId, String userId) {
    return "{\"order_id\":\"" + orderId + "\",\"amount\":5000,\"currency\":\"INR\","
        + "\"method\":\"upi\",\"payer_user_id\":\"" + userId + "\",\"description\":\"test\"}";
  }

  @Test
  void createOrderThenGet_withBasicAuth_returnsStoredOrder() throws Exception {
    String orderId = createOrder(5000);

    mockMvc
        .perform(get("/v1/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(orderId))
        .andExpect(jsonPath("$.receipt").value("o-1"));
  }

  @Test
  void createOrder_missingAuthorization_returns400WithPay4002() throws Exception {
    mockMvc
        .perform(
            post("/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("PAY_4002"));
  }

  @Test
  void createPayment_wrongSecret_returns400WithPay4002() throws Exception {
    mockMvc
        .perform(
            post("/v1/payments")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    RazorpayEmulatorService.basicAuthorization("rzp_test_key_id", "nope"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentJson("order_x", "alice")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("PAY_4002"));
  }

  @Test
  void createPaymentThenGet_normalPayer_returnsCapturedPayment() throws Exception {
    String orderId = createOrder(5000);

    String body =
        mockMvc
            .perform(
                post("/v1/payments")
                    .header(HttpHeaders.AUTHORIZATION, AUTH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(paymentJson(orderId, "alice")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("captured"))
            .andExpect(jsonPath("$.captured").value(true))
            .andExpect(jsonPath("$.order_id").value(orderId))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String paymentId = JsonPath.read(body, "$.id");

    mockMvc
        .perform(get("/v1/payments/" + paymentId).header(HttpHeaders.AUTHORIZATION, AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(startsWith("pay_")))
        .andExpect(jsonPath("$.method").value("upi"));
    mockMvc
        .perform(get("/v1/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, AUTH))
        .andExpect(jsonPath("$.status").value("paid"))
        .andExpect(jsonPath("$.amount_paid").value(5000))
        .andExpect(jsonPath("$.amount_due").value(0));
  }

  @Test
  void createPayment_declinePayer_returnsFailedPayment() throws Exception {
    String orderId = createOrder(5000);

    mockMvc
        .perform(
            post("/v1/payments")
                .header(HttpHeaders.AUTHORIZATION, AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentJson(orderId, "decline-bob")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("failed"))
        .andExpect(jsonPath("$.error_code").value("BAD_REQUEST_ERROR"))
        .andExpect(jsonPath("$.error_description").value("Card declined by gateway"));
  }

  @Test
  void createPayment_gatewayDownPayer_returns503WithPay5001() throws Exception {
    String orderId = createOrder(5000);

    mockMvc
        .perform(
            post("/v1/payments")
                .header(HttpHeaders.AUTHORIZATION, AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentJson(orderId, "gatewaydown-1")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("PAY_5001"));
  }

  @Test
  void getPayment_unknownId_returns404WithPay4004() throws Exception {
    mockMvc
        .perform(get("/v1/payments/pay_missing").header(HttpHeaders.AUTHORIZATION, AUTH))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("PAY_4004"));
  }
}
