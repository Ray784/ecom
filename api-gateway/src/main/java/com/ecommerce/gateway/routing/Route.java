package com.ecommerce.gateway.routing;

import java.util.Objects;

/**
 * Where a request goes and what it needs.
 *
 * @param service downstream service receiving the request
 * @param authenticationRequired whether a valid bearer token must be presented
 * @param requiredRole role the caller must hold, or null when any authenticated user may pass
 */
public record Route(ServiceId service, boolean authenticationRequired, String requiredRole) {

  public static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
  public static final String ROLE_ADMIN = "ROLE_ADMIN";

  public Route {
    Objects.requireNonNull(service, "service");
    if (requiredRole != null && !authenticationRequired) {
      throw new IllegalArgumentException("A required role implies authentication");
    }
  }

  /** Route open to anonymous callers. */
  public static Route publicRoute(ServiceId service) {
    return new Route(service, false, null);
  }

  /** Route open to any caller with a valid token. */
  public static Route authenticated(ServiceId service) {
    return new Route(service, true, null);
  }

  /** Route open only to callers holding the given role. */
  public static Route withRole(ServiceId service, String role) {
    return new Route(service, true, Objects.requireNonNull(role, "role"));
  }
}
