package com.ecommerce.gateway.testsupport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Minimal HTTP server standing in for downstream services in tests.
 *
 * <p>Handlers are registered per path prefix; the longest matching prefix wins. Every request is
 * recorded so tests can assert on what the gateway forwarded.
 */
public final class FakeDownstream implements AutoCloseable {

  /** A request as received by the fake server; header names are lower case. */
  public record Recorded(
      String method, String uri, Map<String, List<String>> headers, String body) {

    public String header(String name) {
      List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
      return values == null || values.isEmpty() ? null : values.get(0);
    }
  }

  /** A canned response. */
  public record Reply(int status, String contentType, String body) {

    public static Reply json(int status, String body) {
      return new Reply(status, "application/json", body);
    }
  }

  private final HttpServer server;
  private final Map<String, Function<Recorded, Reply>> handlers = new ConcurrentHashMap<>();
  private final List<Recorded> requests = new CopyOnWriteArrayList<>();

  public FakeDownstream() {
    try {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext("/", this::handle);
    server.start();
  }

  public String baseUrl() {
    return "http://localhost:" + server.getAddress().getPort();
  }

  public void on(String pathPrefix, Function<Recorded, Reply> handler) {
    handlers.put(pathPrefix, handler);
  }

  public void reset() {
    handlers.clear();
    requests.clear();
  }

  public List<Recorded> requests() {
    return List.copyOf(requests);
  }

  /** Returns the last recorded request whose URI starts with the prefix, or null. */
  public Recorded last(String uriPrefix) {
    Recorded match = null;
    for (Recorded recorded : requests) {
      if (recorded.uri().startsWith(uriPrefix)) {
        match = recorded;
      }
    }
    return match;
  }

  private void handle(HttpExchange exchange) throws IOException {
    Map<String, List<String>> headers = new TreeMap<>();
    exchange
        .getRequestHeaders()
        .forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), List.copyOf(values)));
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    Recorded recorded =
        new Recorded(
            exchange.getRequestMethod(), exchange.getRequestURI().toString(), headers, body);
    requests.add(recorded);

    String path = exchange.getRequestURI().getPath();
    String bestPrefix = null;
    for (String prefix : handlers.keySet()) {
      if (path.startsWith(prefix) && (bestPrefix == null || prefix.length() > bestPrefix.length())) {
        bestPrefix = prefix;
      }
    }
    Reply reply =
        bestPrefix == null
            ? Reply.json(200, "{\"echo\":\"" + path + "\"}")
            : handlers.get(bestPrefix).apply(recorded);
    byte[] bytes = reply.body() == null ? new byte[0] : reply.body().getBytes(StandardCharsets.UTF_8);
    if (reply.contentType() != null) {
      exchange.getResponseHeaders().add("Content-Type", reply.contentType());
    }
    exchange.getResponseHeaders().add("X-Downstream", "fake");
    exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      exchange.getResponseBody().write(bytes);
    }
    exchange.close();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
