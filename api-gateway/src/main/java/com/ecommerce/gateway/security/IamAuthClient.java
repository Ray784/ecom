package com.ecommerce.gateway.security;

import com.ecommerce.gateway.config.GatewayProperties;
import com.ecommerce.gateway.routing.ServiceId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Calls POST /auth/validate on the IAM service.
 *
 * <p>The token is sent both as a bearer Authorization header and as a token field in a JSON
 * body so either IAM request style is supported. A 2xx answer must carry a UserDetailsDTO shaped
 * body with userId and roles; valid set to false or a 401 or 403 status counts as a rejection.
 * Anything else is reported as unavailable so the caller can decide whether to fall back.
 */
@Component
public class IamAuthClient {

  static final String VALIDATE_PATH = "/auth/validate";

  private final HttpClient httpClient;
  private final GatewayProperties properties;
  private final ObjectMapper objectMapper;

  public IamAuthClient(HttpClient httpClient, GatewayProperties properties, ObjectMapper objectMapper) {
    this.httpClient = httpClient;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  public IamValidationResult validate(String token) {
    HttpResponse<String> response;
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(properties.baseUrl(ServiceId.IAM) + VALIDATE_PATH))
              .timeout(properties.getReadTimeout())
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
              .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
              .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      objectMapper.writeValueAsString(Map.of("token", token))))
              .build();
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException | IllegalArgumentException e) {
      return new IamValidationResult.Unavailable("IAM call failed: " + e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new IamValidationResult.Unavailable("Interrupted while calling IAM");
    }
    int status = response.statusCode();
    if (status == 401 || status == 403) {
      return new IamValidationResult.Rejected();
    }
    if (status < 200 || status >= 300) {
      return new IamValidationResult.Unavailable("IAM returned status " + status);
    }
    return parse(response.body());
  }

  private IamValidationResult parse(String body) {
    JsonNode node;
    try {
      node = objectMapper.readTree(body == null ? "" : body);
    } catch (IOException e) {
      return new IamValidationResult.Unavailable("Unparseable IAM response");
    }
    if (node == null || !node.isObject()) {
      return new IamValidationResult.Unavailable("Unexpected IAM response");
    }
    JsonNode valid = node.get("valid");
    if (valid != null && valid.isBoolean() && !valid.booleanValue()) {
      return new IamValidationResult.Rejected();
    }
    String userId = text(node.get("userId"));
    JsonNode roles = node.get("roles");
    if (userId == null || roles == null || !roles.isArray()) {
      return new IamValidationResult.Unavailable("IAM response lacks userId or roles");
    }
    Set<String> roleSet = new HashSet<>();
    roles.forEach(role -> roleSet.add(role.asText()));
    return new IamValidationResult.Valid(
        new AuthenticatedUser(userId, text(node.get("username")), roleSet));
  }

  private static String text(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    String value = node.asText();
    return value.isBlank() ? null : value;
  }
}
