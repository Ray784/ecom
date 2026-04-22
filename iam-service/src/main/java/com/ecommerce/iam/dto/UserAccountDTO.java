package com.ecommerce.iam.dto;

import com.ecommerce.common.dto.UserDetailsDTO;
import java.util.Set;

/**
 * IAM internal view of an account, returned by GET /users/{userId}.
 *
 * <p>Extends UserDetailsDTO with the first name and enabled flag so that user-service can
 * auto initialize a profile from IAM data.
 */
public class UserAccountDTO extends UserDetailsDTO {

  private String firstName;
  private boolean enabled;

  public UserAccountDTO() {}

  public UserAccountDTO(
      String userId,
      String username,
      Set<String> roles,
      String email,
      String firstName,
      boolean enabled) {
    super(userId, username, roles, email, enabled);
    this.firstName = firstName;
    this.enabled = enabled;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
