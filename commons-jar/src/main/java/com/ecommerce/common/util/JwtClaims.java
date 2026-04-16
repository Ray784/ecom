package com.ecommerce.common.util;

import java.util.Set;

/**
 * Claims decoded from a verified JWT.
 *
 * @param userId subject of the token
 * @param username login name of the user
 * @param email email address of the user, may be null
 * @param roles granted roles such as ROLE_CUSTOMER
 * @param issuedAt issue time in epoch seconds
 * @param expiresAt expiry time in epoch seconds
 */
public record JwtClaims(
    String userId, String username, String email, Set<String> roles, long issuedAt, long expiresAt) {

  public JwtClaims {
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }

  /** Returns the expiry time in epoch milliseconds. */
  public long expiresAtMillis() {
    return expiresAt * 1000L;
  }

  /** Returns true if the claims include the given role. */
  public boolean hasRole(String role) {
    return roles.contains(role);
  }
}
