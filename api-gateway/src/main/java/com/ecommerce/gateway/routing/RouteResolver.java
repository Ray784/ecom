package com.ecommerce.gateway.routing;

import static com.ecommerce.gateway.routing.Route.ROLE_ADMIN;
import static com.ecommerce.gateway.routing.Route.ROLE_CUSTOMER;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * Static route table implementing the External API Gateway Definition of the LLD.
 *
 * <p>Rules are evaluated in declaration order and the first match wins, so the more specific
 * protected rules for a path prefix are declared before broader public ones where they overlap.
 */
@Component
public class RouteResolver {

  private static final String ANY_METHOD = "*";

  private final AntPathMatcher matcher = new AntPathMatcher();
  private final List<Rule> rules = new ArrayList<>();

  public RouteResolver() {
    // Public endpoints, no token required
    add("POST", "/auth/login", Route.publicRoute(ServiceId.IAM));
    add("POST", "/users/register", Route.publicRoute(ServiceId.IAM));
    add("GET", "/products", Route.publicRoute(ServiceId.PRODUCT));
    add("GET", "/products/{id}", Route.publicRoute(ServiceId.PRODUCT));
    add("GET", "/search/products", Route.publicRoute(ServiceId.SEARCH));
    add("GET", "/reviews/products/{productId}", Route.publicRoute(ServiceId.REVIEW));
    add("GET", "/reviews/products/{productId}/rating", Route.publicRoute(ServiceId.REVIEW));
    add("GET", "/reviews/{id}", Route.publicRoute(ServiceId.REVIEW));

    // Razorpay emulator and webhook authenticate with Basic Auth or HMAC signatures, not bearer
    // tokens, so the gateway lets them through and payment-service verifies the forwarded headers
    add("POST", "/payments/razorpay/webhook", Route.publicRoute(ServiceId.PAYMENT));
    add("POST", "/v1/orders", Route.publicRoute(ServiceId.PAYMENT));
    add("GET", "/v1/orders/{id}", Route.publicRoute(ServiceId.PAYMENT));
    add("POST", "/v1/payments", Route.publicRoute(ServiceId.PAYMENT));
    add("GET", "/v1/payments/{id}", Route.publicRoute(ServiceId.PAYMENT));

    // Catalog management, admin only
    add("POST", "/products", Route.withRole(ServiceId.PRODUCT, ROLE_ADMIN));
    add("PUT", "/products/{id}", Route.withRole(ServiceId.PRODUCT, ROLE_ADMIN));

    // Customer endpoints
    add("PUT", "/users/{id}/password", Route.withRole(ServiceId.IAM, ROLE_CUSTOMER));
    add("POST", "/orders", Route.withRole(ServiceId.ORDER, ROLE_CUSTOMER));
    add("GET", "/orders", Route.withRole(ServiceId.ORDER, ROLE_CUSTOMER));
    add("GET", "/orders/verified-purchase", Route.withRole(ServiceId.ORDER, ROLE_CUSTOMER));
    add("GET", "/orders/{orderId}", Route.withRole(ServiceId.ORDER, ROLE_CUSTOMER));
    add("PUT", "/orders/{orderId}/cancel", Route.withRole(ServiceId.ORDER, ROLE_CUSTOMER));
    add("GET", "/carts", Route.withRole(ServiceId.ORDER, ROLE_CUSTOMER));
    add("POST", "/carts/items", Route.withRole(ServiceId.ORDER, ROLE_CUSTOMER));
    add("GET", "/invoices/{orderId}", Route.withRole(ServiceId.INVOICE, ROLE_CUSTOMER));
    add("GET", "/profiles/{id}", Route.withRole(ServiceId.USER, ROLE_CUSTOMER));
    add("PUT", "/profiles/{id}", Route.withRole(ServiceId.USER, ROLE_CUSTOMER));
    add("POST", "/profiles/{id}/addresses", Route.withRole(ServiceId.USER, ROLE_CUSTOMER));
    add(
        "DELETE",
        "/profiles/{id}/addresses/{addressId}",
        Route.withRole(ServiceId.USER, ROLE_CUSTOMER));
    add("POST", "/reviews", Route.withRole(ServiceId.REVIEW, ROLE_CUSTOMER));

    // Inspection endpoints for any authenticated user
    add(ANY_METHOD, "/payments/**", Route.authenticated(ServiceId.PAYMENT));
    add(ANY_METHOD, "/notifications/**", Route.authenticated(ServiceId.NOTIFICATION));
  }

  /** Returns the route for the method and path, or empty when the gateway does not expose it. */
  public Optional<Route> resolve(String method, String path) {
    if (method == null || path == null) {
      return Optional.empty();
    }
    for (Rule rule : rules) {
      if ((rule.method().equals(ANY_METHOD) || rule.method().equalsIgnoreCase(method))
          && matcher.match(rule.pattern(), path)) {
        return Optional.of(rule.route());
      }
    }
    return Optional.empty();
  }

  private void add(String method, String pattern, Route route) {
    rules.add(new Rule(method, pattern, route));
  }

  private record Rule(String method, String pattern, Route route) {}
}
