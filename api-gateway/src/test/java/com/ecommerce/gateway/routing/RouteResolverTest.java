package com.ecommerce.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RouteResolverTest {

  private final RouteResolver resolver = new RouteResolver();

  @ParameterizedTest
  @CsvSource({
    "POST, /auth/login, IAM",
    "POST, /users/register, IAM",
    "GET, /products, PRODUCT",
    "GET, /products/p-1, PRODUCT",
    "GET, /search/products, SEARCH",
    "GET, /reviews/products/p-1, REVIEW",
    "GET, /reviews/products/p-1/rating, REVIEW",
    "GET, /reviews/12, REVIEW",
    "get, /products/p-1, PRODUCT"
  })
  void publicRoutes_needNoAuthentication(String method, String path, ServiceId service) {
    assertThat(resolver.resolve(method, path)).contains(Route.publicRoute(service));
  }

  @ParameterizedTest
  @CsvSource({"POST, /products", "PUT, /products/p-1"})
  void catalogManagement_requiresAdmin(String method, String path) {
    assertThat(resolver.resolve(method, path))
        .contains(Route.withRole(ServiceId.PRODUCT, Route.ROLE_ADMIN));
  }

  @ParameterizedTest
  @CsvSource({
    "POST, /orders, ORDER",
    "GET, /orders/o-1, ORDER",
    "PUT, /orders/o-1/cancel, ORDER",
    "GET, /carts, ORDER",
    "POST, /carts/items, ORDER",
    "GET, /invoices/o-1, INVOICE",
    "GET, /profiles/u-1, USER",
    "PUT, /profiles/u-1, USER",
    "POST, /profiles/u-1/addresses, USER",
    "DELETE, /profiles/u-1/addresses/a-1, USER",
    "POST, /reviews, REVIEW"
  })
  void customerRoutes_requireCustomerRole(String method, String path, ServiceId service) {
    assertThat(resolver.resolve(method, path))
        .contains(Route.withRole(service, Route.ROLE_CUSTOMER));
  }

  @ParameterizedTest
  @CsvSource({
    "GET, /payments/order/o-1, PAYMENT",
    "POST, /payments/anything, PAYMENT",
    "GET, /notifications/user/u-1, NOTIFICATION"
  })
  void inspectionRoutes_requireAnyAuthenticatedUser(String method, String path, ServiceId service) {
    assertThat(resolver.resolve(method, path)).contains(Route.authenticated(service));
  }

  @ParameterizedTest
  @CsvSource({
    "GET, /unknown",
    "DELETE, /products/p-1",
    "GET, /reviews",
    "POST, /inventory/reserve",
    "GET, /orders/o-1/extra"
  })
  void unmappedRoutes_resolveToEmpty(String method, String path) {
    assertThat(resolver.resolve(method, path)).isEmpty();
  }

  @Test
  void nullArguments_resolveToEmpty() {
    assertThat(resolver.resolve(null, "/products")).isEqualTo(Optional.empty());
    assertThat(resolver.resolve("GET", null)).isEqualTo(Optional.empty());
  }
}
