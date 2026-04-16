package com.ecommerce.common.dto;

import java.util.Objects;

/** Credentials submitted to the IAM login endpoint. */
public class LoginRequestDTO {

  private String username;
  private String password;

  /** Creates an empty instance for JSON deserialization. */
  public LoginRequestDTO() {}

  /** Creates an instance with all fields populated. */
  public LoginRequestDTO(String username, String password) {
    this.username = username;
    this.password = password;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LoginRequestDTO that = (LoginRequestDTO) o;
    return Objects.equals(username, that.username)
        && Objects.equals(password, that.password);
  }

  @Override
  public int hashCode() {
    return Objects.hash(username, password);
  }

  @Override
  public String toString() {
    return "LoginRequestDTO{" + "username=" + username
        + ", password=****" + "}";
  }
}
