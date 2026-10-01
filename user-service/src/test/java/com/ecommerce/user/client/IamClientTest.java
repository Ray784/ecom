package com.ecommerce.user.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ecommerce.common.exception.ApplicationBaseException;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class IamClientTest {

  private MockRestServiceServer server;
  private IamClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://iam");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new IamClient(builder.build(), true);
  }

  @Test
  void fetchAccount_found_mapsJsonIgnoringUnknownFields() {
    server
        .expect(requestTo("http://iam/users/u-1"))
        .andRespond(
            withSuccess(
                "{\"userId\":\"u-1\",\"username\":\"ann\",\"email\":\"a@x.io\","
                    + "\"firstName\":\"Ann\",\"enabled\":true,\"roles\":[\"ROLE_CUSTOMER\"]}",
                MediaType.APPLICATION_JSON));

    Optional<IamUserAccount> account = client.fetchAccount("u-1");

    assertThat(account).contains(new IamUserAccount("u-1", "ann", "a@x.io", "Ann", true));
  }

  @Test
  void fetchAccount_iamClientError_returnsEmpty() {
    server.expect(requestTo("http://iam/users/u-404")).andRespond(withResourceNotFound());
    assertThat(client.fetchAccount("u-404")).isEmpty();
  }

  @Test
  void fetchAccount_iamServerError_throwsUser5002() {
    server.expect(requestTo("http://iam/users/u-1")).andRespond(withServerError());
    assertThatThrownBy(() -> client.fetchAccount("u-1"))
        .isInstanceOf(ApplicationBaseException.class)
        .extracting(e -> ((ApplicationBaseException) e).getErrorCode())
        .isEqualTo("USER_5002");
  }

  @Test
  void fetchAccount_iamUnreachable_throwsUser5002() {
    server
        .expect(requestTo("http://iam/users/u-1"))
        .andRespond(
            request -> {
              throw new IOException("connection refused");
            });
    assertThatThrownBy(() -> client.fetchAccount("u-1"))
        .isInstanceOf(ApplicationBaseException.class)
        .extracting(e -> ((ApplicationBaseException) e).getErrorCode())
        .isEqualTo("USER_5002");
  }

  @Test
  void fetchAccount_disabled_skipsRemoteCall() {
    IamClient disabled = new IamClient(RestClient.builder().build(), false);
    assertThat(disabled.fetchAccount("u-1")).isEmpty();
  }

  @Test
  void springConstructor_appliesBaseUrlAndTimeouts() {
    RestClient.Builder builder = RestClient.builder();
    IamClient configured = new IamClient(builder, "http://127.0.0.1:1", true, 200);
    assertThatThrownBy(() -> configured.fetchAccount("u-1"))
        .isInstanceOf(ApplicationBaseException.class)
        .extracting(e -> ((ApplicationBaseException) e).getErrorCode())
        .isEqualTo("USER_5002");
  }
}
