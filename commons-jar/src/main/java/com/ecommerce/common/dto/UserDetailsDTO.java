package com.ecommerce.common.dto;

import java.util.Objects;
import java.util.Set;

/** Identity and roles resolved from a validated JWT. */
public class UserDetailsDTO extends BaseResponseDTO {

  private String userId;
  private String username;
  private Set<String> roles;
  private String email;
  private boolean valid;

  /** Creates an empty instance for JSON deserialization. */
  public UserDetailsDTO() {}

  /** Creates a successful response with status 200 carrying the given payload fields. */
  public UserDetailsDTO(String userId, String username, Set<String> roles, String email, boolean valid) {
    super(true, 200, "Success");
    this.userId = userId;
    this.username = username;
    this.roles = roles;
    this.email = email;
    this.valid = valid;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public Set<String> getRoles() {
    return roles;
  }

  public void setRoles(Set<String> roles) {
    this.roles = roles;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean valid) {
    this.valid = valid;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!super.equals(o)) {
      return false;
    }
    UserDetailsDTO that = (UserDetailsDTO) o;
    return Objects.equals(userId, that.userId)
        && Objects.equals(username, that.username)
        && Objects.equals(roles, that.roles)
        && Objects.equals(email, that.email)
        && valid == that.valid;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), userId, username, roles, email, valid);
  }

  @Override
  public String toString() {
    return "UserDetailsDTO{" + "userId=" + userId
        + ", username=" + username
        + ", roles=" + roles
        + ", email=" + email
        + ", valid=" + valid
        + ", " + super.toString() + "}";
  }
}
