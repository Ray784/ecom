package com.ecommerce.common.dto;

import java.util.Objects;

/** Details required to register a new IAM user account. */
public class RegistrationDTO {

  /** Role assigned when the registration request does not specify one. */
  public static final String DEFAULT_ROLE = "ROLE_CUSTOMER";

  private String username;
  private String password;
  private String email;
  private String firstName;
  private String role = DEFAULT_ROLE;

  /** Creates an empty instance for JSON deserialization. */
  public RegistrationDTO() {}

  /** Creates an instance with all fields populated. */
  public RegistrationDTO(String username, String password, String email, String firstName, String role) {
    this.username = username;
    this.password = password;
    this.email = email;
    this.firstName = firstName;
    this.role = role != null ? role : DEFAULT_ROLE;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RegistrationDTO that = (RegistrationDTO) o;
    return Objects.equals(username, that.username)
        && Objects.equals(password, that.password)
        && Objects.equals(email, that.email)
        && Objects.equals(firstName, that.firstName)
        && Objects.equals(role, that.role);
  }

  @Override
  public int hashCode() {
    return Objects.hash(username, password, email, firstName, role);
  }

  @Override
  public String toString() {
    return "RegistrationDTO{" + "username=" + username
        + ", password=****"
        + ", email=" + email
        + ", firstName=" + firstName
        + ", role=" + role + "}";
  }
}
