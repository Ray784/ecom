package com.ecommerce.gateway.routing;

import java.util.Locale;

/** Downstream services reachable through the gateway, keyed by their gateway.services entry. */
public enum ServiceId {
  IAM,
  USER,
  PRODUCT,
  ORDER,
  PAYMENT,
  INVOICE,
  NOTIFICATION,
  REVIEW,
  SEARCH;

  /** Returns the key used under gateway.services, for example product. */
  public String key() {
    return name().toLowerCase(Locale.ROOT);
  }
}
