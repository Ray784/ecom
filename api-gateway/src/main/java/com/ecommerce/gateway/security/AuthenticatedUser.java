package com.ecommerce.gateway.security;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Identity of a caller whose token was validated.
 *
 * @param userId subject of the token
 * @param username login name, may be null
 * @param roles granted roles
 */
public record AuthenticatedUser(String userId, String username, Set<String> roles) {

  public AuthenticatedUser {
    Objects.requireNonNull(userId, "userId");
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }

  public boolean hasRole(String role) {
    return roles.contains(role);
  }

  /** Returns the roles sorted and comma separated, as sent in the X-User-Roles header. */
  public String rolesHeaderValue() {
    return String.join(",", new TreeSet<>(roles));
  }

  /** Returns the username when known, otherwise the user ID, for use in error messages. */
  public String displayName() {
    return username == null || username.isBlank() ? userId : username;
  }
}
