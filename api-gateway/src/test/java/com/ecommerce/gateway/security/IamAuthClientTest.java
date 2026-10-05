package com.ecommerce.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.gateway.config.GatewayProperties;
import com.ecommerce.gateway.testsupport.FakeDownstream;
import com.ecommerce.gateway.testsupport.FakeDownstream.Reply;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IamAuthClientTest {

  private FakeDownstream iam;
  private GatewayProperties properties;
  private IamAuthClient client;

  @BeforeEach
  void setUp() {
    iam = new FakeDownstream();
    properties = new GatewayProperties();
    properties.setReadTimeout(Duration.ofSeconds(5));
    properties.setServices(Map.of("iam", iam.baseUrl() + "/"));
    client = new IamAuthClient(HttpClient.newHttpClient(), properties, new ObjectMapper());
  }

  @AfterEach
  void tearDown() {
    iam.close();
  }

  @Test
  void validate_validResponse_returnsUserAndSendsTokenInHeaderAndBody() {
    iam.on(
        "/auth/validate",
        r ->
            Reply.json(
                200,
                "{\"userId\":\"u1\",\"username\":\"alice\",\"roles\":[\"ROLE_CUSTOMER\"],"
                    + "\"valid\":true,\"extra\":1}"));

    IamValidationResult result = client.validate("tok");

    assertThat(result)
        .isEqualTo(
            new IamValidationResult.Valid(
                new AuthenticatedUser("u1", "alice", Set.of("ROLE_CUSTOMER"))));
    FakeDownstream.Recorded sent = iam.last("/auth/validate");
    assertThat(sent.method()).isEqualTo("POST");
    assertThat(sent.header("Authorization")).isEqualTo("Bearer tok");
    assertThat(sent.body()).isEqualTo("{\"token\":\"tok\"}");
  }

  @Test
  void validate_validFalse_isRejected() {
    iam.on("/auth/validate", r -> Reply.json(200, "{\"userId\":\"u1\",\"roles\":[],\"valid\":false}"));

    assertThat(client.validate("tok")).isInstanceOf(IamValidationResult.Rejected.class);
  }

  @Test
  void validate_status401_isRejected() {
    iam.on("/auth/validate", r -> Reply.json(401, "{\"errorCode\":\"IAM_4002\"}"));

    assertThat(client.validate("tok")).isInstanceOf(IamValidationResult.Rejected.class);
  }

  @Test
  void validate_status403_isRejected() {
    iam.on("/auth/validate", r -> Reply.json(403, "{}"));

    assertThat(client.validate("tok")).isInstanceOf(IamValidationResult.Rejected.class);
  }

  @Test
  void validate_status500_isUnavailable() {
    iam.on("/auth/validate", r -> Reply.json(500, "{}"));

    assertThat(client.validate("tok")).isInstanceOf(IamValidationResult.Unavailable.class);
  }

  @Test
  void validate_missingRoles_isUnavailable() {
    iam.on("/auth/validate", r -> Reply.json(200, "{\"userId\":\"u1\"}"));

    assertThat(client.validate("tok")).isInstanceOf(IamValidationResult.Unavailable.class);
  }

  @Test
  void validate_missingUserId_isUnavailable() {
    iam.on("/auth/validate", r -> Reply.json(200, "{\"roles\":[\"ROLE_CUSTOMER\"]}"));

    assertThat(client.validate("tok")).isInstanceOf(IamValidationResult.Unavailable.class);
  }

  @Test
  void validate_malformedJson_isUnavailable() {
    iam.on("/auth/validate", r -> Reply.json(200, "{oops"));

    assertThat(client.validate("tok")).isInstanceOf(IamValidationResult.Unavailable.class);
  }

  @Test
  void validate_nonObjectJson_isUnavailable() {
    iam.on("/auth/validate", r -> Reply.json(200, "[1,2]"));

    assertThat(client.validate("tok")).isInstanceOf(IamValidationResult.Unavailable.class);
  }

  @Test
  void validate_emptyBody_isUnavailable() {
    iam.on("/auth/validate", r -> Reply.json(200, ""));

    assertThat(client.validate("tok")).isInstanceOf(IamValidationResult.Unavailable.class);
  }

  @Test
  void validate_serverDown_isUnavailable() {
    iam.close();

    assertThat(client.validate("tok")).isInstanceOf(IamValidationResult.Unavailable.class);
  }
}
