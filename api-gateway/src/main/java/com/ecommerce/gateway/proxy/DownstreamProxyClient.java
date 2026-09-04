package com.ecommerce.gateway.proxy;

import com.ecommerce.gateway.config.GatewayProperties;
import com.ecommerce.gateway.routing.ServiceId;
import com.ecommerce.gateway.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Forwards a servlet request to a downstream service and relays the answer unchanged.
 *
 * <p>Downstream status codes and bodies, including ErrorResponseDTO payloads, are returned as
 * is. Client supplied X-User headers are always stripped; for authenticated callers they are
 * replaced with the identity verified by the gateway.
 */
@Component
public class DownstreamProxyClient {

  public static final String USER_ID_HEADER = "X-User-Id";
  public static final String USER_NAME_HEADER = "X-User-Name";
  public static final String USER_ROLES_HEADER = "X-User-Roles";

  private static final String USER_HEADER_PREFIX = "x-user-";

  /** Hop by hop headers plus headers the JDK client refuses to set. */
  private static final Set<String> SKIPPED_REQUEST_HEADERS =
      Set.of(
          "host",
          "connection",
          "content-length",
          "expect",
          "upgrade",
          "keep-alive",
          "te",
          "trailer",
          "transfer-encoding",
          "proxy-authorization",
          "proxy-connection",
          "http2-settings");

  private static final Set<String> SKIPPED_RESPONSE_HEADERS =
      Set.of("connection", "content-length", "keep-alive", "transfer-encoding", "trailer", "upgrade");

  private final HttpClient httpClient;
  private final GatewayProperties properties;

  public DownstreamProxyClient(HttpClient httpClient, GatewayProperties properties) {
    this.httpClient = httpClient;
    this.properties = properties;
  }

  /**
   * Sends the request to the given service.
   *
   * @throws ResponseStatusException with 503 when the service cannot be reached
   */
  public ResponseEntity<byte[]> forward(
      ServiceId service, HttpServletRequest request, byte[] body, AuthenticatedUser user) {
    URI target = targetUri(service, request);
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(target).timeout(properties.getReadTimeout());
    copyRequestHeaders(request, builder);
    if (user != null) {
      builder.header(USER_ID_HEADER, user.userId());
      if (user.username() != null) {
        builder.header(USER_NAME_HEADER, user.username());
      }
      builder.header(USER_ROLES_HEADER, user.rolesHeaderValue());
    }
    HttpRequest.BodyPublisher publisher =
        body == null || body.length == 0
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofByteArray(body);
    builder.method(request.getMethod(), publisher);
    try {
      HttpResponse<byte[]> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
      return toResponseEntity(response);
    } catch (IOException e) {
      throw unavailable(service, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw unavailable(service, e);
    }
  }

  URI targetUri(ServiceId service, HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    String query = request.getQueryString();
    return URI.create(
        properties.baseUrl(service) + path + (query == null || query.isEmpty() ? "" : "?" + query));
  }

  private static void copyRequestHeaders(HttpServletRequest request, HttpRequest.Builder builder) {
    for (String name : Collections.list(request.getHeaderNames())) {
      String lower = name.toLowerCase(Locale.ROOT);
      if (SKIPPED_REQUEST_HEADERS.contains(lower) || lower.startsWith(USER_HEADER_PREFIX)) {
        continue;
      }
      for (String value : Collections.list(request.getHeaders(name))) {
        builder.header(name, value);
      }
    }
  }

  private static ResponseEntity<byte[]> toResponseEntity(HttpResponse<byte[]> response) {
    HttpHeaders headers = new HttpHeaders();
    for (Map.Entry<String, List<String>> entry : response.headers().map().entrySet()) {
      String lower = entry.getKey().toLowerCase(Locale.ROOT);
      if (lower.startsWith(":") || SKIPPED_RESPONSE_HEADERS.contains(lower)) {
        continue;
      }
      headers.addAll(entry.getKey(), entry.getValue());
    }
    return ResponseEntity.status(response.statusCode()).headers(headers).body(response.body());
  }

  private static ResponseStatusException unavailable(ServiceId service, Exception cause) {
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Downstream service " + service.key() + " is unavailable.",
        cause);
  }
}
