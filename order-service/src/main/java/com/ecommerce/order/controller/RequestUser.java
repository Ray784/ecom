package com.ecommerce.order.controller;

import com.ecommerce.common.error.CodedErrorFactory;
import com.ecommerce.common.error.MappedError;

/** Resolves the calling user from the gateway header with request level fallbacks. */
final class RequestUser {

  /** Header attached by the api-gateway after JWT validation. */
  static final String USER_ID_HEADER = "X-User-Id";

  private RequestUser() {}

  /**
   * Returns the first non blank candidate, checked in the given order.
   *
   * <p>Throws IAM_4002 when no candidate identifies a user, because an unauthenticated call has
   * no cart or orders to act on.
   */
  static String resolve(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate.trim();
      }
    }
    throw CodedErrorFactory.create(MappedError.IAM_4002);
  }
}
